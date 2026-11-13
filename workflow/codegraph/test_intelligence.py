#!/usr/bin/env python3
"""Framework-aware test inventory, quality hints, coverage, flaky and impact
analysis. Heuristics never delete or quarantine tests; tests run only on request."""
from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import time

import audit as audit_module
import native_bindings
import quality
import test_checks

PYTHON_TEST = re.compile(r'^[ \t]*(?:async\s+)?def\s+(test\w*)\s*\(', re.M)
LIMITS = [
    'Test bodies are parsed heuristically; custom runners, parameterization and helpers can be missed.',
    'Similar bodies do not imply equivalent behavior; review fixtures, parameters and protected regression intent.',
    'High line coverage is not high test quality.',
    'Flaky analysis never deletes or quarantines a test; it records observed instability only.',
    'Mutation testing is opt-in, scoped and budgeted; no repository-wide campaign runs by default.',
]


def config(root):
    return json.loads(Path(__file__).with_name('audit-config.json').read_text())


def _all_files(root, settings=None):
    settings = settings or config(root)
    files, _ = audit_module.select_files(Path(root), settings, [], ['.'], include_tests=True)
    return files, settings


def _framework(language, text):
    probes = {
        'dart': ('flutter_test', 'flutter'), 'rust': ('cargo', 'test'),
        'kotlin': ('JUnit', 'JUnit'), 'java': ('org.junit', 'JUnit'),
        'python': ('pytest', 'pytest'),
    }
    value = probes.get(language)
    if not value:
        return language
    if language == 'rust':
        return 'cargo-test'
    return value[1] if value[0] in text else value[1] + ' (imports not found)'


def _category(path, language):
    lowered = path.lower()
    if 'integration_test' in lowered or '/androidtest/' in lowered:
        return 'integration'
    if language == 'dart' and lowered.startswith('test/'):
        return 'widget/unit'
    return 'unit'


def _parse_blocks(language, path, text):
    if language == 'python':
        items = []
        code = native_bindings.masked(text)
        for found in PYTHON_TEST.finditer(code):
            name = found.group(1)
            line = text.count('\n', 0, found.start()) + 1
            end = len(code)
            indent = len(code[found.start():]) - len(code[found.start():].lstrip())
            for match in re.finditer(r'^[ \t]{0,' + str(indent) + r'}\S', code[found.end():], re.M):
                candidate = found.end() + match.start()
                if text.count('\n', 0, candidate) >= line and code[candidate:candidate + indent + 1].strip():
                    end = candidate
                    break
            items.append({'path': path, 'line': line, 'name': name, 'body': text[found.start():end],
                          'head': '', 'start': found.start(), 'end': end,
                          'digest': quality.fingerprint('test-source', 'body', path, name, '', text[found.start():end])})
        return items, [] if items else ['No literal Python test functions recognized']
    return test_checks.blocks(path, text, language)


def inventory(root, files=None, settings=None):
    root = Path(root).resolve()
    files = files or _all_files(root, settings)[0]
    settings = settings or config(root)
    tests, limits = [], {}
    for language, paths in files.items():
        for path in paths:
            if not audit_module.is_test(settings, path):
                continue
            text = (root / path).read_text(errors='replace')
            items, errors = _parse_blocks(language, path, text)
            if errors:
                limits[path] = errors
            for block in items:
                tests.append({
                    'id': f'{path}::{block["name"]}',
                    'identity': block['name'], 'file': path, 'line': block['line'],
                    'framework': _framework(language, text), 'language': language,
                    'category': _category(path, language), 'execution_status': 'NOT_EXECUTED',
                    'duration_s': None, 'associated_production': None,
                    'platform': 'host', 'configuration': 'default',
                    'coverage_available': False, 'body_digest': block['digest'],
                })
    return tests, {'status': quality.STATUS_PARTIAL if limits else quality.STATUS_PASS,
                   'tests': len(tests), 'syntax_limits': limits,
                   'reason': 'Discovery uses parsed test declarations, not a full runner listing'}


