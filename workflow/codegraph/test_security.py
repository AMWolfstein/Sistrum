import json
from pathlib import Path
import tempfile
import unittest

import quality
import security_scan


SEMGREP = {'results': [{'check_id': 'x.eval', 'path': 'a.py', 'start': {'line': 4},
                        'extra': {'message': 'eval on input', 'severity': 'ERROR'}}]}
GITLEAKS = [{'RuleID': 'generic-api-key', 'File': 'a.py', 'StartLine': 2,
             'Secret': 'super-secret-value', 'Description': 'API key'}]
CARGO = {'vulnerabilities': {'list': [{'advisory': {'id': 'RUSTSEC-0000-0001', 'title': 'bad crate', 'severity': 'high'},
                                      'package': {'name': 'demo', 'version': '1.0.0'}}]}}


class SecurityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def test_semgrep_parser_builds_unified_findings(self):
        found = security_scan.parse_semgrep(json.dumps(SEMGREP), self.root)
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0]['category'], 'security')
        self.assertEqual(found[0]['code'], 'x.eval')
        self.assertEqual(found[0]['symbols'], {'affected': [], 'related': []})

    def test_gitleaks_never_carries_the_secret_value(self):
        found = security_scan.parse_gitleaks(json.dumps(GITLEAKS), self.root)
        self.assertEqual(len(found), 1)
        text = json.dumps(found)
        self.assertNotIn('super-secret-value', text)
        self.assertIn('redacted', text)
        self.assertEqual(found[0]['severity'], 'high')

    def test_cargo_audit_parser_is_confirmed_advisory_evidence(self):
        found = security_scan.parse_cargo_audit(json.dumps(CARGO), self.root)
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0]['confidence'], 'CONFIRMED')
        self.assertIn('demo@1.0.0', found[0]['symbols']['affected'])

    def test_malformed_tool_output_is_not_clean(self):
        self.assertIsNone(security_scan.parse_semgrep('not json', self.root))
        self.assertIsNone(security_scan.parse_gitleaks('{}', self.root))
        self.assertIsNone(security_scan.parse_cargo_audit('{}', self.root))

    def test_missing_tools_leave_an_explicit_incomplete_result(self):
        payload = security_scan.security_scan(self.root, tools=['semgrep', 'gitleaks', 'cargo-audit'])
        self.assertEqual(payload['status'], 'UNAVAILABLE')
        self.assertTrue(all(tool['status'] == 'UNAVAILABLE' for tool in payload['tools'].values()))
        self.assertTrue(Path(payload['report']).is_file())


if __name__ == '__main__':
    unittest.main()
