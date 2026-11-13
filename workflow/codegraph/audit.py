#!/usr/bin/env python3
"""Report-only code, test-quality and coverage-gap audit; no automatic fixes or test execution."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import fnmatch
import json
from pathlib import Path
import re
import subprocess
import codegraph_adapter
import toolchains

def parse_dart_machine(output: str, root: Path, tool: dict) -> list[dict]:
    """dart analyze --format=machine: SEVERITY|TYPE|CODE|path|line|column|length|message."""
    found = []
    for line in output.splitlines():
        parts = line.split('|', 7)
        if len(parts) != 8 or not parts[4].isdigit():
            continue
        path = Path(parts[3])
        try:
            relative = path.resolve().relative_to(root.resolve()).as_posix() if path.is_absolute() else path.as_posix()
        except ValueError:
            continue
        found.append({'path': relative, 'line': int(parts[4]), 'code': parts[2].lower(), 'message': parts[7], 'severity': parts[0].lower()})
    return found

def parse_cargo_json(output: str, root: Path, tool: dict) -> list[dict]:
    """cargo --message-format=json: one compiler-message object per diagnostic."""
    base = (root / tool.get('cwd', '.')).resolve()
    found, seen = [], set()
    for line in output.splitlines():
        if not line.startswith('{'):
            continue
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        message = entry.get('message') or {}
        code = (message.get('code') or {}).get('code') or ('compiler_error' if message.get('level') == 'error' else None)
        span = next((s for s in message.get('spans', []) if s.get('is_primary')), None)
        if entry.get('reason') != 'compiler-message' or not code or not span:
            continue
        path = Path(span['file_name'])
        try:
            relative = (path if path.is_absolute() else base / path).resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            continue
        key = (relative, span['line_start'], code, message.get('message'))
        if key not in seen:  # the same diagnostic repeats for each target that compiles the file
            seen.add(key)
            found.append({'path': relative, 'line': span['line_start'], 'code': code,
                          'message': message.get('message', ''), 'severity': message.get('level', '')})
    return found


def parse_ruff_json(output: str, root: Path, tool: dict) -> list[dict]:
    """ruff check --output-format=json: a list of violations with location/code."""
    base = (root / tool.get('cwd', '.')).resolve()
    try: data = json.loads(output or '[]')
    except ValueError: return []
    found = []
    for item in data if isinstance(data, list) else []:
        path = Path(item.get('filename', ''))
        try: relative = (path if path.is_absolute() else base / path).resolve().relative_to(root.resolve()).as_posix()
        except (ValueError, OSError): continue
        location = item.get('location') or {}
        found.append({'path': relative, 'line': int(location.get('row') or 1), 'code': item.get('code') or 'ruff',
                      'message': item.get('message', ''), 'severity': 'warning'})
    return found

def parse_shellcheck_json(output: str, root: Path, tool: dict) -> list[dict]:
    """shellcheck --format=json1: {'comments': [{file, line, level, code, message}]}."""
    base = (root / tool.get('cwd', '.')).resolve()
    try: data = json.loads(output or '{}')
    except ValueError: return []
    found = []
    comments = data.get('comments', []) if isinstance(data, dict) else []
    for item in comments:
        path = Path(item.get('file', ''))
        try: relative = (path if path.is_absolute() else base / path).resolve().relative_to(root.resolve()).as_posix()
        except (ValueError, OSError): continue
        found.append({'path': relative, 'line': int(item.get('line') or 1), 'code': f'SC{item.get("code")}',
                      'message': item.get('message', ''), 'severity': item.get('level', 'warning')})
    return found


def select_files(root, config, languages, paths, base=None, include_tests=False):
    unknown = set(languages) - set(config['languages'])
    if unknown: raise ValueError(f'Unknown languages: {sorted(unknown)}')
    argv = ['git','-C',str(root)]
    tracked = subprocess.run([*argv,*(['diff','--name-only','-z',base] if base else ['ls-files','-z'])],
                             capture_output=True,check=True).stdout.decode()
    others = subprocess.run([*argv,'ls-files','-z','--others','--exclude-standard'],capture_output=True,check=True).stdout.decode()
    prefixes = []
    for raw in paths:
        path = (root/raw).resolve()
        if not path.exists() or not path.is_relative_to(root): raise ValueError(f'Invalid scope: {raw}')
        prefixes.append(path.relative_to(root).as_posix())
    result, protected = {}, []
    for path in sorted(set((tracked+'\0'+others).split('\0'))-{''}):
        if any(part in {'.git','.work','.dart_tool','build','target','node_modules','__pycache__'} for part in Path(path).parts): continue
        if prefixes and not any(prefix=='.' or path==prefix or path.startswith(prefix+'/') for prefix in prefixes): continue
        language = next((name for name, data in config['languages'].items() if Path(path).suffix in data['extensions']),None)
        if not language or (languages and language not in languages): continue
        if not include_tests and is_test(config,path): continue
        if not (root/path).is_file(): continue
        if (root/path).is_symlink() or not (root/path).resolve().is_relative_to(root): raise ValueError(f'Unsafe input: {path}')
        if any(fnmatch.fnmatchcase(path,p) for p in config['protected']): protected.append(path);continue
        result.setdefault(language,[]).append(path)
    return result, protected


def is_test(config,path):
    return any(Path(path).suffix in language['extensions'] and
               any(fnmatch.fnmatchcase(path,pattern) for pattern in language.get('tests',[]))
               for language in config['languages'].values())


def classify(found, categories, kind, language):
    kinds = list(categories) if kind=='code' else [kind]
    result=[]
    for finding in found:
        matches=[name for name in kinds if any(fnmatch.fnmatchcase(finding['code'],pattern) for pattern in categories[name].get(language,[]))]
        if matches: result.append({**finding,'categories':matches,'evidence':'tool-diagnostic','confidence':'REVIEW_REQUIRED'})
    return result


def run_tool(root, language, tool, members, kind, log):
    if not tool: return [], {'status':'UNAVAILABLE','reason':'No diagnostic runner configured for this language'}
    if tool.get('runner')=='clang-tidy':
        import native_audit
        return native_audit.run_cpp(root,tool,members,log)
    if tool.get('runner')=='android-lint':
        import native_audit
        return native_audit.run_android(root,tool,log)
    argv=list(tool['run'])
    if language=='dart' and len(members)==1: argv.append(members[0])
    if tool.get('append_files'): argv += members[:200]
    if language=='rust' and kind!='dead-code':
        argv[2]='clippy'
        argv += ['--','-W','clippy::perf','-W','clippy::complexity','-W','clippy::style']
    if language=='rust':
        # The repository pin wins over the configured default toolchain.
        channel=toolchains.rust_channel(root)
        if channel: argv=[f'+{channel}' if re.fullmatch(r'\+\S+',argument) else argument for argument in argv]
    try:
        done=subprocess.run(argv,cwd=root/tool['cwd'],capture_output=True,text=True,errors='replace',
                            stdin=subprocess.DEVNULL,timeout=tool['timeout'])
    except (OSError,subprocess.TimeoutExpired) as error:
        return [], {'status':'INCOMPLETE','command':argv,'reason':str(error)}
    log.write_text(done.stdout+'\n--- stderr ---\n'+done.stderr)
    parser={'dart-machine':parse_dart_machine,'cargo-json':parse_cargo_json,
            'ruff-json':parse_ruff_json,'shellcheck-json':parse_shellcheck_json}[tool['parser']]
    found=parser(done.stdout,root,tool)
    errors=sum(f.get('severity')=='error' for f in found)
    accepted=set(tool.get('findings_exit_codes',[0]))
    failed=errors>0 or (done.returncode not in accepted and (language!='dart' or not found))
    return found, {'status':'INCOMPLETE' if failed else 'RAN','command':argv,'exit_code':done.returncode,
                   'diagnostics':len(found),'errors':errors,'log':str(log),
                   'scope_note':'Runner may analyze additional files; findings below are filtered to the selected scope'}


def scan(root, kind='all', languages=(), paths=(), base=None):
    root=root.resolve()
    if not (languages or paths or base): raise ValueError('Choose --lang, --path, --base or --all')
    config=json.loads(Path(__file__).with_name('audit-config.json').read_text())
    if kind not in ('all','code','tests','gaps') and kind not in config['categories']: raise ValueError('Unknown audit kind')
    include_tests=kind in ('all','tests','gaps')
    files,protected=select_files(root,config,languages,paths,base,include_tests=include_tests)
    source_files={language:[p for p in members if not is_test(config,p)] for language,members in files.items()}
    source_files={language:members for language,members in source_files.items() if members}
    if kind=='gaps': files=source_files
    if kind=='tests':
        files={language:[p for p in members if is_test(config,p) or language=='rust'] for language,members in files.items()}
        files={language:members for language,members in files.items() if members}
    directory=root/'.work/audit'/kind;directory.mkdir(parents=True,exist_ok=True)
    findings=[];tools={}
    if kind not in ('tests','gaps'):
        # Android Lint is a whole variant analysis: share one run across JVM languages.
        jvm={language:members for language,members in files.items()
             if (config['tools'].get(language) or {}).get('runner')=='android-lint'}
        jvm_results={};jvm_resources=[];jvm_resource_status={}
        if jvm:
            import native_audit
            first=next(iter(jvm));tool=config['tools'][first]
            compatible=all(config['tools'][language]==tool for language in jvm)
            if not compatible:
                found=[];shared={'status':'INCOMPLETE','reason':'Selected JVM languages configure different Android analyses; cannot share safely'}
            else:
                eligible=any(Path(path).suffix in ('.kt','.java') and any(path.startswith(prefix) for prefix in tool.get('source_roots',[]))
                             for members in jvm.values() for path in members)
                if eligible: found,shared=native_audit.run_android(root,tool,directory/'android.log')
                else: found=[];shared={'status':'INCOMPLETE','reason':'No selected JVM file belongs to configured Android variant source roots'}
            for language,members in jvm.items():
                uncovered=[path for path in members if Path(path).suffix not in ('.kt','.java') or
                           not any(path.startswith(prefix) for prefix in config['tools'][language].get('source_roots',[]))]
                status={**shared,'shared_languages':sorted(jvm),'uncovered_files':uncovered,
                        'source_roots':tool.get('source_roots',[])}
                if uncovered and status['status']=='RAN': status['status']='PARTIAL'
                selected=set(members)-set(uncovered)
                jvm_results[language]=([f for f in found if f['path'] in selected],status)
            # Lint reports resource diagnostics against res/ paths, which are not
            # language sources. Keep them instead of dropping them with the
            # per-language file filter; attribute them once to the shared run.
            prefixes=[f'{prefix}res/' for prefix in tool.get('source_roots',[]) if prefix.endswith('/')]
            jvm_resources=[f for f in found if any(f['path'].startswith(prefix) for prefix in prefixes)]
            jvm_resource_status={**shared,'source_roots':tool.get('source_roots',[])}
        for language,members in files.items():
            found,tools[language]=jvm_results[language] if language in jvm_results else run_tool(
                root,language,config['tools'].get(language),members,'code' if kind=='all' else kind,directory/f'{language}.log')
            selected=set(members)
            scoped=[f for f in found if f['path'] in selected]
            accepted=classify(scoped,config['categories'],'code' if kind=='all' else kind,language)
            tools[language]['selected_diagnostics']=len(scoped)
            tools[language]['classified_findings']=len(accepted)
            tools[language]['unclassified_diagnostics']=len(scoped)-len(accepted)
            findings.extend(accepted)
        if jvm_resources:
            language=next(iter(jvm))
            accepted=classify(jvm_resources,config['categories'],'code' if kind=='all' else kind,language)
            findings.extend(accepted)
            tools['android-resources']={**jvm_resource_status,'selected_diagnostics':len(jvm_resources),
                                        'classified_findings':len(accepted),
                                        'unclassified_diagnostics':len(jvm_resources)-len(accepted)}
    if kind in ('all','tests','gaps'):
        import test_checks
        if kind in ('all','tests'):
            found,tools['test-quality']=test_checks.inspect_tests(root,files,config['languages'],lambda path:is_test(config,path))
            findings.extend(found)
        if kind in ('all','gaps'):
            all_files,_=select_files(root,config,[],['.'],include_tests=True)
            tests=[p for members in all_files.values() for p in members if is_test(config,p)]
            # Inline Rust tests are also reference evidence for other source files.
            tests += [p for p in all_files.get('rust',[]) if not is_test(config,p) and '#[test' in (root/p).read_text(errors='replace')]
            found,tools['test-references']=test_checks.direct_gaps(root,source_files,tests)
            findings.extend(found)
            found,statuses=test_checks.inspect_coverage(root,source_files,config.get('coverage',{}))
            findings.extend(found)
            tools.update({f'coverage-{language}':status for language,status in statuses.items()})
    gaps=[name for name,tool in tools.items() if tool['status']!='RAN']
    if kind=='tests' and not tools.get('test-quality',{}).get('tests_read') and not gaps: files={}
    categories=list(config['categories'])+['tests','gaps'] if kind=='all' else list(config['categories']) if kind=='code' else [kind]
    counts={category:sum(category in finding['categories'] for finding in findings) for category in categories}
    result={'status':'NO_FILES' if not files else 'INCOMPLETE' if gaps else 'FINDINGS' if findings else 'PASS',
            'kind':kind,'at':datetime.now(timezone.utc).isoformat(),'scope':{'languages':list(languages),'paths':list(paths),'base':base},
            'by_category':counts,'file_list':files,'protected_skipped':protected,'tools':tools,'gaps':gaps,'findings':findings,
            'limits':config['notes']+['No source files are modified. Compiler tools can write build caches and execute build scripts.',
             'A clean report is not proof that all dead code has been found. Only emitted, allowlisted diagnostics are classified.',
             'Compiler errors make coverage incomplete. Dart suggestions depend on the enabled analyzer lints.',
             'Test-quality/direct-reference hints are not proof of redundancy or missing behavior tests.',
             'Existing LCOV can be stale or restricted to a subset of tests; no tests are executed automatically.',
             'Validate cross-language references and behavior before applying any suggestion.']}
    (directory/'report.json').write_text(json.dumps(result,indent=2)+'\n')
    lines=[f'# Code scan: {kind}', '', f'Status: {result["status"]}', '',
           f'Files: {sum(map(len,files.values()))}; findings: {len(findings)}; protected: {len(protected)}.', '', '## Tools', '']
    lines += [f'- {name}: {tool["status"]} — {tool.get("reason",str(tool.get("command")))}' for name,tool in tools.items()]
    lines += ['', '## Categories', '']+[f'- {name}: {count}' for name,count in counts.items()]
    lines += ['', '## Candidates requiring verification', '']
    lines += [f'- `{f["path"]}:{f["line"]}` [{f["code"]}] ({", ".join(f["categories"])}) {f["message"]}' for f in findings] or ['None reported in this scope.']
    lines += ['', '## Limits', '']+[f'- {note}' for note in result['limits']]
    (directory/'report.md').write_text('\n'.join(lines)+'\n')
    return result,directory/'report.md'


def configure_parser(p, include_root=True):
    if include_root: p.add_argument('--root',type=Path,default=Path.cwd())
    p.add_argument('--kind',choices=['all','code','dead-code','optimization','simplifier','tests','gaps'],default='all')
    p.add_argument('--lang',nargs='+',default=[])
    p.add_argument('--path',action='append',default=[])
    p.add_argument('--base');p.add_argument('--all',action='store_true')
    return p


def execute(args):
    result,report=scan(args.root,args.kind,args.lang,args.path or (['.'] if args.all or not (args.lang or args.base) else []),args.base)
    print(json.dumps({'status':result['status'],'findings':len(result['findings']),'gaps':result['gaps'],'report':str(report)}))
    return 2 if result['status'] in ('INCOMPLETE','NO_FILES') else 1 if result['findings'] else 0


def main(argv=None):
    p=configure_parser(argparse.ArgumentParser(description=__doc__))
    args=p.parse_args(argv)
    try:
        return execute(args)
    except (ValueError,OSError,subprocess.CalledProcessError) as error:
        p.error(str(error))

if __name__=='__main__':
    raise SystemExit(main())