def apply_results(tests, results_path):
    """Attach actual runner results (JSON: [{test|id, status, duration}])."""
    if not results_path:
        return tests, quality.execution(quality.STATUS_SKIPPED, reason='No runtime result file supplied')
    data = json.loads(Path(results_path).read_text())
    entries = data.get('tests', data) if isinstance(data, dict) else data
    by_id = {test['id']: test for test in tests}
    by_name = {test['identity']: test for test in tests}
    matched = 0
    for entry in entries:
        target = by_id.get(entry.get('id')) or by_name.get(entry.get('name'))
        if not target:
            continue
        matched += 1
        target['execution_status'] = entry.get('status', 'UNKNOWN')
        target['duration_s'] = entry.get('duration')
    return tests, quality.execution(quality.STATUS_PASS, matched=matched, supplied=len(entries))


def quality_audit(root, files=None, settings=None):
    root = Path(root).resolve()
    settings = settings or config(root)
    files = files or _all_files(root, settings)[0]
    findings, statuses = [], {}
    found, status = test_checks.inspect_tests(root, files, settings['languages'],
                                             lambda path: audit_module.is_test(settings, path))
    findings += [quality.with_schema({**item, 'category': 'test-quality', 'categories': ['test-quality'],
                                      'analyzer': {'name': 'test-quality', 'version': None}}) for item in found]
    statuses['structure'] = {**status,
                             'status': quality.STATUS_INCOMPLETE if status['status'] != 'RAN' else quality.STATUS_PASS}
    heuristic = {'over_mocking': 0, 'fragile_async': 0, 'weak_assertion': 0, 'golden': 0, 'obsolete_candidate': 0}
    for language, paths in files.items():
        for path in paths:
            if not audit_module.is_test(settings, path):
                continue
            text = (root / path).read_text(errors='replace')
            items, _ = _parse_blocks(language, path, text)
            for block in items:
                body = native_bindings.masked(block['body'])
                mocks = len(re.findall(r'\b(?:mock|Mock|fake|Fake|stub|Stub)\w*\s*\(', body))
                asserts = len(test_checks.ASSERT.findall(body))
                if mocks and asserts and mocks > asserts * 2:
                    heuristic['over_mocking'] += 1
                    findings.append(_test_finding(path, block, 'over_mocking',
                                                  f'{mocks} mock/fake constructions vs {asserts} assertions; verify the behavior is still exercised'))
                if re.search(r'Future\.delayed|Thread\.sleep|time\.sleep|\bsleep\s*\(', body):
                    heuristic['fragile_async'] += 1
                    findings.append(_test_finding(path, block, 'fragile_async',
                                                  'Fixed delay/sleep in test body; prefer a deterministic completion signal'))
                if re.search(r'\bisNotNull\b|\bisNotEmpty\b|\bassertNotNull\b|\bassertTrue\(true\)', body) and asserts <= 1:
                    heuristic['weak_assertion'] += 1
                    findings.append(_test_finding(path, block, 'weak_assertion',
                                                  'Only existence/non-null assertions detected; verify the observable value or behavior'))
                if 'matchesGoldenFile' in body:
                    heuristic['golden'] += 1
                if language in ('dart', 'python'):
                    missing = [item for item in _imports(language, text) if not _import_exists(root, path, language, item)]
                    if missing:
                        heuristic['obsolete_candidate'] += 1
                        findings.append(_test_finding(path, block, 'obsolete_candidate',
                                                      f'Imported module not found in this checkout: {missing[0]}; verify before removing anything'))
    statuses['heuristics'] = quality.execution(quality.STATUS_PASS, counts=heuristic,
                                               reason='Review hints only; none is proof of redundancy')
    return findings, statuses


def _imports(language, text):
    if language == 'python':
        return [name for name in re.findall(r'^\s*(?:from|import)\s+([\w.]+)', text, re.M)
                if not name.startswith(('unittest', 'pytest', 'os', 'sys', 'json', 'pathlib', 'typing', 'tempfile'))]
    found = []
    for value in re.findall(r'^\s*import\s+[\'"]([^\'"]+)[\'"]', text, re.M):
        if value.startswith(('./', '../')):
            found.append(value)
    return found


