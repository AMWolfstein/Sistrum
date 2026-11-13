"""Conventional Commit subject validation for every agent-created commit.

The subject is `<type>(<scope>): <summary>`, with a lowercase type and scope, an
imperative summary, no trailing period and an optional task-ID suffix. Merge,
fixup/squash and Git-generated revert subjects are left alone.
"""
from __future__ import annotations

from pathlib import Path
import re
import subprocess

TYPES = ('feat', 'fix', 'refactor', 'perf', 'test', 'docs', 'chore', 'build', 'ci', 'style', 'revert')
SCOPE = re.compile(r'^[a-z0-9][a-z0-9._/-]*$')
SUBJECT = re.compile(r'^(?P<type>[A-Za-z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?: (?P<summary>.+)$')
TASK_SUFFIX = re.compile(r'\s+\((?:M\d{3}-(?:T|TOR)\d{3}|TOR\d{3})\)$')
SKIPPED = ('Merge ', 'Revert "', 'fixup! ', 'squash! ')
MAX_SUBJECT = 72


def check_subject(line):
    """Return a list of problems for one commit subject (empty means valid)."""
    subject = (line or '').strip()
    if not subject:
        return ['subject is empty']
    if subject.startswith(SKIPPED):
        return []
    problems = []
    if len(subject) > MAX_SUBJECT:
        problems.append(f'subject is {len(subject)} characters; keep the first line at most {MAX_SUBJECT}')
    match = SUBJECT.match(subject)
    if not match:
        return problems + ['expected `<type>(<scope>): <summary>` (scope optional), for example `feat(workflow): ...`']
    kind = match.group('type')
    scope = match.group('scope')
    if kind not in TYPES:
        problems.append(f'unknown type `{kind}`; use one of {", ".join(TYPES)}')
    if scope is not None:
        if not scope:
            problems.append('scope is empty; remove the parentheses or name an area')
        elif not SCOPE.match(scope):
            problems.append(f'scope `{scope}` must be lowercase letters/digits/dots/slashes')
    summary = TASK_SUFFIX.sub('', match.group('summary')).strip()
    if not summary:
        problems.append('summary is empty')
    elif summary.endswith('.'):
        problems.append('summary must not end with a period')
    elif re.search(r'\s{2,}', summary):
        problems.append('summary contains repeated spaces')
    return problems


def subjects_from_range(root, revision):
    """Read commit subjects from a Git revision/range without failing on empty output."""
    done = subprocess.run(['git', '-C', str(root), 'log', '--format=%H%x1f%s', revision, '--'],
                          capture_output=True, text=True, check=True)
    result = []
    for raw in done.stdout.splitlines():
        if not raw.strip():
            continue
        sha, _, subject = raw.partition('\x1f')
        result.append((sha.strip(), subject.strip()))
    return result


def check_range(root, revision):
    """Validate every commit subject in `revision`; skip merge/fixup subjects."""
    checked, violations = 0, []
    for sha, subject in subjects_from_range(root, revision):
        problems = check_subject(subject)
        checked += 1
        if problems:
            violations.append({'commit': sha, 'subject': subject, 'problems': problems})
    return {'status': 'FAIL' if violations else 'PASS', 'checked': checked,
            'violations': violations, 'range': revision,
            'convention': '<type>(<scope>): <summary>; types: ' + ', '.join(TYPES)}


def check(root, *, message=None, message_file=None, revision=None):
    """Validate a literal message, a commit-msg file, or a Git revision/range."""
    if message is not None or message_file is not None:
        text = message if message is not None else Path(message_file).read_text(errors='replace')
        subject = text.splitlines()[0] if text.splitlines() else ''
        problems = check_subject(subject)
        return {'status': 'FAIL' if problems else 'PASS', 'checked': 1,
                'violations': [{'subject': subject, 'problems': problems}] if problems else [],
                'convention': '<type>(<scope>): <summary>; types: ' + ', '.join(TYPES)}
    return check_range(root, revision or 'HEAD')


HOOK = """#!/bin/sh
# Installed by `work.py commit-check --install-hook`.
root=$(git rev-parse --show-toplevel)
script="$root/workflow/work.py"
[ -f "$script" ] || script="{fallback}"
exec python3 "$script" --root "$root" commit-check --message-file "$1"
"""


def install_hook(root):
    """Install a local commit-msg hook so a non-conforming subject cannot be committed."""
    target = Path(subprocess.run(['git', '-C', str(root), 'rev-parse', '--git-path', 'hooks/commit-msg'],
                                 capture_output=True, text=True, check=True).stdout.strip())
    if not target.is_absolute():
        target = Path(root) / target
    existing = target.read_text(errors='replace') if target.is_file() else ''
    if existing and 'commit-check --message-file' not in existing:
        return {'status': 'FAIL', 'reason': f'{target} already exists and is not managed by this workflow',
                'path': str(target)}
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(HOOK.format(fallback=Path(__file__).with_name('work.py').resolve()))
    target.chmod(0o755)
    return {'status': 'PASS', 'installed': str(target),
            'convention': '<type>(<scope>): <summary>'}
