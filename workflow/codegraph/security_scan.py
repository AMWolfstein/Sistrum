#!/usr/bin/env python3
"""Optional security analyzers. Detects tools first, never installs them, never
uploads source, and redacts secret values from every report."""
from __future__ import annotations

import json
import os
from pathlib import Path
import tempfile

import quality

BUNDLED_RULES = Path(__file__).with_name('security-rules.yml')
LIMITS = [
    'Security scanners use their own rule/advisory sets; absence of findings is not proof of safety.',
    'No source or secret is uploaded: Semgrep runs only a local config, gitleaks uses --redact and --no-git.',
    'cargo-audit needs an advisory database; an offline/failed lookup is INCOMPLETE, never clean.',
]


def parse_semgrep(text, root):
    try:
        data = json.loads(text or '{}')
    except ValueError:
        return None
    found = []
    for item in data.get('results', []) if isinstance(data, dict) else []:
        extra = item.get('extra') or {}
        path = item.get('path') or ''
        found.append(quality.finding(
            'semgrep', 'security', path, (item.get('start') or {}).get('line', 1),
            extra.get('message') or item.get('check_id', 'semgrep finding'),
            code=item.get('check_id', 'semgrep'), severity=str(extra.get('severity', 'warning')).lower(),
            confidence='PROBABLE', evidence='tool-diagnostic',
            recommendation='Inspect the rule match; a match alone does not prove exploitability.',
            limitations=['Semgrep rules cover only patterns in the selected local configuration.']))
    return found


def parse_gitleaks(text, root):
    try:
        data = json.loads(text or '[]')
    except ValueError:
        return None
    if not isinstance(data, list):
        return None
    found = []
    for item in data:
        # Never carry a secret value, even a redacted one, into reports.
        rule = item.get('RuleID') or item.get('ruleId') or 'secret'
        path = item.get('File') or item.get('file') or ''
        line = int(item.get('StartLine') or item.get('startLine') or 1)
        found.append(quality.finding(
            'gitleaks', 'security', path, max(line, 1),
            f'Potential secret detected by rule {rule}; value redacted.',
            code=rule, severity='high', confidence='PROBABLE', evidence='secret-scan',
            recommendation='Confirm whether the value is a real credential, rotate it, and remove it from history if needed.',
            limitations=['Pattern-based detection can match placeholders or test data.']))
    return found


def parse_cargo_audit(text, root):
    try:
        data = json.loads(text or '{}')
    except ValueError:
        return None
    if not isinstance(data, dict) or 'vulnerabilities' not in data:
        return None
    found = []
    for item in (data.get('vulnerabilities') or {}).get('list', []):
        advisory = item.get('advisory') or {}
        package = item.get('package') or {}
        severity = str(advisory.get('severity') or 'medium').lower()
        found.append(quality.finding(
            'cargo-audit', 'security', 'rust_backend/Cargo.lock', 1,
            f'{advisory.get("id", "advisory")}: {advisory.get("title", "vulnerable dependency")}',
            code=advisory.get('id', 'advisory'), severity=severity, confidence='CONFIRMED',
            evidence='advisory-database', symbols=[f'{package.get("name", "?")}@{package.get("version", "?")}'],
            recommendation='Upgrade or replace the affected dependency; confirm with the advisory database.',
            limitations=['Advisory databases can lag or miss unmaintained crates.']))
    return found


def semgrep(root, paths=(), timeout=900, config=None, baseline=None):
    executable = quality.tool_path(['semgrep'])
    if not executable:
        return [], quality.unavailable_tool('semgrep', 'semgrep is not installed; install it only if the owner approves')
    rules = Path(config or rules_path()).expanduser()
    if not rules.is_absolute():
        rules = Path(root) / rules
    if not rules.is_file():
        return [], quality.execution(quality.STATUS_UNAVAILABLE,
                                     reason=f'No local Semgrep config at {rules}; remote/auto registry config is not used',
                                     tool='semgrep')
    command = [executable, 'scan', '--json', '--quiet', '--metrics=off', '--config', str(rules)]
    command += [str(p) for p in paths] or ['.']
    done = quality.run(command, root, timeout, Path(root) / '.work/quality/logs/semgrep.log')
    if done['status'] == quality.STATUS_UNAVAILABLE:
        return [], done
    found = parse_semgrep(done.get('stdout', ''), root)
    if found is None:
        return [], quality.execution(quality.STATUS_INCOMPLETE, reason='Semgrep produced malformed/empty JSON',
                                     tool='semgrep', command=command)
    status = quality.execution(quality.STATUS_PASS if done['status'] == quality.STATUS_PASS else quality.STATUS_INCOMPLETE,
                               reason=None if done['status'] == quality.STATUS_PASS else f'Semgrep exited {done.get("exit_code")}',
                               tool='semgrep', command=command, exit_code=done.get('exit_code'),
                               version=quality.tool_version(executable), findings=len(found))
    return found, status


