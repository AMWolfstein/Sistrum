#!/usr/bin/env python3
"""Local CodeGraph CLI/SQLite adapter. No agent installer, npm download or source execution.

Index only a private copy of requested inputs under CODEGRAPH_CACHE. Python reads the
resulting SQLite database in read-only mode. This tool has no task/review pipeline.
"""
from __future__ import annotations
import argparse
import json
import os
import sqlite3
import subprocess
import sys
from pathlib import Path, PurePosixPath

PACKAGE = '@colbymchenry/codegraph'
SCHEMA = 1


def command():
    configured = os.environ.get('CODEGRAPH_COMMAND')
    if configured is None:
        repository = Path(__file__).resolve().parents[2]
        for path in [Path(__file__).parent / 'node_modules/.bin/codegraph',
                     repository / '.work/tools/node_modules/.bin/codegraph']:
            if path.is_file():
                return [str(path)]
    value = json.loads(configured or '["codegraph"]')
    if not isinstance(value, list) or not value or not all(isinstance(v, str) and v for v in value):
        raise ValueError('CODEGRAPH_COMMAND must be a JSON argv list')
    return value


def run(args, cwd=None):
    done = subprocess.run([*command(), *args], cwd=cwd, capture_output=True, text=True,
                          env={**os.environ, 'CODEGRAPH_TELEMETRY': '0', 'DO_NOT_TRACK': '1', 'CODEGRAPH_NO_DOWNLOAD': '1'}, timeout=840)
    if done.returncode:
        raise RuntimeError(f'CodeGraph exit {done.returncode}: {(done.stderr + done.stdout)[-1500:]}')
    return done.stdout


def version():
    info = {'schema': SCHEMA, 'package': PACKAGE}
    try:
        installed = run(['--version']).strip()
        return {**info, 'available': True, 'version': installed}
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        return {**info, 'available': False, 'reason': str(error)}


def safe_relative(value):
    if not isinstance(value, str) or not value or '\\' in value:
        raise ValueError('invalid corpus path')
    path = PurePosixPath(value)
    if not path.parts or ':' in value or path.as_posix() != value or path.is_absolute() or '..' in path.parts or path.parts[0] in ('.git', '.codegraph', '.work'):
        raise ValueError(f'unsafe corpus path: {value}')
    return path


def stage(root, cache, paths):
    """Persistent private source mirror: preserve mtimes of unchanged inputs for sync."""
    mirror = cache / 'source'
    mirror.mkdir(parents=True, exist_ok=True)
    manifest = cache / 'inputs.json'
    previous = json.loads(manifest.read_text()) if manifest.exists() else []
    for rel in set(previous) - set(paths):
        obsolete = mirror / safe_relative(rel)
        obsolete.unlink(missing_ok=True)
        parent = obsolete.parent
        while parent != mirror:
            try:
                parent.rmdir()
            except OSError:
                break
            parent = parent.parent
    for rel in paths:
        safe_relative(rel)
        source, target = root / rel, mirror / rel
        if source.is_symlink() or not source.resolve().is_relative_to(root):
            raise ValueError(f'symlink/outside-root input: {rel}')
        content = source.read_bytes()
        if not target.exists() or target.read_bytes() != content:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
    # Control scanner options; never inherit a second, silently different corpus policy.
    (mirror / 'codegraph.json').write_text(json.dumps({'include': paths, 'exclude': []}))
    manifest.write_text(json.dumps(paths))
    return mirror


def read_database(database, requested):
    db = sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    try:
        files = {r['path']: dict(r) for r in db.execute('SELECT path,language,node_count,errors FROM files')}
        nodes, by_id = [], {}
        for row in db.execute('SELECT * FROM nodes'):
            n = dict(row)
            if n['file_path'] not in requested:
                continue
            by_id[n['id']] = n
            nodes.append({'id': n['id'], 'label': n['name'], 'kind': n['kind'],
                          'source_file': n['file_path'], 'source_location': 'L' + str(n['start_line']),
                          'end_line': n['end_line'], 'language': n['language'],
                          'qualified_name': n['qualified_name']})
        edges, rejected = [], 0
        for row in db.execute('SELECT * FROM edges'):
            e = dict(row); source, target = by_id.get(e['source']), by_id.get(e['target'])
            if not source or not target:
                continue
            if e['kind'] == 'calls' and source['language'] != target['language'] and not (
                    {source['language'], target['language']} <= {'c', 'cpp'}):
                rejected += 1
                continue  # cross-language calls require an explicit bridge/binding, not a shared name
            metadata = json.loads(e['metadata'] or '{}')
            inferred = e['kind'] not in ('contains', 'defines')
            edges.append({'source': e['source'], 'target': e['target'], 'relation': e['kind'],
                          'source_file': source['file_path'],
                          'source_location': 'L' + str(e['line'] or source['start_line']),
                          'confidence': 'INFERRED' if inferred else 'EXTRACTED',
                          'context': metadata.get('resolvedBy'), 'evidence': metadata})
        unresolved = {r[0]: r[1] for r in db.execute(
            "SELECT file_path,COUNT(*) FROM unresolved_refs WHERE status='pending' GROUP BY file_path")}
        coverage = []
        for path in sorted(requested):
            item = files.get(path)
            if item is None:
                coverage.append({'file': path, 'status': 'not-indexed', 'errors': [], 'symbols': 0})
                continue
            errors = json.loads(item['errors'] or '[]')
            coverage.append({'file': path, 'language': item['language'], 'status': 'partial' if errors else 'indexed',
                             'errors': errors, 'symbols': item['node_count'], 'unresolved_references': unresolved.get(path, 0)})
        return nodes, edges, coverage, rejected
    finally:
        db.close()


def extract(request_path, out):
    request = json.loads(request_path.read_text())
    root = Path(request['root']).resolve()
    cache = Path(os.environ.get('CODEGRAPH_CACHE', str(Path(__file__).resolve().parents[2] / '.work/codegraph')))
    if not cache.is_absolute():
        raise ValueError('CODEGRAPH_CACHE must be absolute')
    cache.mkdir(parents=True, exist_ok=True)
    requested = set(request['files'])
    auxiliary = request.get('auxiliary', [])
    paths = sorted(requested | set(auxiliary))
    info = version()
    if not info['available']:
        raise RuntimeError(info['reason'])
    mirror = stage(root, cache, paths)
    database = mirror / '.codegraph/codegraph.db'
    run(['sync', str(mirror)] if database.exists() else ['init', str(mirror), '--yes'])
    nodes, edges, coverage, rejected = read_database(database, requested)
    result = {'schema': SCHEMA, 'tool': info, 'nodes': nodes, 'edges': edges, 'coverage': coverage,
              'stats': {'extracted_sources': sum(c['status'] == 'indexed' for c in coverage),
                        'partial_sources': sum(c['status'] == 'partial' for c in coverage),
                        'unindexed_sources': sum(c['status'] == 'not-indexed' for c in coverage),
                        'rejected_cross_language_calls': rejected}}
    temporary = out.with_suffix('.tmp')
    temporary.write_text(json.dumps(result))
    os.replace(temporary, out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('version')
    cmd = sub.add_parser('extract'); cmd.add_argument('--request', type=Path, required=True); cmd.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.action == 'version': print(json.dumps(version()))
        else: extract(args.request, args.out)
        return 0
    except (OSError, ValueError, KeyError, RuntimeError, sqlite3.Error, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
