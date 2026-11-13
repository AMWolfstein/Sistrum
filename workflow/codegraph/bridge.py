#!/usr/bin/env python3
"""Cross-language bridge discovery, contract verification and scorecard.

Extends the existing native/channel evidence; it is not a second analysis
framework. Every unresolved mechanism stays explicit and never becomes a clean
result. No device, APK or runtime test is started here.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess

import native_bindings
import quality

SOURCES = {'.dart', '.kt', '.java', '.rs', '.c', '.cpp', '.cc', '.cxx', '.h', '.hpp'}
EXCLUDED = {'.git', '.work', 'build', 'target', 'node_modules', 'third_party', 'vendor',
            '__pycache__', '.dart_tool'}
PASS, FAIL, NA, UNVERIFIED = 'PASS', 'FAIL', 'NOT_APPLICABLE', 'NOT_VERIFIED'
CHECK_NAMES = ('symbol_resolution', 'signature_compatibility', 'type_compatibility',
               'abi_compatibility', 'calling_conventions', 'memory_ownership', 'resource_disposal',
               'thread_safety', 'lifecycle', 'cancellation_propagation', 'error_propagation',
               'callback_lifecycle', 'runtime_execution')
REQUIRED = {
    'jni': ('symbol_resolution', 'signature_compatibility', 'type_compatibility', 'abi_compatibility',
            'calling_conventions', 'memory_ownership', 'resource_disposal', 'thread_safety', 'lifecycle',
            'cancellation_propagation', 'error_propagation', 'callback_lifecycle', 'runtime_execution'),
    'platform-channel': ('symbol_resolution', 'type_compatibility', 'thread_safety', 'lifecycle',
                         'cancellation_propagation', 'error_propagation', 'runtime_execution'),
    'uniffi': ('symbol_resolution', 'signature_compatibility', 'type_compatibility', 'abi_compatibility',
               'memory_ownership', 'runtime_execution'),
    'c-abi': ('symbol_resolution', 'signature_compatibility', 'type_compatibility', 'abi_compatibility',
              'memory_ownership', 'runtime_execution'),
    'jni-dynamic-registration': ('symbol_resolution', 'runtime_execution'),
}
LIMITS = [
    'Static discovery cannot prove runtime linkage, calling convention, ABI or ownership.',
    'Generated UniFFI/Pigeon surfaces may be produced only at build time; missing generated files are unresolved.',
    'Dynamic registration, reflection, computed channel names and callbacks are never guessed.',
    'Runtime coverage is 0/UNKNOWN unless a runtime report from actual executed tests is supplied.',
    'Bridge verification never authorizes removing code; separate unreachability evidence is required.',
]


def collect(root, paths=None):
    root = Path(root)
    if paths:
        selected = [str(Path(p).as_posix()) for p in paths]
    elif (root / '.git').exists():
        done = subprocess.run(['git', '-C', str(root), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                              capture_output=True, check=True)
        selected = sorted(set(done.stdout.decode().split('\0')) - {''})
    else:
        selected = sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file())
    texts = {}
    for raw in selected:
        parts = Path(raw).parts
        if any(part in EXCLUDED for part in parts) or Path(raw).suffix not in SOURCES:
            continue
        target = root / raw
        if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()) or not target.is_file():
            continue
        texts[raw] = target.read_text(errors='replace')
    return texts


def _owner(code, offset):
    intervals = []
    for header in re.finditer(r'\b(?:class|object|interface)\s+(\w+)[^{\n]*\{', code):
        depth, end = 1, header.end()
        while end < len(code) and depth:
            depth += (code[end] == '{') - (code[end] == '}')
            end += 1
        intervals.append((header.end(), end, header.group(1)))
    owners = [name for start, end, name in intervals if start <= offset < end]
    return owners[-1] if owners else None


def _params(text, start):
    """Top-level comma count between the parenthesis starting at/after `start`."""
    index = text.find('(', start)
    if index < 0:
        return None
    depth, content = 0, []
    while index < len(text):
        char = text[index]
        if char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                break
        elif depth == 1:
            content.append(char)
        index += 1
    body = ''.join(content).strip()
    if not body:
        return 0
    return body.count(',') + 1


def discover(root, paths=None):
    root = Path(root).resolve()
    texts = collect(root, paths)
    declarations = []
    for path, original in texts.items():
        if not path.endswith(('.kt', '.java')):
            continue
        code = native_bindings.masked(original)
        package = re.search(r'^\s*package\s+([\w.]+)', code, re.M)
        for found in re.finditer(r'\b(?:external|native)\s+fun\s+(\w+)\s*\(', code):
            owner = _owner(code, found.start())
            if owner:
                declarations.append({'file': path, 'line': native_bindings.line(code, found.start()),
                                     'name': found.group(1), 'owner': owner,
                                     'package': package.group(1) if package else '',
                                     'params': _params(code, found.start())})
        for found in re.finditer(r'\bnative\s+[\w<>\[\], .]+\s+(\w+)\s*\(', code):
            if code[max(0, found.start() - 20):found.start()].rstrip().endswith('fun'):
                continue
            owner = _owner(code, found.start())
            if owner:
                declarations.append({'file': path, 'line': native_bindings.line(code, found.start()),
                                     'name': found.group(1), 'owner': owner,
                                     'package': package.group(1) if package else '',
                                     'params': _params(code, found.start())})

    native_records = native_bindings.inventory(texts, declarations)
    channels = native_bindings.channels(texts)
    generated = sorted(path for path in texts
                       if path.endswith('.g.dart') or 'pigeon' in Path(path).name.lower()
                       or 'uniffi' in Path(path).name.lower())

    contracts = []
    for record in native_records:
        if record['kind'] == 'JNI declaration':
            contracts.append(_jni_contract(root, texts, record, record.get('candidates', [])))
        elif record['kind'] == 'JNI dynamic registration':
            contracts.append(_contract('jni-dynamic-registration', f'dynamic:{record["file"]}:{record["line"]}',
                                       'kotlin/java', 'native', [record], {
                                           'symbol_resolution': (UNVERIFIED, 'RegisterNatives names are computed at runtime'),
                                           'runtime_execution': (UNVERIFIED, 'No runtime registration evidence'),
                                       }, _scope(root)))
        elif record['kind'] == 'UniFFI export surface':
            contracts.append(_contract('uniffi', f'uniffi:{record["file"]}:{record["line"]}',
                                       'rust', 'kotlin/java', [record], {
                                           'symbol_resolution': (PASS if any('uniffi' in p for p in generated) else UNVERIFIED,
                                                                 'Generated UniFFI file ' + ('present' if generated else 'not found in checkout')),
                                           'runtime_execution': (UNVERIFIED, 'No runtime evidence'),
                                       }, _scope(root)))
    contracts.extend(_c_abi_contracts(root, texts))
    contracts.extend(_channel_contracts(root, channels, generated))
    for contract in contracts:
        _finish(contract)
    inventory = {
        'jvm_native_declarations': sum(1 for d in declarations),
        'literal_channels': sum(1 for c in channels if c.get('name')),
        'unresolved_channels': sum(1 for c in channels if not c.get('name')),
        'uniffi_exports': sum(1 for r in native_records if r['kind'] == 'UniFFI export surface'),
        'dynamic_registrations': sum(1 for r in native_records if r['kind'] == 'JNI dynamic registration'),
        'generated_binding_files': generated,
    }
    known = inventory['jvm_native_declarations'] + inventory['literal_channels'] + inventory['uniffi_exports'] \
        + inventory['dynamic_registrations']
    return {'root': str(root), 'texts': texts, 'contracts': contracts, 'inventory': inventory,
            'known_contract_declarations': known, 'generated': generated,
            'limitations': LIMITS, 'scope': _scope(root)}


def _scope(root):
    return {'platform': 'android', 'variant': 'debug', 'abi': 'arm64-v8a', 'target': 'android-arm64',
            'note': 'Default static scope; other variants/ABIs are unverified.'}


def _contract(mechanism, name, source, target, declarations, checks, scope, **extra):
    locations = sorted(f'{d.get("file")}:{d.get("line")}' for d in declarations)
    digest = quality.fingerprint('bridge', mechanism, '|'.join(locations), name)
    result = {
        'schema': quality.SCHEMA, 'id': 'BR-' + digest[:12], 'fingerprint': digest,
        'mechanism': mechanism, 'name': name, 'source_language': source, 'target_language': target,
        'declarations': declarations, 'checks': {}, 'scope': dict(scope),
        'unresolved_references': [], 'evidence': [{'kind': 'source', 'detail': f'{l}'} for l in locations],
        'optimization': {}, 'status': UNVERIFIED,
    }
    for check, value in checks.items():
        detail, evidence = (value if isinstance(value, tuple) else (value, ''))
        result['checks'][check] = {'status': detail, 'detail': evidence}
    result.update(extra)
    return result


def _jni_contract(root, texts, record, candidates):
    path, number, name = record['file'], record['line'], record.get('name', '')
    declared = _decl_params(texts.get(path, ''), number, name)
    candidate_params = []
    for candidate in candidates:
        text = texts.get(candidate['file'], '')
        count = _cpp_params(text, candidate['line'], record.get('jni_name', ''))
        candidate_params.append({'file': candidate['file'], 'line': candidate['line'], 'params': count})
    resolution = (PASS, f'{len(candidates)} native implementation candidate(s)') if candidates \
        else (FAIL, 'No JNI implementation symbol found')
    signature = (UNVERIFIED, 'Parameter counts unavailable')
    if candidates and declared is not None and candidate_params and candidate_params[0]['params'] is not None:
        same = declared == candidate_params[0]['params']
        signature = (PASS if same else FAIL,
                     f'JVM declares {declared} parameter(s); native candidate has {candidate_params[0]["params"]}')
    fallback = _fallback_near(texts.get(path, ''), number)
    contract = _contract('jni', record.get('jni_name') or name, 'kotlin/java', 'cpp/rust',
                         [record], {
                             'symbol_resolution': resolution,
                             'signature_compatibility': signature,
                             'type_compatibility': (UNVERIFIED, 'Static parameter counts do not prove JNI types'),
                             'abi_compatibility': (UNVERIFIED, 'No ABI/build-variant evidence'),
                             'calling_conventions': (UNVERIFIED, 'Requires generated headers/runtime evidence'),
                             'memory_ownership': (UNVERIFIED, 'Requires implementation review'),
                             'resource_disposal': (NA, 'Not determinable without the implementation contract'),
                             'thread_safety': (UNVERIFIED, 'Requires implementation and call-site review'),
                             'lifecycle': (UNVERIFIED, 'Requires runtime/lifecycle tests'),
                             'cancellation_propagation': (UNVERIFIED, 'Requires runtime evidence'),
                             'error_propagation': (UNVERIFIED, 'Requires runtime evidence'),
                             'callback_lifecycle': (UNVERIFIED, 'Requires runtime evidence'),
                             'runtime_execution': (UNVERIFIED, 'No runtime test report supplied'),
                         }, _scope(root), caller={'file': path, 'line': number, 'symbol': name},
                         callee={'symbol': record.get('jni_name'), 'candidates': candidate_params},
                         declared_params=declared, fallback_paths=[path] if fallback else [],
                         variables=record.get('jni_name'), reachable=True)
    if not candidates:
        contract['unresolved_references'].append({'kind': 'native-implementation', 'symbol': record.get('jni_name')})
    return contract


def _decl_params(text, number, name):
    lines = text.splitlines()
    if not 0 < number <= len(lines):
        return None
    start = '\n'.join(lines[number - 1:number + 3])
    found = re.search(r'\b' + re.escape(name) + r'\s*\(', start)
    return _params(start, found.start()) if found else None


def _cpp_params(text, number, jni_name):
    lines = text.splitlines()
    if not 0 < number <= len(lines):
        return None
    start = '\n'.join(lines[number - 1:number + 3])
    found = re.search(re.escape(jni_name) + r'\s*\(', start)
    if not found:
        return None
    params = _params(start, found.start())
    return None if params is None else max(params - 2, 0)  # JNIEnv*, jobject/jclass


def _fallback_near(text, number):
    lines = text.splitlines()
    window = '\n'.join(lines[max(0, number - 8):number + 8]).lower()
    return any(word in window for word in ('fallback', 'fall back', 'unsupported'))


def _c_abi_contracts(root, texts):
    contracts = []
    for path, text in texts.items():
        if not path.endswith(('.rs', '.c', '.cpp', '.cc', '.cxx')):
            continue
        code = text
        for found in re.finditer(r'(?:#\[(?:unsafe\()?no_mangle(?:\))?\]\s*)?(?:pub\s+)?(?:unsafe\s+)?extern\s+"C"\s+(?:fn\s+(\w+)|(?:void|int|char|size_t|[\w:<>* ]+)\s+(\w+)\s*\()', code):
            name = found.group(1) or found.group(2)
            if not name or name.startswith('Java_'):
                continue
            prefix = code[max(0, found.start() - 200):found.start()]
            archs = re.findall(r'target_arch\s*=\s*"([^"]+)"', prefix)
            expected = {'arm64-v8a': 'aarch64', 'armeabi-v7a': 'arm', 'x86_64': 'x86_64'}.get(_scope(root)['abi'])
            if archs and expected and expected not in archs:
                abi = (FAIL, f'Export is compiled for {archs}, configured scope is {expected}')
            elif archs:
                abi = (PASS, f'Export cfg matches {archs}')
            else:
                abi = (UNVERIFIED, 'No cfg/ABI evidence')
            contracts.append(_contract('c-abi', name, 'rust/cpp', 'kotlin/java', [{
                'file': path, 'line': native_bindings.line(code, found.start()), 'name': name}], {
                'symbol_resolution': (PASS, 'Export symbol declared'),
                'signature_compatibility': (UNVERIFIED, 'Requires generated headers'),
                'type_compatibility': (UNVERIFIED, 'Requires generated headers'),
                'abi_compatibility': abi,
                'memory_ownership': (UNVERIFIED, 'Requires implementation review'),
                'runtime_execution': (UNVERIFIED, 'No runtime test report supplied'),
            }, _scope(root), exported_symbols=[name], reachable=True))
    return contracts


def _channel_contracts(root, channels, generated):
    sides = {}
    for record in channels:
        if not record.get('name'):
            continue
        side = 'dart' if record['file'].endswith('.dart') else 'jvm'
        sides.setdefault(record['name'], {'dart': [], 'jvm': []})[side].append(record)
    contracts = []
    for name, record in sorted(sides.items()):
        records = record['dart'] + record['jvm']
        if record['dart'] and record['jvm']:
            resolution = (PASS, 'Literal name present on both Dart and JVM sides')
            unresolved = []
        else:
            resolution = (UNVERIFIED, f'Only the {("Dart" if record["dart"] else "JVM")} side was found; computed/other handlers remain unresolved')
            unresolved = [{'kind': 'channel-counterpart', 'channel': name}]
        generated_match = [path for path in generated if name in (Path(root) / path).read_text(errors='replace')]
        contracts.append(_contract('platform-channel', name, 'dart', 'kotlin/java', records, {
            'symbol_resolution': resolution,
            'type_compatibility': (UNVERIFIED, 'Codec payload types are not statically resolved'),
            'thread_safety': (UNVERIFIED, 'Handler thread confinement requires runtime evidence'),
            'lifecycle': (UNVERIFIED, 'Requires lifecycle tests'),
            'cancellation_propagation': (UNVERIFIED, 'Requires runtime evidence'),
            'error_propagation': (UNVERIFIED, 'Requires runtime evidence'),
            'runtime_execution': (UNVERIFIED, 'No runtime test report supplied'),
        }, _scope(root), unresolved_references=unresolved,
            generated_binding=generated_match, reachable=True))
    for record in channels:
        if record.get('name'):
            continue
        contracts.append(_contract('platform-channel', f'unresolved:{record["file"]}:{record["line"]}',
                                   'dart', 'kotlin/java', [record], {
                                       'symbol_resolution': (UNVERIFIED, 'Computed channel name'),
                                       'runtime_execution': (UNVERIFIED, 'No runtime evidence'),
                                   }, _scope(root), unresolved_references=[{'kind': 'computed-channel-name'}]))
    return contracts


def _finish(contract):
    checks = contract['checks']
    for name in REQUIRED[contract['mechanism']]:
        checks.setdefault(name, {'status': UNVERIFIED, 'detail': 'Not evaluated'})
    applicable = [value for value in checks.values() if value['status'] != NA]
    if any(value['status'] == FAIL for value in applicable):
        contract['status'] = 'FAILED'
    elif applicable and all(value['status'] == PASS for value in applicable):
        contract['status'] = 'VERIFIED'
    elif any(value['status'] == PASS for value in applicable):
        contract['status'] = 'PARTIAL'
    else:
        contract['status'] = 'UNVERIFIED'
    reasons = []
    if contract['status'] != 'VERIFIED':
        reasons.append(f'Contract status is {contract["status"]}')
    if contract['unresolved_references']:
        reasons.append(f'{len(contract["unresolved_references"])} unresolved reference(s)')
    reasons.append('Bridge verification does not authorize removal; separate unreachability evidence is required')
    contract['optimization'] = {'eligible': False,
                                'status': 'REVIEW_REQUIRED' if contract['status'] == 'VERIFIED' and not contract['unresolved_references'] else 'BLOCKED',
                                'reasons': reasons}


def apply_runtime(contracts, report):
    """Attach actual runtime results supplied by the owner; never infer them."""
    if not report:
        return contracts
    data = json.loads(Path(report).read_text())
    entries = data.get('contracts', data) if isinstance(data, dict) else {}
    for contract in contracts:
        entry = entries.get(contract['id']) or entries.get(contract['name'])
        if not isinstance(entry, dict):
            continue
        passed = entry.get('passed')
        detail = entry.get('detail') or ', '.join(entry.get('tests', []))
        contract['checks']['runtime_execution'] = {
            'status': PASS if passed is True else FAIL if passed is False else UNVERIFIED,
            'detail': detail or 'Runtime report entry had no result', 'tests': entry.get('tests', [])}
        contract['runtime'] = {'source': str(report), **entry}
    return contracts


def scorecard(contracts, inventory=None):
    inventory = inventory or {}
    reference = sum(int(inventory.get(key, 0)) for key in
                    ('jvm_native_declarations', 'literal_channels', 'unresolved_channels',
                     'uniffi_exports', 'dynamic_registrations'))
    discovered = 0
    for contract in contracts:
        if contract['mechanism'] in ('jni', 'uniffi', 'jni-dynamic-registration'):
            discovered += len(contract['declarations'])
        elif contract['mechanism'] == 'platform-channel':
            discovered += sum(1 for record in contract['declarations'] if record.get('name'))
    unresolved = [c['id'] for c in contracts if c['status'] in ('UNVERIFIED', 'PARTIAL') or c['unresolved_references']]
    failed = [c['id'] for c in contracts if c['status'] == 'FAILED']
    applicable, verified = 0, 0
    for contract in contracts:
        checks = [v for v in contract['checks'].values() if v['status'] != NA]
        applicable += len(checks)
        verified += sum(v['status'] == PASS for v in checks)
    runtime_required = [c for c in contracts if c['mechanism'] in REQUIRED]
    runtime_verified = [c for c in runtime_required
                        if c['checks'].get('runtime_execution', {}).get('status') == PASS]
    scopes = {}
    for contract in contracts:
        key = '/'.join(str(contract['scope'][k]) for k in ('platform', 'variant', 'abi'))
        bucket = scopes.setdefault(key, {'contracts': 0, 'verified': 0})
        bucket['contracts'] += 1
        bucket['verified'] += contract['status'] == 'VERIFIED'
    return {
        'discovery': {'discovered_contracts': len(contracts),
                      'discovered_declarations': discovered,
                      'reference_inventory_size': reference,
                      'coverage_pct': 'UNKNOWN' if not reference else round(min(100.0 * discovered / reference, 100.0), 1),
                      'reference_inventory': 'Independent declaration extraction (JVM native declarations, literal channels, UniFFI exports, dynamic registrations)',
                      'note': 'UNKNOWN when the reference inventory is empty; analyzer self-coverage is never reported as discovery coverage.'},
        'static_verification': {'applicable_checks': applicable, 'verified_checks': verified,
                                'coverage_pct': round(100.0 * verified / applicable, 1) if applicable else 'UNKNOWN',
                                'unresolved_checks': sum(1 for c in contracts for v in c['checks'].values() if v['status'] == UNVERIFIED)},
        'runtime_verification': {'required_contracts': len(runtime_required),
                                 'verified_contracts': len(runtime_verified),
                                 'coverage_pct': round(100.0 * len(runtime_verified) / len(runtime_required), 1) if runtime_required else 'UNKNOWN',
                                 'note': 'Only actual supplied runtime test results count; compilation is not runtime verification.'},
        'unresolved_contracts': {'count': len(unresolved), 'ids': unresolved},
        'failed_contracts': {'count': len(failed), 'ids': failed},
        'platform_coverage': scopes,
    }


def verify(root, paths=None, runtime_report=None):
    root = Path(root).resolve()
    data = discover(root, paths)
    apply_runtime(data['contracts'], runtime_report)
    for contract in data['contracts']:
        _finish(contract)
    data['scorecard'] = scorecard(data['contracts'], data['inventory'])
    data['status'] = ('FAILED' if data['scorecard']['failed_contracts']['count'] else
                      'INCOMPLETE' if any(c['status'] != 'VERIFIED' for c in data['contracts']) else 'PASS')
    payload = {'title': 'Bridge verification', 'status': data['status'],
               'scope': {'paths': list(paths or []) or ['.'], **data['scope']},
               'inventory': data['inventory'], 'scorecard': data['scorecard'],
               'contracts': data['contracts'], 'findings': _findings(data['contracts']),
               'summary': {'contracts': len(data['contracts']),
                           'verified': sum(c['status'] == 'VERIFIED' for c in data['contracts']),
                           'failed': data['scorecard']['failed_contracts']['count'],
                           'unresolved': data['scorecard']['unresolved_contracts']['count']},
               'limitations': data['limitations']}
    json_path, md_path = quality.write_report(root, 'bridge-verification', payload,
                                              markdown_text=_markdown(payload))
    payload['report'] = str(json_path)
    payload['report_markdown'] = str(md_path)
    return payload


def _findings(contracts):
    result = []
    for contract in contracts:
        if contract['status'] in ('FAILED', 'UNVERIFIED') or contract['unresolved_references']:
            first = contract['declarations'][0]
            bad = [name for name, value in contract['checks'].items() if value['status'] == FAIL]
            result.append(quality.finding(
                'bridge', 'bridge', first['file'], first['line'],
                f'{contract["mechanism"]} {contract["name"]}: {contract["status"]}'
                + (f'; failed checks: {", ".join(bad)}' if bad else ''),
                code=contract['mechanism'], severity='high' if bad else 'medium',
                confidence='CONFIRMED' if bad else 'UNCERTAIN', evidence='bridge-verification',
                symbols=[contract['name']], recommendation='Resolve the listed contract checks before changing this bridge.',
                limitations=contract['unresolved_references'] and ['Unresolved references remain.'] or [],
                unresolved=[r.get('kind') for r in contract['unresolved_references']]))
    return result


def _markdown(payload):
    score = payload['scorecard']
    lines = ['# Bridge verification', '', f'Status: **{payload["status"]}**', '',
             '## Scorecard', '', '| Metric | Value |', '|---|---|',
             f'| Discovery coverage | {score["discovery"]["coverage_pct"]}% |',
             f'| Static verification coverage | {score["static_verification"]["coverage_pct"]}% |',
             f'| Runtime verification coverage | {score["runtime_verification"]["coverage_pct"]}% |',
             f'| Unresolved contracts | {score["unresolved_contracts"]["count"]} |',
             f'| Failed contracts | {score["failed_contracts"]["count"]} |', '']
    lines += ['## Contracts', '', '| ID | Mechanism | Name | Status | Eligible |', '|---|---|---|---|---|']
    for contract in payload['contracts']:
        lines.append(f'| {contract["id"]} | {contract["mechanism"]} | {contract["name"]} '
                     f'| {contract["status"]} | {contract["optimization"]["status"]} |')
    lines += ['', '## Limits', ''] + [f'- {note}' for note in payload['limitations']]
    return '\n'.join(lines)


def bridge_index(root, paths=None):
    """Names/files that are bridge-reachable, for optimistic dead-code guards."""
    data = discover(root, paths)
    names, files = set(), set()
    for contract in data['contracts']:
        names.add(contract['name'])
        names.add(contract['name'].split('_')[-1])
        for declaration in contract['declarations']:
            files.add(declaration['file'])
            if declaration.get('name'):
                names.add(declaration['name'])
        for candidate in contract.get('callee', {}).get('candidates', []):
            files.add(candidate['file'])
            names.add(candidate.get('symbol') or '')
    return {'names': sorted(n for n in names if n), 'files': sorted(f for f in files if f), 'contracts': len(data['contracts'])}
