import subprocess
import tempfile
from pathlib import Path
import unittest

import commits


class SubjectTests(unittest.TestCase):
    def test_valid_subjects_and_task_suffixes(self):
        for subject in ('feat(workflow): add unified audit and test intelligence',
                        'fix: close the playback race',
                        'docs(workflow)!: drop the legacy path',
                        'feat(android): ship an arm64-only app (M004-T007)',
                        'chore: tidy the registry (TOR001)',
                        'revert: feat(workflow): add a temporary probe'):
            self.assertEqual(commits.check_subject(subject), [], subject)

    def test_invalid_subjects_are_rejected(self):
        cases = {
            'Add sistrum workflow': 'expected',
            'Feat(workflow): uppercase type': 'unknown type',
            'feat(): empty scope': 'scope is empty',
            'feat(Workflow): uppercase scope': 'must be lowercase',
            'feat(workflow): ends with a period.': 'period',
            'feature(workflow): unknown type': 'unknown type',
            'feat(workflow):  repeated  spaces': 'repeated spaces',
            'feat(workflow): ' + 'x' * 80: 'at most 72',
        }
        for subject, expected in cases.items():
            problems = ' '.join(commits.check_subject(subject))
            self.assertIn(expected, problems, subject)

    def test_merge_fixup_and_git_revert_are_left_alone(self):
        for subject in ('Merge branch main into reboot',
                        'fixup! feat(workflow): add unified audit',
                        'squash! bad message',
                        'Revert "feat(workflow): add unified audit"'):
            self.assertEqual(commits.check_subject(subject), [])


class RangeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.write('a', 'one')
        self.commit('feat(workflow): first commit')

    def write(self, name, text):
        (self.root / name).write_text(text)
        subprocess.run(['git', '-C', str(self.root), 'add', name], check=True)

    def commit(self, message):
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.email=a@b', '-c', 'user.name=t',
                        'commit', '-qm', message], check=True)

    def test_range_accepts_conforming_history(self):
        self.write('b', 'two')
        self.commit('fix(workflow): second commit (M001-T001)')
        result = commits.check(self.root, revision='HEAD~1..HEAD')
        self.assertEqual((result['status'], result['checked']), ('PASS', 1))

    def test_range_reports_the_offending_commit(self):
        self.write('b', 'two')
        self.commit('Add another file')
        result = commits.check(self.root, revision='HEAD~1..HEAD')
        self.assertEqual(result['status'], 'FAIL')
        self.assertEqual(result['violations'][0]['subject'], 'Add another file')
        self.assertIn('expected', ' '.join(result['violations'][0]['problems']))

    def test_literal_message_and_message_file(self):
        self.assertEqual(commits.check(self.root, message='feat(workflow): ok')['status'], 'PASS')
        self.assertEqual(commits.check(self.root, message='nope')['status'], 'FAIL')
        target = self.root / 'COMMIT_EDITMSG'
        target.write_text('fix(workflow): from the hook\n\nBody text.\n')
        self.assertEqual(commits.check(self.root, message_file=target)['status'], 'PASS')
        target.write_text('nope\n')
        self.assertEqual(commits.check(self.root, message_file=target)['status'], 'FAIL')

    def test_hook_install_writes_the_managed_script(self):
        result = commits.install_hook(self.root)
        self.assertEqual(result['status'], 'PASS')
        hook = Path(result['installed'])
        self.assertTrue(hook.is_file())
        self.assertIn('commit-check --message-file', hook.read_text())
        self.assertEqual(commits.install_hook(self.root)['status'], 'PASS')

    def test_hook_blocks_a_non_conforming_commit(self):
        self.assertEqual(commits.install_hook(self.root)['status'], 'PASS')
        self.write('b', 'two')
        bad = subprocess.run(['git', '-C', str(self.root), '-c', 'user.email=a@b', '-c', 'user.name=t',
                              'commit', '-qm', 'bad message'], capture_output=True, text=True)
        self.assertNotEqual(bad.returncode, 0)
        good = subprocess.run(['git', '-C', str(self.root), '-c', 'user.email=a@b', '-c', 'user.name=t',
                               'commit', '-qm', 'feat(workflow): second commit'], capture_output=True, text=True)
        self.assertEqual(good.returncode, 0, good.stderr)

    def test_hook_install_refuses_an_unmanaged_existing_hook(self):
        target = self.root / '.git/hooks/commit-msg'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text('#!/bin/sh\nexit 0\n')
        self.assertEqual(commits.install_hook(self.root)['status'], 'FAIL')


if __name__ == '__main__':
    unittest.main()