def _rules_path():
    """Bundled local rules; AUDIT_SEMGREP_CONFIG overrides."""
    return os.environ.get('AUDIT_SEMGREP_CONFIG') or str(BUNDLED_RULES)


def gitleaks(root, paths=(), timeout=600, baseline=None):
    executable = quality.tool_path(['gitleaks'])
    if not executable:
        return [], quality.unavailable_tool('gitleaks', 'gitleaks is not installed; install it only if the owner approves')
    with tempfile.TemporaryDirectory(prefix='gitleaks-') as temporary:
        report = Path(temporary) / 'report.json'
        command = [executable, 'detect', '--no-git', '--redact', '--exit-code', '0',
                   '--report-format', 'json', '--report-path', str(report), '--source', str(root)]
        done = quality.run(command, root, timeout, Path(root) / '.work/quality/logs/gitleaks.log')
        if done['status'] == quality.STATUS_UNAVAILABLE:
            return [], done
        text = report.read_text() if report.is_file() else done.get('stdout', '')
        found = parse_gitleaks(text, root)
        if found is None:
            return [], quality.execution(quality.STATUS_INCOMPLETE, reason='Gitleaks produced malformed JSON',
                                         tool='gitleaks', command=command)
        return found, quality.execution(quality.STATUS_PASS, tool='gitleaks', command=command,
                                        version=quality.tool_version(executable), findings=len(found),
                                        limitations=['Scans the working tree only (--no-git); history is not inspected.'])


def cargo_audit(root, paths=(), timeout=900, baseline=None):
    executable = quality.tool_path(['cargo-audit'])
    if not executable:
        return [], quality.unavailable_tool('cargo-audit', 'cargo-audit is not installed; `cargo audit` is not assumed')
    command = [executable, 'audit', '--json']
    cwd = root / 'rust_backend' if (root / 'rust_backend/Cargo.toml').is_file() else root
    done = quality.run(command, cwd, timeout, root / '.work/quality/logs/cargo-audit.log', ok_codes=(0, 1))
    if done['status'] == quality.STATUS_UNAVAILABLE:
        return [], done
    found = parse_cargo_audit(done.get('stdout', ''), root)
    if found is None:
        reason = 'cargo-audit unavailable or advisory database unreachable'
        return [], quality.execution(quality.STATUS_INCOMPLETE, reason=f'{reason}: {done.get("stderr", "")[-300:]}',
                                     tool='cargo-audit', command=command)
    return found, quality.execution(quality.STATUS_PASS, tool='cargo-audit', command=command,
                                    exit_code=done.get('exit_code'), version=quality.tool_version(executable),
                                    findings=len(found))


def security_scan(root, tools=(), paths=(), timeout=900, baseline=None):
    root = Path(root).resolve()
    selected = list(tools) or ['semgrep', 'gitleaks', 'cargo-audit']
    runners = {'semgrep': semgrep, 'gitleaks': gitleaks, 'cargo-audit': cargo_audit}
    unknown = set(selected) - set(runners)
    if unknown:
        raise ValueError(f'Unknown security tools: {sorted(unknown)}')
    findings, statuses = [], {}
    for name in selected:
        found, status = runners[name](root, paths=paths, timeout=timeout, baseline=baseline)
        findings.extend(found)
        statuses[name] = status
    result = quality.result_status(statuses, findings)
    payload = {'title': 'Security audit', 'status': result, 'kind': 'security',
               'scope': {'paths': list(paths) or ['.'], 'tools': selected},
               'tools': statuses, 'findings': findings,
               'summary': {'findings': len(findings), 'tools_selected': len(selected),
                           'tools_unavailable': sum(t['status'] == quality.STATUS_UNAVAILABLE for t in statuses.values())},
               'limitations': LIMITS}
    json_path, md_path = quality.write_report(root, 'security', payload)
    payload['report'] = str(json_path)
    payload['report_markdown'] = str(md_path)
    return payload
