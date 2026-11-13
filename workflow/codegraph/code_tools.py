#!/usr/bin/env python3
"""Standalone source inventory and navigation. No execution gates or source fingerprints."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import codegraph_adapter as graph
import native_bindings
import resource_index

SOURCES = {'.dart', '.rs', '.kt', '.kts', '.java', '.c', '.cpp', '.cc', '.cxx', '.h', '.hpp', '.py', '.js', '.ts', '.tsx', '.jsx'}
RESOURCES = resource_index.FORMATS | {'.sh', '.bash'}
EXCLUDED = {'.git', '.work', '.dart_tool', 'node_modules', 'build', 'target', 'vendor', 'third_party', '__pycache__'}
LIMITS = ['Static relationships are not proof of runtime reachability or dead code.',
          'JNI overloads, nested JVM owners, dynamic loading and generated UniFFI require verification.',
          'Platform-channel literals are candidates; computed names and handlers are not resolved.',
          'YAML, Gradle/CMake semantics, assets and other unsupported formats are not resolved.']


def inputs(root, selected=None):
    if selected is None:
        run = subprocess.run(['git', '-C', str(root), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                             capture_output=True, check=True)
        selected = sorted(set(run.stdout.decode().split('\0')) - {''})
    accepted, excluded = [], []
    for raw in selected:
        graph.safe_relative(raw)
        path = root / raw
        if any(p in EXCLUDED for p in Path(raw).parts) or path.suffix not in SOURCES | RESOURCES:
            excluded.append(raw)
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError(f'symlink/outside-root input: {raw}')
        if path.is_file():
            accepted.append(raw)
    return sorted(set(accepted)), excluded


def jvm_symbols(nodes, texts):
    """Supply simple class owners only; ambiguous nested ownership stays unresolved."""
    result, file_owners = [], {}
    for node in nodes:
        file = node['source_file']
        if not file.endswith(('.kt', '.java')):
            continue
        if file not in file_owners:
            code = native_bindings.masked(texts[file])
            offsets, current = [], 0
            for line in code.split('\n'):
                offsets.append(current)
                current += len(line) + 1
            intervals = []
            for header in re.finditer(r'\b(?:class|object)\s+(\w+)[^{\n]*\{', code):
                depth, end = 1, header.end()
                while end < len(code) and depth:
                    depth += (code[end] == '{') - (code[end] == '}')
                    end += 1
                intervals.append((header.end(), end, header.group(1)))
            file_owners[file] = offsets, intervals
        offsets, intervals = file_owners[file]
        number = int(node['source_location'].removeprefix('L'))
        if not 0 < number <= len(offsets):
            continue
        offset = offsets[number-1]
        owners = [owner for start,end,owner in intervals if start <= offset < end]
        if len(owners) == 1:
            result.append({'file': file, 'line': number, 'name': node['label'], 'owner': owners[0]})
    return result


def channels(texts):
    records = []
    for file, text in texts.items():
        if not file.endswith(('.dart', '.kt', '.java')):
            continue
        masked = native_bindings.masked(text)
        pattern = r'\b(MethodChannel|EventChannel|BasicMessageChannel)\s*(?:<[^>]+>)?\s*\('
        for call in re.finditer(pattern, masked):
            # Dart takes the literal first; JVM usually takes a messenger first.
            tail = text[call.end():]
            literal = re.match(r'\s*[\'\"]([^\'\"\n$]+)[\'\"]' if file.endswith('.dart')
                               else r'\s*[\w.]+\s*,\s*[\'\"]([^\'\"\n$]+)[\'\"]', tail)
            records.append({'file': file, 'line': native_bindings.line(text, call.start()),
                            'kind': call.group(1), 'name': literal.group(1) if literal else None,
                            'fact': 'extracted', 'status': 'literal-channel-candidate' if literal else 'unresolved-channel-name'})
    return records


def update(root, selected=None):
    paths, excluded = inputs(root, selected)
    cache = root / '.work/codegraph'
    cache.mkdir(parents=True, exist_ok=True)
    request = cache / 'request.json'
    request.write_text(json.dumps({'root': str(root), 'files': [p for p in paths if Path(p).suffix in SOURCES],
                                   'auxiliary': [p for p in paths if Path(p).suffix in RESOURCES]}))
    raw = cache / 'graph.json'
    # Separate mirror from the public adapter default and from other repositories.
    previous = os.environ.get('CODEGRAPH_CACHE')
    os.environ['CODEGRAPH_CACHE'] = str(cache / 'index')
    try:
        graph.extract(request, raw)
    finally:
        if previous is None: os.environ.pop('CODEGRAPH_CACHE', None)
        else: os.environ['CODEGRAPH_CACHE'] = previous
    result = json.loads(raw.read_text())
    texts = {p: (root / p).read_text(errors='replace') for p in paths}
    result.update(schema=1, root=str(root), scope={'files': paths, 'excluded': excluded, 'selected_only': selected is not None},
                  resources=resource_index.index(texts, graph.command()),
                  native_bindings=native_bindings.inventory(texts, jvm_symbols(result['nodes'], texts)),
                  platform_channels=channels(texts), limitations=LIMITS)
    target = cache / 'map.json'
    # Keep an earlier successful map intact if collection or serialization fails.
    with tempfile.NamedTemporaryFile(mode='w', dir=cache, delete=False) as output:
        json.dump(result, output, indent=2)
        temporary = Path(output.name)
    temporary.replace(target)
    return target, result


def query(data, file=None, symbol=None, limit=50):
    nodes = [n for n in data['nodes'] if (not file or n['source_file'] == file)
             and (not symbol or symbol in (n['label'], n.get('qualified_name')))]
    ids = {n['id'] for n in nodes}
    related = {n['source_file'] for n in nodes} | ({file} if file else set())
    edges = [e for e in data['edges'] if e['source'] in ids or e['target'] in ids]
    resources = [e for e in data['resources']['links'] if e['from'] in related or e['to'] in related]
    native = [e for e in data['native_bindings'] if e['file'] in related
              or any(c['file'] in related for c in e.get('candidates', []))]
    channel_names = {e['name'] for e in data['platform_channels'] if e['file'] in related and e['name']}
    channel_records = [e for e in data['platform_channels'] if e['file'] in related or e['name'] in channel_names]
    neighbors = {e[key] for e in edges for key in ('source', 'target')} - ids
    sections = {'symbols': nodes, 'related_symbols': [n for n in data['nodes'] if n['id'] in neighbors],
                'relationships': edges, 'resources': resources,
                'resource_symbols': [n for n in data['resources'].get('symbols', []) if n.get('file') in related],
                'shell': [r for r in data['resources'].get('shell', []) if r.get('file') in related],
                'native_bindings': native,
                'platform_channels': channel_records}
    return {'scope': {'files': len(data['scope'].get('files', [])), 'excluded': len(data['scope'].get('excluded', [])),
                      'selected_only': data['scope'].get('selected_only', False)}, 'notice': 'Saved snapshot; run update after source changes.',
            'totals': {k: len(v) for k,v in sections.items()},
            **{k: v[:limit] for k,v in sections.items()},
            'coverage': [c for c in data['coverage'] if c['file'] in related],
            'resource_coverage': {p:c for p,c in data['resources']['coverage'].items() if p in related},
            'limitations': data['limitations']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path.cwd())
    sub = parser.add_subparsers(dest='command', required=True)
    up = sub.add_parser('update');up.add_argument('--paths', nargs='+')
    q = sub.add_parser('query');q.add_argument('--file');q.add_argument('--symbol');q.add_argument('--limit', type=int, default=50)
    import audit
    audit.configure_parser(sub.add_parser('audit', help='Report-only code, test-quality and coverage-gap scans'), include_root=False)
    s = sub.add_parser('serena', help='Optional semantic query on the private indexed mirror')
    s.add_argument('operation', choices=['symbols', 'definition', 'references', 'version'])
    s.add_argument('--file');s.add_argument('--symbol');s.add_argument('--timeout', type=float, default=120)
    args = parser.parse_args();root = args.root.resolve()
    if args.command == 'update':
        path, data = update(root, args.paths)
        print(json.dumps({'map': str(path), 'symbols': len(data['nodes']), 'relationships': len(data['edges']), 'stats': data['stats']}))
    elif args.command == 'query':
        if not (args.file or args.symbol) or args.limit < 1: parser.error('query needs --file or --symbol and a positive --limit')
        print(json.dumps(query(json.loads((root / '.work/codegraph/map.json').read_text()),args.file,args.symbol,args.limit), indent=2))
    elif args.command == 'audit':
        try:
            raise SystemExit(audit.execute(args))
        except (ValueError, OSError, subprocess.CalledProcessError) as error:
            parser.error(str(error))
    else:
        import sys
        managed=root/'.work/serena-python'/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
        command = [os.environ.get('SERENA_PYTHON') or (str(managed) if managed.is_file() else sys.executable), str(Path(__file__).with_name('serena_adapter.py')),
                   args.operation, '--project', str(root / '.work/codegraph/index/source'), '--timeout', str(args.timeout)]
        if args.file: command += ['--file', args.file]
        if args.symbol: command += ['--symbol', args.symbol]
        subprocess.run(command, check=True)

if __name__ == '__main__':
    main()
