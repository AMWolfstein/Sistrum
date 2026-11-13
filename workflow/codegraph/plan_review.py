#!/usr/bin/env python3
"""Deterministic planning review for the existing speckit-analyze stage.

Adds architecture, cross-language and test-strategy checks that use repository
evidence. It creates no new stage, gate or approval loop.
"""
from __future__ import annotations

import json
from pathlib import Path
import re

import quality

BRIDGE_WORDS = re.compile(r'\b(jni|ffi|uniffi|methodchannel|eventchannel|pigeon|native code|native layer|cross-language|bridge)\b', re.I)
TEST_WORDS = re.compile(r'\b(test|spec|coverage|regression)\b', re.I)
RISK_WORDS = {
    'error': r'\b(error|exception|failure|fallback)\b',
    'cancellation': r'\b(cancel|abort|dispose|close)\b',
    'cross_language': r'\b(jni|ffi|uniffi|methodchannel|eventchannel|pigeon|bridge)\b',
    'performance': r'\b(performance|latency|allocation|memory|fps)\b',
    'migration': r'\b(migrat|schema|database|storage|upgrade)\b',
}
PATH = re.compile(r'`([\w./-]+\.(?:dart|kt|java|rs|c|cpp|h|hpp|py|json|yml|yaml|gradle|kts|toml))`|(?<![\w`])((?:lib|test|integration_test|android|rust_backend|workflow|scripts)/[\w./-]+)')


def load(feature):
    feature = Path(feature)
    result = {}
    for name in ('spec.md', 'plan.md', 'tasks.md'):
        path = feature / name
        result[name] = path.read_text(errors='replace') if path.is_file() else None
    return result


def _paths(text):
    found = set()
    for match in PATH.finditer(text or ''):
        value = (match.group(1) or match.group(2)).rstrip('.,;)')
        found.add(value)
    return sorted(found)


def _plan_symbols(root):
    map_path = Path(root) / '.work/codegraph/map.json'
    if not map_path.is_file():
        return set()
    try:
        data = json.loads(map_path.read_text())
    except ValueError:
        return set()
    return {node.get('label') for node in data.get('nodes', []) if node.get('label')}


def _load_json(path):
    if not path or not Path(path).is_file():
        return None
    try:
        return json.loads(Path(path).read_text())
    except ValueError:
        return None