def _import_exists(root, path, language, value):
    if language == 'python':
        return (root / (value.replace('.', '/') + '.py')).is_file() or (root / value.replace('.', '/')).is_dir()
    target = ((root / path).parent / value).resolve()
    return target.is_relative_to(root.resolve()) and target.is_file()


def _test_finding(path, block, code, message):
    return quality.finding('test-quality', 'test-quality', path, block['line'], message, code=code,
                           severity='low', confidence='REVIEW_REQUIRED', evidence='heuristic',
                           symbols=[block['name']],
                           recommendation='Inspect setup, fixtures, parameters and protected behavior before changing the test.',
                           limitations=['Body-level heuristic; helpers and custom runners are not resolved.'])


def coverage(root, files=None, settings=None):
    root = Path(root).resolve()
    settings = settings or config(root)
    files = files or _all_files(root, settings)[0]
    source_files = {language: [p for p in paths if not audit_module.is_test(settings, p)]
                    for language, paths in files.items()}
    source_files = {k: v for k, v in source_files.items() if v}
    found, statuses = test_checks.inspect_coverage(root, source_files, settings.get('coverage', {}))
    findings = [quality.with_schema({**item, 'category': 'coverage', 'categories': ['coverage'],
                                     'analyzer': {'name': 'coverage', 'version': None}}) for item in found]
    summary = {}
    for language, config_value in settings.get('coverage', {}).items():
        report = root / config_value['lcov']
        branch_total = branch_hit = 0
        if report.is_file():
            try:
                for raw in report.read_text().splitlines():
                    if raw.startswith('BRDA:'):
                        parts = raw[5:].split(',')
                        if len(parts) >= 4:
                            branch_total += 1
                            branch_hit += parts[3] not in ('-', '0')
            except OSError:
                pass
        summary[language] = {'line_report': str(report) if report.is_file() else None,
                             'branch_records': branch_total,
                             'branch_hits': branch_hit,
                             'branch_coverage_pct': round(100.0 * branch_hit / branch_total, 1) if branch_total else 'UNKNOWN',
                             'note': 'Line/branch data describes the supplied run only; test quality is separate.'}
    return findings, statuses, summary


def mutation(root, language='rust', paths=(), timeout=900, budget=50, execute=False):
    root = Path(root).resolve()
    if budget < 1 or budget > 200:
        raise ValueError('mutation budget must be between 1 and 200')
    if not paths:
        return [], quality.execution(quality.STATUS_SKIPPED,
                                     reason='Mutation testing requires an explicit --path scope; repository-wide runs are not default')
    if language == 'rust':
        executable = shutil.which('cargo-mutants') or shutil.which('cargo')
        if not executable:
            return [], quality.unavailable_tool('cargo-mutants', 'cargo-mutants is not installed')
        command = [executable] + ([] if Path(executable).name == 'cargo-mutants' else ['mutants']) + \
                  ['--json', '--timeout', '300', '--file', *[str(p) for p in paths]]
        cwd = root / 'rust_backend' if (root / 'rust_backend/Cargo.toml').is_file() else root
    elif language == 'python':
        executable = shutil.which('mutmut')
        if not executable:
            return [], quality.unavailable_tool('mutmut', 'mutmut is not installed')
        command = [executable, 'run', '--paths-to-mutate', ','.join(paths)]
        cwd = root
    else:
        return [], quality.execution(quality.STATUS_UNAVAILABLE,
                                     reason=f'No compatible mutation tool is configured for {language}')
    if not execute:
        return [], quality.execution(quality.STATUS_SKIPPED, reason='Mutation testing is opt-in; re-run with --execute',
                                     command=command, budget=budget)
    done = quality.run(command, cwd, timeout, root / '.work/quality/logs/mutation.log', ok_codes=(0, 1, 2))
    if done['status'] == quality.STATUS_UNAVAILABLE:
        return [], done
    surviving = _mutation_survivors(done.get('stdout', ''))
    findings = [quality.finding('mutation', 'mutation', path, 1,
                                f'Surviving mutant: {detail}', code='surviving-mutant',
                                severity='medium', confidence='CONFIRMED', evidence='mutation-testing',
                                recommendation='Inspect the mutant and add or strengthen a behavioral test.',
                                limitations=['Only mutants within the requested files/budget ran.'])
                for path, detail in surviving]
    partial = done['status'] == quality.STATUS_FAILED
    return findings, quality.execution(quality.STATUS_INCOMPLETE if partial else quality.STATUS_PASS,
                                       command=command, budget=budget,
                                       reason='Partial/timed-out mutation run' if partial else None,
                                       survivors=len(surviving), log=done.get('log'))


