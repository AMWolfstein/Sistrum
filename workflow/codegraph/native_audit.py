"""Native-language diagnostic runners. No fixes, synthetic compiler flags or APK tasks."""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

CHECKS = '-*,clang-diagnostic-*,clang-analyzer-deadcode.*,misc-unused-*,performance-*,readability-redundant-*,readability-container-size-empty,modernize-use-nullptr,modernize-use-equals-default'
DIAGNOSTIC = re.compile(r'^(.+?):(\d+):(\d+): (warning|error|fatal error): (.*?)(?: \[([^\]]+)\])?$')


def relative(root, raw, base=None):
    if not raw: return None
    path=Path(raw)
    try: return (path if path.is_absolute() else (base or root)/path).resolve().relative_to(root.resolve()).as_posix()
    except (ValueError,OSError): return None


def parse_lint(report,root):
    text=report.read_text()
    if re.search(r'<!\s*(DOCTYPE|ENTITY)\b',text,re.I): raise ValueError('Unsupported XML entity/DTD')
    tree=ET.fromstring(text)
    if tree.tag!='issues': raise ValueError('Not an Android Lint issues report')
    findings=[];internal=[]
    for issue in tree.findall('issue'):
        code=issue.get('id','');message=issue.get('message','')
        if code in {'LintError','LintCrash','UnknownIssueId','ObsoleteLintCustomCheck'}:
            internal.append(f'{code}: {message}')
        for location in issue.findall('location'):
            path=relative(root,location.get('file',''))
            if not path:continue
            number=int(location.get('line','1'))
            if number<1:raise ValueError('Invalid Lint source line')
            findings.append({'path':path,'line':number,'code':code,'message':message,
                             'severity':issue.get('severity','').lower()})
    return findings,internal


def gradle_entry(android):
    """The project wrapper when tracked, otherwise the gradle on PATH (this repo tracks only the properties)."""
    wrapper=android/'gradlew'
    if wrapper.is_file():return ['bash',str(wrapper.resolve())]
    executable=shutil.which('gradle')
    return [executable] if executable else None


def run_android(root,tool,log):
    """Run once for Kotlin/Java; a private report path prevents stale XML reuse."""
    android=root/tool.get('cwd','android')
    entry=gradle_entry(android)
    if not entry:return [],{'status':'UNAVAILABLE','reason':'No Android Gradle wrapper or gradle executable; no APK is generated to create a wrapper'}
    variant=tool.get('variant','Debug')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*',variant):raise ValueError('Invalid Android variant')
    log.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='lint-',dir=log.parent) as temporary:
        report=Path(temporary).resolve()/'lint.xml'
        command=[*entry,f':app:lint{variant}','--console=plain','--no-configuration-cache',
                 '--init-script',str(Path(__file__).with_name('android-audit.init.gradle').resolve()),
                 f'-Dmornye.audit.report={report}']
        try:
            with log.open('w') as output:
                done=subprocess.run(command,cwd=android,stdout=output,stderr=subprocess.STDOUT,text=True,errors='replace',
                                    stdin=subprocess.DEVNULL,timeout=tool.get('timeout',1800))
        except (OSError,subprocess.TimeoutExpired) as error:
            return [],{'status':'INCOMPLETE','reason':str(error),'command':command,'variant':variant,'log':str(log)}
        status={'status':'RAN','command':command,'exit_code':done.returncode,'log':str(log),'variant':variant,
                'runner':'Android Lint (shared Kotlin/Java)','limitations':[
                    'Only this Android variant; not general whole-program unused-public-symbol detection',
                    'Gradle may compile dependencies and execute build scripts, but no APK task is requested',
                    'Existing suppressions/baselines may limit emitted diagnostics; findings are not deletion proof']}
        if not report.is_file():return [],{**status,'status':'INCOMPLETE','reason':'No fresh Android Lint XML report produced'}
        saved=log.with_suffix('.xml');shutil.copyfile(report,saved);status['report']=str(saved)
        try:found,internal=parse_lint(report,root)
        except (ValueError,OSError,ET.ParseError) as error:
            return [],{**status,'status':'INCOMPLETE','reason':f'Invalid fresh Lint report: {error}'}
        checked_tests='MORNYE_AUDIT_LINT checkTestSources=true' in log.read_text(errors='replace').splitlines()
        status.update(diagnostics=len(found),internal_errors=internal,test_sources_checked=checked_tests)
        if not checked_tests:status.update(status='INCOMPLETE',reason='Effective Lint test-source analysis was not confirmed')
        if done.returncode or internal:status.update(status='INCOMPLETE',reason='Gradle/Lint execution failed or analysis had internal errors')
        return found,status