def review(root, feature, bridge_report=None, audit_report=None):
    root = Path(root).resolve()
    docs = load(feature)
    text = '\n'.join(value for value in docs.values() if value)
    findings = []

    def add(check, severity, category, message, recommendation, location='plan.md'):
        findings.append({'check': check, 'severity': severity, 'category': category,
                         'location': location, 'message': message, 'recommendation': recommendation,
                         'evidence': 'repository'})

    missing = [name for name, value in docs.items() if value is None]
    if missing:
        add('artifacts', 'CRITICAL', 'completeness', f'Missing planning artifacts: {missing}',
            'Produce the missing artifact before implementation.', missing[0])

    # Architecture: validate referenced paths and reuse of existing symbols.
    symbols = _plan_symbols(root)
    planned_paths = _paths(text)
    for path in planned_paths:
        target = root / path
        if not target.exists() and not re.search(r'\b(new|create|add|introduce)\b[^.\n]{0,80}' + re.escape(Path(path).name), text, re.I):
            add('architecture-path', 'LOW', 'architecture',
                f'Referenced path `{path}` does not exist and is not explicitly marked new',
                'State whether this is a new file or correct the path.', path)
    if symbols:
        for name in symbols:
            if len(name) < 5 or name not in text:
                continue
            if re.search(r'\b(?:create|add|introduce|implement)\b[^.\n]{0,60}\b' + re.escape(name) + r'\b', text, re.I):
                add('architecture-duplicate', 'MEDIUM', 'architecture',
                    f'Plan appears to add `{name}` although the CodeGraph snapshot already defines it',
                    'Reuse or extend the existing implementation, or justify the duplicate.', 'plan.md')
    if re.search(r'\b(native|android|kotlin|rust|jni|ffi)\b', text, re.I) and not re.search(r'\brollback\b', text, re.I):
        add('architecture-rollback', 'MEDIUM', 'architecture',
            'Cross-platform/native plan has no stated rollback strategy',
            'Add the rollback/migration path for the affected platform or artifact.', 'plan.md')

    # Cross-language impact uses the actual bridge report, never a full audit rerun.
    bridge_data = _load_json(bridge_report) or _load_json(root / '.work/quality/bridge-verification.json')
    touches_bridge = bool(BRIDGE_WORDS.search(text) or any(bridge_word in path.lower() for path in planned_paths
                                                          for bridge_word in ('jni', 'ffi', 'native', 'bridge')))
    if touches_bridge:
        if bridge_data is None:
            add('bridge-evidence', 'HIGH', 'cross-language',
                'Plan touches cross-language boundaries but no bridge verification report is available',
                'Run `code_tools.py bridge verify` (scoped) and cite its evidence; do not assume linkage.')
        else:
            failed = bridge_data.get('scorecard', {}).get('failed_contracts', {}).get('ids', [])
            unresolved = bridge_data.get('scorecard', {}).get('unresolved_contracts', {}).get('ids', [])
            if failed:
                add('bridge-failed', 'CRITICAL', 'cross-language',
                    f'Bridge verification has failed contracts: {failed[:5]}',
                    'Resolve failed contract checks before changing these boundaries.')
            if unresolved:
                add('bridge-unresolved', 'HIGH', 'cross-language',
                    f'{len(unresolved)} unresolved bridge contracts remain',
                    'Scope the plan to verified contracts or add runtime contract tests.')

    # Test strategy: require the behaviors implied by the plan itself.
    implementation = '\n'.join(value for key, value in docs.items() if key != 'spec.md' and value)
    for key, pattern in RISK_WORDS.items():
        if re.search(pattern, implementation, re.I):
            has_test = re.search(r'\b' + key.replace('_', r'[\s-]?') + r'\b', implementation, re.I) and TEST_WORDS.search(implementation)
            if not has_test:
                add('test-strategy', 'HIGH', 'test-strategy',
                    f'Plan mentions {key.replace("_", " ")} risk but tasks/plan do not name a corresponding test',
                    f'Add a focused test for {key.replace("_", " ")} behavior; avoid duplicate unrelated tests.')
    if not TEST_WORDS.search(implementation):
        add('test-missing', 'HIGH', 'test-strategy', 'Plan/tasks contain no test work',
            'Name the behavior, regression and expected assertions to protect.')

    # Existing unresolved audit evidence lowers confidence, but is not a new gate.
    audit_data = _load_json(audit_report)
    if audit_data and audit_data.get('status') in ('INCOMPLETE', 'FAILED'):
        add('audit-incomplete', 'MEDIUM', 'evidence',
            'Referenced audit report is incomplete; planning confidence must be reduced',
            'Disclose the missing analyzer/coverage scope in plan.md.')

    severities = {item['severity'] for item in findings}
    decision = 'BLOCKED' if 'CRITICAL' in severities else 'NEEDS_REVISION' if severities & {'HIGH', 'MEDIUM'} else 'PASS'
    result = {'schema': quality.SCHEMA, 'at': quality.now(), 'decision': decision,
              'feature': str(Path(feature)), 'findings': findings,
              'metrics': {'findings': len(findings), 'paths_checked': len(planned_paths),
                          'bridge_report': bool(bridge_data), 'map_symbols': len(symbols)},
              'limits': ['Text/path heuristics plus existing reports; they do not replace spec/plan reading.',
                         'No new approval stage or review loop is created; the planning agent revises the plan.',
                         'Bridge results are reused only when their snapshot still matches the planned files.']}
    return result


def write(root, payload):
    root = Path(root).resolve()
    json_path, md_path = quality.write_report(root, 'plan-review', payload,
                                              markdown_text=_markdown(payload))
    payload['report'], payload['report_markdown'] = str(json_path), str(md_path)
    return payload


def _markdown(payload):
    lines = ['# Planning review', '', f'Decision: **{payload["decision"]}**', '',
             f'Feature: `{payload["feature"]}`', '', '## Findings', '']
    if not payload['findings']:
        lines.append('None in the checked evidence.')
    for item in payload['findings']:
        lines.append(f'- [{item["severity"]}] {item["check"]} ({item["location"]}): {item["message"]} — {item["recommendation"]}')
    lines += ['', '## Limits', ''] + [f'- {note}' for note in payload['limits']]
    return '\n'.join(lines)