def _mutation_survivors(text):
    result = []
    for line in text.splitlines():
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if isinstance(entry, dict) and entry.get('status') in ('survived', 'Survived'):
            path = entry.get('file') or entry.get('source') or 'unknown'
            result.append((path, entry.get('mutant') or entry.get('diff') or 'mutant'))
    return result


def flaky(root, command, repeat=3, timeout=600):
    root = Path(root).resolve()
    if not command:
        return [], quality.execution(quality.STATUS_SKIPPED,
                                     reason='Flaky analysis requires an explicit --command that selects the tests')
    repeat = max(2, min(int(repeat), 10))
    history, findings = [], []
    for attempt in range(1, repeat + 1):
        started = time.monotonic()
        done = quality.run(command, root, timeout, root / f'.work/quality/logs/flaky-{attempt}.log',
                           ok_codes=(0,))
        duration = round(time.monotonic() - started, 3)
        history.append({'attempt': attempt, 'status': done['status'], 'exit_code': done.get('exit_code'),
                        'duration_s': duration})
        if done['status'] == quality.STATUS_UNAVAILABLE:
            return findings, done
    outcomes = {item['exit_code'] for item in history if item['exit_code'] is not None}
    durations = [item['duration_s'] for item in history]
    unstable = len(outcomes) > 1 or any(item['status'] == quality.STATUS_FAILED for item in history)
    if unstable:
        findings.append(quality.finding('flaky', 'flaky', command[0], 1,
                                        f'Mixed outcomes across {repeat} runs: exit codes {sorted(outcomes)}',
                                        code='flaky-test', severity='high', confidence='CONFIRMED',
                                        evidence='repeated-execution',
                                        recommendation='Reproduce with the recorded environment; investigate timing, ordering or shared state. Do not delete or quarantine automatically.',
                                        limitations=['Only the selected command was repeated.']))
    return findings, quality.execution(quality.STATUS_FINDINGS if unstable else quality.STATUS_PASS,
                                       history=history, repeat=repeat,
                                       runtime_variance=round(max(durations) - min(durations), 3),
                                       environment={'cwd': str(root)},
                                       reproduction={'command': command, 'repeat': repeat})