def clang_tidy():
    configured=os.environ.get('AUDIT_CLANG_TIDY')
    if configured:return shutil.which(configured)
    found=shutil.which('clang-tidy')
    if found:return found
    ndk=os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
    if ndk:
        candidates=sorted(Path(ndk).glob('toolchains/llvm/prebuilt/*/bin/clang-tidy'))
        return next((str(p) for p in candidates if os.access(p,os.X_OK)),None)
    return None


def compilation_database(root,tool):
    configured=os.environ.get('AUDIT_CPP_COMPILATION_DATABASE') or tool.get('database')
    if configured:
        path=Path(configured)
        if not path.is_absolute():path=root/path
        if path.is_dir():path=path/'compile_commands.json'
        return path.resolve()
    # Default is the actual Android Debug/arm64 configuration, not a host substitute.
    choices=[p for pattern in tool.get('database_globs',[]) for p in root.glob(pattern) if p.is_file()]
    return max(choices,key=lambda p:(p.stat().st_mtime,str(p))).resolve() if choices else None


def parse_clang(output,root,directory):
    findings=[];errors=[]
    for line in output.splitlines():
        match=DIAGNOSTIC.match(line)
        if not match:continue
        raw,number,_,severity,message,codes=match.groups()
        if severity in ('error','fatal error'):errors.append(line)
        path=relative(root,raw,directory)
        if not path:continue
        resolved=codes or ('clang-diagnostic-error' if severity in ('error','fatal error') else 'clang-diagnostic-warning')
        for code in resolved.split(','):
            findings.append({'path':path,'line':int(number),'code':code.strip(),'message':message,'severity':severity})
    return findings,errors


def run_cpp(root,tool,members,log):
    executable=clang_tidy()
    if not executable:return [],{'status':'UNAVAILABLE','reason':'clang-tidy missing; set AUDIT_CLANG_TIDY or activate the NDK toolchain'}
    database=compilation_database(root,tool)
    if database is None or not database.is_file():
        return [],{'status':'UNAVAILABLE','reason':'No compilation database; configure CMake with CMAKE_EXPORT_COMPILE_COMMANDS=ON or set AUDIT_CPP_COMPILATION_DATABASE'}
    if database.name != 'compile_commands.json':
        return [],{'status':'INCOMPLETE','reason':'clang-tidy requires a database named compile_commands.json; refusing to validate a different file','database':str(database)}
    try:
        entries=json.loads(database.read_text())
        if not isinstance(entries,list):raise ValueError('Expected a list of compile commands')
        commands={}
        for item in entries:
            directory=Path(item['directory'])
            if not directory.is_absolute():directory=database.parent/directory
            path=relative(root,item['file'],directory)
            if path in members and directory.is_dir() and ('command' in item or 'arguments' in item):commands[path]=directory
    except (OSError,ValueError,KeyError,TypeError) as error:
        return [],{'status':'INCOMPLETE','reason':f'Invalid compilation database: {error}','database':str(database)}
    missing=sorted(set(members)-set(commands))
    status={'status':'PARTIAL' if missing else 'RAN','database':str(database),'covered_translation_units':sorted(commands),
            'uncovered_files':missing,'runner':'clang-tidy','limitations':[
                'Only selected translation units in this database/configuration; other variants/ABIs are unverified',
                'Headers are analyzed as included by translation units; standalone headers without commands stay uncovered',
                'Compilation database may be stale; compiler/header failures are incomplete evidence, never a clean scan']}
    if not commands:return [],{**status,'status':'INCOMPLETE','reason':'No selected file has a usable compile command'}
    findings=[];errors=[];results=[];texts=[]
    for path,directory in sorted(commands.items()):
        argv=[executable,'-p',str(database.parent),f'--checks={tool.get("checks",CHECKS)}',
              f'--header-filter=^{re.escape(str(root.resolve()))}/','--quiet',str(root/path)]
        try:
            done=subprocess.run(argv,cwd=directory,capture_output=True,text=True,errors='replace',
                                stdin=subprocess.DEVNULL,timeout=tool.get('timeout',300))
        except (OSError,subprocess.TimeoutExpired) as error:
            errors.append(f'{path}: {error}');continue
        output=done.stdout+'\n--- stderr ---\n'+done.stderr
        texts.append(f'### {path}\n{output}')
        found,compiler_errors=parse_clang(output,root,directory)
        findings.extend(found);errors.extend(compiler_errors)
        results.append({'file':path,'command':argv,'exit_code':done.returncode})
        if done.returncode:errors.append(f'{path}: clang-tidy exited {done.returncode}')
    log.write_text('\n'.join(texts))
    unique={(f['path'],f['line'],f['code'],f['message']):f for f in findings}
    status.update(diagnostics=len(unique),errors=errors,commands=results,log=str(log))
    if errors:status.update(status='INCOMPLETE',reason='Compiler/tool errors prevent complete analysis')
    return list(unique.values()),status
