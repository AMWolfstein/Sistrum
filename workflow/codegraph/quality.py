#!/usr/bin/env python3
"""Shared quality infrastructure: versioned findings, execution status, bounded
subprocesses, input-keyed caches and JSON/Markdown reports.

Audit, Test Intelligence, Bridge Verification and Safe Optimization all emit the
same evidence shape from here. Nothing in this module edits source files.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from datetime import datetime, timezone

SCHEMA = '1.0.0'

STATUS_PASS = 'PASS'
STATUS_FINDINGS = 'FINDINGS'
STATUS_UNAVAILABLE = 'UNAVAILABLE'
STATUS_INCOMPLETE = 'INCOMPLETE'
STATUS_FAILED = 'FAILED'
STATUS_SKIPPED = 'SKIPPED'
STATUSES = (STATUS_PASS, STATUS_FINDINGS, STATUS_UNAVAILABLE, STATUS_INCOMPLETE, STATUS_FAILED, STATUS_SKIPPED)
SEVERITIES = ('critical', 'high', 'medium', 'low', 'info')
CONFIDENCES = ('CONFIRMED', 'PROBABLE', 'UNCERTAIN', 'REVIEW_REQUIRED')

_PRIORITY = {STATUS_PASS: 0, STATUS_FINDINGS: 1, STATUS_SKIPPED: 2, STATUS_UNAVAILABLE: 3,
             STATUS_INCOMPLETE: 4, STATUS_FAILED: 5}


def now():
    return datetime.now(timezone.utc).isoformat()


def normalize(message):
    """Whitespace/quote-insensitive text so moved code keeps a stable fingerprint."""
    text = re.sub(r'\s+', ' ', str(message or '')).strip().lower()
    return re.sub(r'[`"\'“”‘’]', '', text)


def fingerprint(analyzer, category, path, code='', symbol='', message=''):
    payload = '|'.join((analyzer, category, str(path).replace('\\', '/'), str(code), str(symbol), normalize(message)))
    return hashlib.sha256(payload.encode('utf-8')).hexdigest()[:32]


def finding(analyzer, category, path, line, message, *, code='', severity='warning',
            confidence='REVIEW_REQUIRED', evidence='tool-diagnostic', symbols=None,
            related_symbols=None, recommendation='', limitations=None, unresolved=None,
            environment=None, verification='UNVERIFIED', baseline=None, reproduction=None,
            analyzer_version=None):
    """One canonical finding. Top-level path/line/code/categories keep old reports readable."""
    line = max(int(line or 1), 1)
    affected = sorted(set(symbols or []))
    digest = fingerprint(analyzer, category, path, code, ','.join(affected), message)
    return {
        'schema': SCHEMA,
        'id': 'F-' + digest[:12],
        'fingerprint': digest,
        'category': category,
        'categories': [category],
        'severity': severity,
        'confidence': confidence,
        'path': str(path).replace('\\', '/'),
        'line': line,
        'column': None,
        'code': code,
        'message': message,
        'symbols': {'affected': affected, 'related': sorted(set(related_symbols or []))},
        'analyzer': {'name': analyzer, 'version': analyzer_version},
        'evidence': [{'kind': evidence, 'detail': message}],
        'reproduction': reproduction or {'command': None, 'note': 'Re-run the producing command in the recorded scope'},
        'environment': environment or {},
        'recommendation': recommendation,
        'verification': {'status': verification, 'baseline': baseline or 'NOT_COMPARED'},
        'limitations': list(limitations or []),
        'unresolved_dependencies': list(unresolved or []),
    }


def with_schema(item):
    """Add unified fields to an existing audit/test finding without losing its fields."""
    if item.get('schema') == SCHEMA and item.get('fingerprint'):
        return item
    copy = dict(item)
    symbol = ','.join((copy.get('symbols') or {}).get('affected', []) if isinstance(copy.get('symbols'), dict) else [])
    digest = fingerprint(copy.get('analyzer', {}).get('name', 'audit') if isinstance(copy.get('analyzer'), dict) else 'audit',
                         (copy.get('categories') or [copy.get('category', 'code')])[0],
                         copy.get('path', ''), copy.get('code', ''), symbol, copy.get('message', ''))
    copy.update(schema=SCHEMA, id='F-' + digest[:12], fingerprint=digest,
                category=copy.get('category') or (copy.get('categories') or ['code'])[0],
                severity=copy.get('severity') or 'warning',
                confidence=copy.get('confidence', 'REVIEW_REQUIRED'),
                symbols=copy.get('symbols') or {'affected': [], 'related': []},
                analyzer=copy.get('analyzer') or {'name': 'audit', 'version': None},
                evidence=copy.get('evidence') if isinstance(copy.get('evidence'), list) else [{'kind': 'tool-diagnostic', 'detail': copy.get('message', '')}],
                environment=copy.get('environment') or {},
                recommendation=copy.get('recommendation', ''),
                verification=copy.get('verification') or {'status': 'UNVERIFIED', 'baseline': 'NOT_COMPARED'},
                limitations=copy.get('limitations') or [],
                unresolved_dependencies=copy.get('unresolved_dependencies') or [])
    return copy


def execution(status, *, reason=None, command=None, exit_code=None, log=None, **extra):
    if status not in STATUSES:
        raise ValueError(f'unknown execution status: {status}')
    value = {'status': status}
    if reason:
        value['reason'] = str(reason)
    if command is not None:
        value['command'] = list(command)
    if exit_code is not None:
        value['exit_code'] = exit_code
    if log:
        value['log'] = str(log)
    value.update(extra)
    return value


def combine_statuses(statuses):
    """Worst status wins: a missing or failed analyzer never becomes a clean run."""
    worst = STATUS_PASS
    for status in statuses:
        if status not in _PRIORITY:
            raise ValueError(f'unknown execution status: {status}')
        if _PRIORITY[status] > _PRIORITY[worst]:
            worst = status
    return worst


def result_status(tools, findings):
    """Aggregate analyzer executions and findings into one report status."""
    worst = combine_statuses([tool.get('status', STATUS_PASS) for tool in tools.values()])
    if findings:
        if worst == STATUS_FAILED:
            return STATUS_FAILED
        if worst in (STATUS_INCOMPLETE, STATUS_UNAVAILABLE, STATUS_SKIPPED):
            return STATUS_INCOMPLETE
        return STATUS_FINDINGS
    return worst


def unavailable_tool(name, reason, **extra):
    return execution(STATUS_UNAVAILABLE, reason=reason, tool=name, **extra)


def tool_path(names, env=None):
    """First executable found on PATH (or an explicit override), without installing anything."""
    env = env or os.environ
    for name in names:
        configured = env.get(f'AUDIT_{name.upper().replace("-", "_")}')
        if configured:
            found = shutil.which(configured) or (configured if os.access(configured, os.X_OK) else None)
            if found:
                return found
        found = shutil.which(name)
        if found:
            return found
    return None


def tool_version(executable, timeout=20):
    if not executable:
        return None
    try:
        done = subprocess.run([executable, '--version'], capture_output=True, text=True, errors='replace',
                              stdin=subprocess.DEVNULL, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return None
    text = (done.stdout or done.stderr or '').strip().splitlines()
    return text[0][:200] if text else None


def run(argv, cwd, timeout, log=None, env=None, ok_codes=(0,)):
    """Bounded subprocess. Missing tools are UNAVAILABLE, timeouts/nonzero are FAILED."""
    argv = [str(arg) for arg in argv]
    started = time.monotonic()
    if log:
        Path(log).parent.mkdir(parents=True, exist_ok=True)
    try:
        done = subprocess.run(argv, cwd=cwd, capture_output=True, text=True, errors='replace',
                              stdin=subprocess.DEVNULL, timeout=timeout, env=env)
    except FileNotFoundError as error:
        return {'status': STATUS_UNAVAILABLE, 'reason': f'executable not found: {error}', 'command': argv}
    except subprocess.TimeoutExpired:
        if log:
            Path(log).write_text(f'$ {" ".join(argv)}\nTIMEOUT after {timeout}s\n')
        return {'status': STATUS_FAILED, 'reason': f'timeout after {timeout}s', 'command': argv}
    except OSError as error:
        return {'status': STATUS_FAILED, 'reason': str(error), 'command': argv}
    duration = round(time.monotonic() - started, 3)
    if log:
        Path(log).write_text(f'$ {" ".join(argv)}\nexit={done.returncode} duration={duration}s\n\n'
                             f'{done.stdout}\n--- stderr ---\n{done.stderr}')
    status = STATUS_PASS if done.returncode in ok_codes else STATUS_FAILED
    return {'status': status, 'command': argv, 'exit_code': done.returncode, 'stdout': done.stdout,
            'stderr': done.stderr, 'duration_s': duration, 'log': str(log) if log else None}


def digest_files(root, paths, extra=None):
    """Deterministic input digest for cache keys (content, not mtime)."""
    root = Path(root)
    digest = hashlib.sha256()
    for path in sorted(str(p).replace('\\', '/') for p in paths):
        target = root / path
        digest.update(path.encode())
        digest.update(b'\0')
        try:
            digest.update(target.read_bytes())
        except OSError:
            digest.update(b'<missing>')
        digest.update(b'\0')
    if extra is not None:
        digest.update(json.dumps(extra, sort_keys=True, default=str).encode())
    return digest.hexdigest()


class Cache:
    """Namespace cache keyed by full input identity; mismatched keys are discarded."""

    def __init__(self, root, namespace):
        if not re.fullmatch(r'[A-Za-z0-9_.-]+', namespace):
            raise ValueError('unsafe cache namespace')
        self.root = Path(root)
        self.namespace = namespace
        self.directory = self.root / '.work/quality/cache' / namespace

    def key(self, payload):
        return hashlib.sha256(json.dumps(payload, sort_keys=True, default=str).encode()).hexdigest()

    def load(self, key):
        target = self.directory / f'{key}.json'
        if not target.is_file():
            return None
        try:
            stored = json.loads(target.read_text())
        except (OSError, ValueError):
            return None
        if stored.get('schema') != SCHEMA or stored.get('key') != key:
            return None
        return stored.get('data')

    def store(self, key, data):
        self.directory.mkdir(parents=True, exist_ok=True)
        target = self.directory / f'{key}.json'
        with tempfile.NamedTemporaryFile(mode='w', dir=self.directory, delete=False) as output:
            json.dump({'schema': SCHEMA, 'key': key, 'saved_at': now(), 'data': data}, output, indent=2)
            temporary = Path(output.name)
        temporary.replace(target)
        return target

    def clear(self):
        removed = 0
        if self.directory.is_dir():
            for target in self.directory.iterdir():
                if target.is_file():
                    target.unlink()
                    removed += 1
        return removed

    def status(self):
        files = sorted(self.directory.glob('*.json')) if self.directory.is_dir() else []
        return {'namespace': self.namespace, 'directory': str(self.directory), 'entries': len(files)}


def compare(findings, baseline):
    """Fingerprint comparison. A missing baseline is explicit, never an improvement."""
    if baseline is None:
        return {'status': 'NOT_COMPARED', 'new': len(findings), 'fixed': 0, 'unchanged': 0}
    old = {item.get('fingerprint') for item in baseline if item.get('fingerprint')}
    current = {item.get('fingerprint') for item in findings if item.get('fingerprint')}
    return {'status': 'COMPARED', 'new': len(current - old), 'fixed': len(old - current),
            'unchanged': len(current & old)}


def load_baseline(path):
    if not path:
        return None
    data = json.loads(Path(path).read_text())
    if isinstance(data, list):
        return data
    return data.get('findings', [])


def markdown(payload):
    """Compact human report from the canonical payload."""
    lines = [f'# {payload.get("title", "Quality report")}', '',
             f'Status: **{payload.get("status", "UNKNOWN")}**', '',
             f'Generated: {payload.get("at")}', '']
    if payload.get('scope'):
        lines += ['## Scope', '']
        for key, value in payload['scope'].items():
            lines.append(f'- {key}: `{value}`')
        lines.append('')
    tools = payload.get('tools') or {}
    if tools:
        lines += ['## Analyzer execution', '', '| Analyzer | Status | Reason / command |', '|---|---|---|']
        for name, tool in tools.items():
            detail = tool.get('reason') or (' '.join(tool.get('command', [])) if tool.get('command') else '')
            lines.append(f'| {name} | {tool.get("status", "UNKNOWN")} | {str(detail).replace("|", "/")[:180]} |')
        lines.append('')
    findings = payload.get('findings') or []
    lines += [f'## Findings ({len(findings)})', '']
    if findings:
        lines += ['| ID | Category | Severity | Confidence | Location | Summary |', '|---|---|---|---|---|---|']
        for item in findings[:200]:
            lines.append(f'| {item.get("id", "")} | {item.get("category", "")} | {item.get("severity", "")} '
                         f'| {item.get("confidence", "")} | `{item.get("path", "")}:{item.get("line", 1)}` '
                         f'| {str(item.get("message", "")).replace("|", "/")[:160]} |')
        if len(findings) > 200:
            lines.append(f'| ... | | | | | {len(findings) - 200} more in JSON |')
    else:
        lines.append('None reported in this scope.')
    lines.append('')
    if payload.get('summary'):
        lines += ['## Summary', '']
        lines += [f'- {key}: {value}' for key, value in payload['summary'].items()]
        lines.append('')
    limitations = payload.get('limitations') or []
    if limitations:
        lines += ['## Limits', ''] + [f'- {note}' for note in limitations] + ['']
    return '\n'.join(lines)


def write_report(root, name, payload, *, markdown_text=None):
    """Write `.work/quality/<name>.json` and `.md` atomically; return both paths."""
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', name):
        raise ValueError('unsafe report name')
    directory = Path(root) / '.work/quality'
    directory.mkdir(parents=True, exist_ok=True)
    json_path, md_path = directory / f'{name}.json', directory / f'{name}.md'
    body = {'schema': SCHEMA, 'at': now(), **payload}
    for target, text in ((json_path, json.dumps(body, indent=2, default=str) + '\n'),
                         (md_path, (markdown_text or markdown(body)) + '\n')):
        with tempfile.NamedTemporaryFile(mode='w', dir=directory, delete=False) as output:
            output.write(text)
            temporary = Path(output.name)
        temporary.replace(target)
    return json_path, md_path