def impact(root, changed, map_path=None):
    root = Path(root).resolve()
    map_path = Path(map_path) if map_path else root / '.work/codegraph/map.json'
    files, settings = _all_files(root)
    tests, _ = inventory(root, files, settings)
    if not map_path.is_file():
        return {'status': quality.STATUS_INCOMPLETE, 'selected_tests': [t['id'] for t in tests],
                'conservative': True, 'reason': 'No CodeGraph snapshot; selecting all discovered tests'}
    data = json.loads(map_path.read_text())
    changed = set(changed or [])
    nodes = {node['id']: node for node in data.get('nodes', [])}
    seeds = {node_id for node_id, node in nodes.items() if node['source_file'] in changed}
    affected = set(seeds)
    for _ in range(4):
        for edge in data.get('edges', []):
            if edge['source'] in affected:
                affected.add(edge['target'])
            if edge['target'] in affected:
                affected.add(edge['source'])
    names = {nodes[i]['label'] for i in affected if i in nodes}
    corpus = {path: (root / path).read_text(errors='replace') for path in
              {t['file'] for t in tests}}
    selected = []
    for test in tests:
        text = native_bindings.masked(corpus.get(test['file'], ''))
        if any(re.search(r'\b' + re.escape(name) + r'\b', text) for name in names) or \
                any(Path(path).stem in text for path in changed):
            selected.append(test['id'])
    coverage_incomplete = len(affected) == len(seeds) and len(seeds) < len(changed)
    return {'status': quality.STATUS_INCOMPLETE if coverage_incomplete else quality.STATUS_PASS,
            'selected_tests': selected or [t['id'] for t in tests] if coverage_incomplete else selected,
            'conservative': coverage_incomplete,
            'reason': 'Dependency information incomplete; selection expanded conservatively' if coverage_incomplete
                      else 'Targeted selection does not replace final full-suite verification',
            'affected_symbols': sorted(names), 'changed': sorted(changed)}


def report(root, name, payload):
    return quality.write_report(root, name, payload)


def run(kind, root, files=None, **options):
    root = Path(root).resolve()
    settings = config(root)
    if kind == 'inventory':
        tests, status = inventory(root, files, settings)
        tests, result_status = apply_results(tests, options.get('results')) if options.get('results') else (tests, status)
        findings = []
        payload = {'title': 'Test inventory', 'status': result_status['status'], 'kind': 'test-inventory',
                   'scope': {'paths': ['.']}, 'tools': {'discovery': status}, 'tests': tests,
                   'findings': findings, 'summary': {'tests': len(tests)},
                   'limitations': LIMITS}
    elif kind == 'quality':
        findings, tools = quality_audit(root, files, settings)
        status = quality.result_status(tools, findings)
        payload = {'title': 'Test quality audit', 'status': status, 'kind': 'test-quality',
                   'scope': {'paths': ['.']}, 'tools': tools, 'findings': findings,
                   'summary': {'findings': len(findings)}, 'limitations': LIMITS}
    elif kind == 'coverage':
        findings, tools, summary = coverage(root, files, settings)
        status = quality.result_status(tools, findings)
        payload = {'title': 'Coverage intelligence', 'status': status, 'kind': 'coverage',
                   'scope': {'paths': ['.']}, 'tools': tools, 'findings': findings,
                   'summary': summary, 'limitations': LIMITS}
    elif kind == 'mutation':
        findings, status = mutation(root, options.get('language', 'rust'), options.get('paths', ()),
                                    options.get('timeout', 900), options.get('budget', 50),
                                    options.get('execute', False))
        payload = {'title': 'Mutation analysis', 'status': status['status'], 'kind': 'mutation',
                   'scope': {'paths': list(options.get('paths', []))}, 'tools': {'mutation': status},
                   'findings': findings, 'summary': {'survivors': len(findings)}, 'limitations': LIMITS}
    elif kind == 'flaky':
        findings, status = flaky(root, options.get('command'), options.get('repeat', 3),
                                 options.get('timeout', 600))
        payload = {'title': 'Flaky test analysis', 'status': status['status'], 'kind': 'flaky',
                   'scope': {'repeat': status.get('repeat')}, 'tools': {'flaky': status},
                   'findings': findings, 'summary': status.get('history', []), 'limitations': LIMITS}
    elif kind == 'impact':
        result = impact(root, options.get('changed', ()), options.get('map'))
        payload = {'title': 'Test impact analysis', 'status': result['status'], 'kind': 'test-impact',
                   'scope': {'changed': list(options.get('changed', []))}, 'tools': {},
                   'findings': [], 'impact': result, 'summary': {'selected_tests': len(result['selected_tests'])},
                   'limitations': LIMITS}
    else:
        raise ValueError(f'unknown test intelligence kind: {kind}')
    json_path, md_path = report(root, f'tests-{kind}', payload)
    payload['report'], payload['report_markdown'] = str(json_path), str(md_path)
    return payload
