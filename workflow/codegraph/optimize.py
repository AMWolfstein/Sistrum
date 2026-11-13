#!/usr/bin/env python3
"""Universal safe optimization: AUDIT (default), PLAN and explicitly approved APPLY.

Recommendations are evidence, never permission. APPLY refuses production removal
without separate unreachability evidence and refuses bridge-reachable symbols.
Only same-file, byte-identical duplicate test bodies are executable automatically.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from datetime import datetime, timezone

import bridge
import quality
import test_intelligence

TEST_ACTIONS = ('KEEP', 'MERGE', 'REWRITE', 'REMOVE', 'ADD_COVERAGE', 'INVESTIGATE')
PROD_ACTIONS = ('KEEP', 'SIMPLIFY', 'REFACTOR', 'MERGE', 'REMOVE', 'INVESTIGATE')
LIMITS = [
    'Recommendations are review candidates; missing evidence stays UNKNOWN and is never an improvement.',
    'Bridge-reachable symbols are never removal candidates regardless of same-language references.',
    'APPLY requires explicit item approval, repository-state verification, backup and rollback.',
    'A VERIFIED bridge contract does not by itself authorize removal.',
    'No Sistrum production code or application test is changed by this upgrade.',
]


def _load_reports(root):
    findings, sources = [], []
    for path in sorted((root / '.work/quality').glob('*.json')) if (root / '.work/quality').is_dir() else []:
        try:
            data = json.loads(path.read_text())
        except (OSError, ValueError):
            continue
        if path.name == 'optimization-plan.json':
            continue
        findings += data.get('findings', [])
        sources.append(str(path))
    for path in sorted((root / '.work/audit').glob('*/report.json')) if (root / '.work/audit').is_dir() else []:
        try:
            data = json.loads(path.read_text())
        except (OSError, ValueError):
            continue
        findings += data.get('findings', [])
        sources.append(str(path))
    seen, unique = set(), []
    for item in findings:
        item = quality.with_schema(item)
        if item['fingerprint'] not in seen:
            seen.add(item['fingerprint'])
            unique.append(item)
    return unique, sources


def _duplicate_operations(root, settings):
    """Same-file exact-body duplicate tests: the only deterministic APPLY operation."""
    operations = []
    files, _ = test_intelligence._all_files(root)
    for language, paths in files.items():
        for path in paths:
            if not _is_test(settings, path):
                continue
            text = (root / path).read_text(errors='replace')
            items, _ = test_intelligence._parse_blocks(language, path, text)
            first = {}
            for block in items:
                key = (language, block['digest'])
                if key in first and len(block['body'].strip()) > 40:
                    original = first[key]
                    start, end = block['start'], block['end']
                    while end < len(text) and text[end] in '; \t':
                        end += 1
                    line_start = text.rfind('\n', 0, start) + 1
                    if not text[line_start:start].strip():
                        start = line_start
                    if end < len(text) and text[end] == '\n':
                        end += 1
                    operations.append({
                        'kind': 'remove_test_block', 'path': path,
                        'test': block['name'], 'duplicate_of': original['name'],
                        'start': start, 'end': end,
                        'block_sha256': hashlib.sha256(text[start:end].encode()).hexdigest(),
                        'expected_file_sha256': hashlib.sha256(text.encode()).hexdigest(),
                        'source': {'same_file': True, 'language': language, 'body_identical': True},
                    })
                else:
                    first.setdefault(key, block)
    return operations


def _is_test(settings, path):
    import audit
    return audit.is_test(settings, path)


def _rec(root, finding, bridge_info, operations):
    categories = finding.get('categories') or []
    category = finding.get('category', '')
    domain = 'test' if ('tests' in categories or 'test-quality' in categories or category in ('tests', 'test-quality')) else 'production'
    bridge_names = bridge_info.get('names', [])
    symbol_names = (finding.get('symbols') or {}).get('affected', [])
    reason = finding.get('message', '')
    action, risk, blocking, operation = 'INVESTIGATE', 'medium', [], None
    if any(name in bridge_names for name in symbol_names):
        action = 'KEEP'
        blocking.append('Symbol is part of a discovered cross-language bridge')
        reason += ' (bridge-reachable; not a removal candidate)'
    elif 'dead-code' in categories or category == 'dead-code':
        if finding.get('confidence') == 'CONFIRMED':
            action = 'REMOVE'
            blocking.append('Unreachability is not established; production removal stays blocked')
        else:
            action = 'INVESTIGATE'
            blocking.append('Dead-code evidence is not CONFIRMED')
    elif 'simplifier' in categories:
        action = 'SIMPLIFY'
        blocking.append('Behavioral equivalence requires review')
    elif 'optimization' in categories or 'performance' in categories:
        action, risk = 'SIMPLIFY', 'low'
        blocking.append('No measured before/after performance evidence')
    elif 'security' in categories:
        action = 'INVESTIGATE'
        blocking.append('Security finding requires owner triage')
    if domain == 'test':
        if finding.get('code') in ('same_body', 'duplicate-test'):
            action = 'MERGE'
            for candidate in operations:
                if candidate['path'] == finding['path'] and candidate['test'] == ((finding.get('symbols') or {}).get('affected') or [''])[0]:
                    operation = candidate
                    break
            if not operation:
                blocking.append('Duplicate body is not byte-identical within the same file')
        elif finding.get('code') in ('assertion_free', 'weak_assertion', 'over_mocking'):
            action = 'REWRITE'
            blocking.append('Replacement assertions require authoring')
        elif finding.get('code') in ('no_direct_test', 'not_in_coverage', 'uncovered_lines', 'uncovered_branch'):
            action = 'ADD_COVERAGE'
        elif finding.get('code') in ('fragile_async', 'obsolete_candidate'):
            action = 'INVESTIGATE'
    digest = hashlib.sha256((finding['fingerprint'] + action).encode()).hexdigest()[:12]
    return {
        'id': 'OPT-' + digest, 'domain': domain, 'action': action,
        'target': {'files': [finding.get('path')], 'symbols': symbol_names},
        'reason': reason, 'evidence': [finding['id']], 'confidence': finding.get('confidence'),
        'risk': risk, 'expected_benefit': 'Reduce review surface / remove evidence-backed duplication' if action in ('REMOVE', 'MERGE', 'SIMPLIFY') else 'Clarify the required verification',
        'affected_behavior': 'Tests and observable app behavior must remain equivalent' if domain == 'test' else 'Public behavior, ABI, threading, lifecycle and fallbacks must remain equivalent',
        'dependencies': [], 'cross_language_impact': 'BRIDGE_REACHABLE' if any(name in bridge_names for name in symbol_names) else 'NONE_DETECTED',
        'required_tests': ['Run the affected test file', 'Run the full suite before delivery'],
        'verification_plan': ['Run the supplied --verify-command(s)', 'Compare before/after quality reports'],
        'rollback_strategy': 'Restore the backup copied under .work/quality/backup/<run>/ on any failed verification',
        'blocking_conditions': blocking,
        'operation': operation if operation and not blocking else None,
    }


def plan(root, findings=None, sources=None, bridge_info=None):
    root = Path(root).resolve()
    loaded, loaded_sources = _load_reports(root)
    findings = findings if findings is not None else loaded
    sources = sources if sources is not None else loaded_sources
    bridge_info = bridge_info or bridge.bridge_index(root)
    settings = test_intelligence.config(root)
    operations = _duplicate_operations(root, settings)
    items = [_rec(root, item, bridge_info, operations) for item in findings]
    counts = {}
    for item in items:
        counts[item['domain'] + ':' + item['action']] = counts.get(item['domain'] + ':' + item['action'], 0) + 1
    head = _head(root)
    identity = hashlib.sha256(json.dumps([(i['id'], i['target'], i['action']) for i in items], sort_keys=True).encode()).hexdigest()[:16]
    payload = {'title': 'Safe optimization plan', 'mode': 'PLAN', 'status': 'FINDINGS' if items else 'PASS',
               'plan_id': 'OPLAN-' + identity, 'head': head,
               'at': datetime.now(timezone.utc).isoformat(),
               'scope': {'sources': sources, 'bridge_contracts': bridge_info.get('contracts', 0)},
               'items': items, 'summary': counts,
               'tools': {'evidence_sources': quality.execution(quality.STATUS_PASS, count=len(sources))},
               'limitations': LIMITS}
    json_path, md_path = quality.write_report(root, 'optimization-plan', payload,
                                              markdown_text=_plan_markdown(payload))
    payload['report'], payload['report_markdown'] = str(json_path), str(md_path)
    return payload


def _head(root):
    import subprocess
    try:
        return subprocess.run(['git', '-C', str(root), 'rev-parse', 'HEAD'], capture_output=True,
                              text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def _plan_markdown(payload):
    lines = ['# Safe optimization plan', '', f'Plan: `{payload["plan_id"]}`  ', f'Head: `{payload["head"]}`', '',
             f'Items: {len(payload["items"])}', '', '| ID | Domain | Action | Target | Risk | Executable |', '|---|---|---|---|---|---|']
    for item in payload['items']:
        lines.append(f'| {item["id"]} | {item["domain"]} | {item["action"]} | `{", ".join(item["target"]["files"])}` '
                     f'| {item["risk"]} | {"yes" if item["operation"] else "no"} |')
    lines += ['', '## Blocking conditions', '']
    for item in payload['items']:
        if item['blocking_conditions']:
            lines.append(f'- {item["id"]}: ' + '; '.join(item['blocking_conditions']))
    lines += ['', '## Limits', ''] + [f'- {note}' for note in LIMITS]
    return '\n'.join(lines)


def _gate(root, item):
    reasons = []
    if not item.get('operation'):
        reasons.append('No deterministic operation is attached to this item')
    if item['domain'] == 'production' and item['action'] in ('REMOVE', 'REFACTOR', 'MERGE'):
        reasons.append('Production removal/restructuring requires separate unreachability evidence')
    if item.get('cross_language_impact') == 'BRIDGE_REACHABLE':
        reasons.append('Target is bridge-reachable')
    operation = item.get('operation') or {}
    path = operation.get('path')
    if path:
        target = (root / path).resolve()
        if not target.is_relative_to(root.resolve()):
            reasons.append('Target escapes the repository')
        if path.startswith(('third_party/', 'rust_backend/vendor/', 'rust_backend/target/')) or \
                Path(path).name.endswith(('.g.dart', '.freezed.dart')):
            reasons.append('Target is protected/generated code')
        if not target.is_file():
            reasons.append('Target file is missing')
    return reasons


def apply(root, plan_path, approve, verify_commands=(), dry_run=False, allow_head_change=False, batch_size=10):
    root = Path(root).resolve()
    data = json.loads(Path(plan_path).read_text())
    approve = list(approve or [])
    if not approve:
        raise ValueError('APPLY requires explicit --approve with one or more plan item IDs')
    items = {item['id']: item for item in data.get('items', [])}
    unknown = [value for value in approve if value not in items]
    if unknown:
        raise ValueError(f'Unknown plan items: {unknown}')
    if data.get('head') and _head(root) != data['head'] and not allow_head_change:
        raise ValueError('Repository HEAD changed since the plan; regenerate the plan or pass --allow-head-change')
    approved = [items[value] for value in approve]
    if len(approved) > max(1, min(int(batch_size), 50)):
        raise ValueError(f'Approve at most {max(1, min(int(batch_size), 50))} items per APPLY batch')
    gate = {item['id']: _gate(root, item) for item in approved}
    blocked = {key: value for key, value in gate.items() if value}
    if blocked:
        return {'status': 'BLOCKED', 'reason': 'Safety gates failed', 'blocked': blocked,
                'plan_id': data.get('plan_id')}
    if dry_run:
        return {'status': 'DRY_RUN', 'plan_id': data.get('plan_id'),
                'would_apply': [{'id': item['id'], 'operation': item['operation']} for item in approved]}

    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = root / '.work/quality/backup' / run_id
    backup.mkdir(parents=True, exist_ok=True)
    originals = {}
    changed = []
    try:
        for item in approved[:max(1, min(int(batch_size), 50))]:
            operation = item['operation']
            path = root / operation['path']
            text = path.read_text()
            if hashlib.sha256(text.encode()).hexdigest() != operation['expected_file_sha256']:
                raise ValueError(f'{operation["path"]} changed since planning; refusing to edit')
            if operation['kind'] == 'remove_test_block':
                start, end = operation['start'], operation['end']
                block = text[start:end]
                if hashlib.sha256(block.encode()).hexdigest() != operation['block_sha256']:
                    raise ValueError(f'{operation["path"]} block does not match the plan; refusing to edit')
                originals.setdefault(path, text)
                (backup / operation['path']).parent.mkdir(parents=True, exist_ok=True)
                (backup / operation['path']).write_text(text)
                updated = text[:start] + text[end:]
                path.write_text(updated)
                changed.append({'id': item['id'], 'path': operation['path'], 'operation': operation['kind']})
            else:
                raise ValueError(f'Unsupported operation: {operation["kind"]}')
    except (OSError, ValueError) as error:
        for path, text in originals.items():
            path.write_text(text)
        return {'status': 'ROLLED_BACK', 'reason': str(error), 'backup': str(backup), 'changed': []}

    checks = []
    for index, command in enumerate(verify_commands or []):
        result = quality.run(command, root, 900, root / f'.work/quality/logs/apply-verify-{index}.log')
        checks.append({'command': command, 'status': result['status'], 'exit_code': result.get('exit_code')})
        if result['status'] != quality.STATUS_PASS:
            for path, text in originals.items():
                path.write_text(text)
            return {'status': 'ROLLED_BACK', 'reason': 'Verification failed', 'checks': checks,
                    'backup': str(backup), 'changed': []}
    status = 'APPLIED_NOT_VERIFIED' if not checks else 'APPLIED'
    return {'status': status, 'plan_id': data.get('plan_id'), 'run': run_id, 'backup': str(backup),
            'changed': changed, 'checks': checks,
            'verification': 'NOT_RUN' if not checks else 'RUN'}


def compare(root, before, after):
    root = Path(root).resolve()

    def load(path):
        data = json.loads(Path(path).read_text())
        return data

    old, new = load(before), load(after)
    delta = quality.compare(new.get('findings', []), old.get('findings', []))
    metrics = {}
    for key in ('tests', 'coverage', 'scorecard'):
        metrics[key] = {'before': old.get(key), 'after': new.get(key)}
    missing = [key for key, value in metrics.items() if value['before'] is None or value['after'] is None]
    return {'status': 'INCOMPLETE' if missing else 'COMPARED', 'findings': delta,
            'metrics': metrics, 'missing_evidence': missing,
            'note': 'Missing metrics remain UNKNOWN; absence of evidence is never improvement.'}
