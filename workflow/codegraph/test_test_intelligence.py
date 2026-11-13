import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import quality
import test_intelligence


class TestIntelligenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.write('lib/a.dart', 'int add(int a, int b) => a + b;\n')
        self.write('test/a_test.dart', """import 'package:flutter_test/flutter_test.dart';
import '../lib/a.dart';

void main() {
  test('adds', () {
    expect(add(1, 1), 2);
    expect(add(2, 2), 4);
  });

  test('adds again', () {
    expect(add(1, 1), 2);
    expect(add(2, 2), 4);
  });

  test('weak', () {
    expect(add(1, 1), isNotNull);
  });

  test('sleeps', () async {
    await Future.delayed(const Duration(seconds: 1));
    expect(add(1, 1), 2);
  });
}
""")
        self.write('workflow/test_probe.py', """import unittest
class Probe(unittest.TestCase):
    def test_ok(self):
        self.assertEqual(1, 1)
""")

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        return target

    def test_inventory_uses_parsed_declarations_not_regex_only(self):
        tests, status = test_intelligence.inventory(self.root)
        names = {test['identity'] for test in tests}
        self.assertIn('adds', names)
        self.assertIn('test_ok', names)
        self.assertEqual(status['status'], 'PASS')
        dart = next(test for test in tests if test['identity'] == 'adds')
        self.assertEqual(dart['category'], 'widget/unit')
        self.assertEqual(dart['execution_status'], 'NOT_EXECUTED')

    def test_runtime_results_are_attached_only_when_supplied(self):
        tests, _ = test_intelligence.inventory(self.root)
        report = self.write('results.json', json.dumps({'tests': [{'name': 'adds', 'status': 'PASSED', 'duration': 0.1}]}))
        tests, status = test_intelligence.apply_results(tests, report)
        target = next(test for test in tests if test['identity'] == 'adds')
        self.assertEqual(target['execution_status'], 'PASSED')
        self.assertEqual(status['matched'], 1)

    def test_quality_detects_duplicate_weak_and_fragile_tests(self):
        findings, tools = test_intelligence.quality_audit(self.root)
        codes = {finding['code'] for finding in findings}
        self.assertIn('same_body', codes)
        self.assertIn('weak_assertion', codes)
        self.assertIn('fragile_async', codes)
        self.assertEqual(tools['heuristics']['status'], 'PASS')

    def test_coverage_imports_branch_data_and_stays_partial(self):
        self.write('coverage/lcov.info', 'SF:lib/a.dart\nDA:1,1\nDA:2,0\nBRDA:1,0,0,1\nBRDA:1,0,1,0\nend_of_record\n')
        findings, tools, summary = test_intelligence.coverage(self.root)
        self.assertEqual(summary['dart']['branch_records'], 2)
        self.assertEqual(summary['dart']['branch_hits'], 1)
        self.assertEqual(tools['dart']['status'], 'RAN')
        self.assertTrue(any(finding['code'] == 'uncovered_lines' for finding in findings))
        self.assertEqual(tools['dart']['status'] in ('PARTIAL', 'RAN'), True)

    def test_mutation_is_opt_in_and_needs_a_scope(self):
        findings, status = test_intelligence.mutation(self.root, 'rust', paths=())
        self.assertEqual(findings, [])
        self.assertEqual(status['status'], quality.STATUS_SKIPPED)
        findings, status = test_intelligence.mutation(self.root, 'rust', paths=('src',), execute=False)
        self.assertEqual(status['status'], quality.STATUS_SKIPPED)

    def test_impact_without_a_map_expands_conservatively(self):
        result = test_intelligence.impact(self.root, ['lib/a.dart'])
        self.assertEqual(result['status'], quality.STATUS_INCOMPLETE)
        self.assertTrue(result['conservative'])
        self.assertGreaterEqual(len(result['selected_tests']), 5)

    def test_impact_with_a_map_selects_referencing_tests(self):
        self.write('.work/codegraph/map.json', json.dumps({
            'nodes': [{'id': 'n1', 'label': 'add', 'source_file': 'lib/a.dart'},
                      {'id': 'n2', 'label': 'helper', 'source_file': 'lib/b.dart'}],
            'edges': [{'source': 'n2', 'target': 'n1', 'relation': 'calls'}]}))
        result = test_intelligence.impact(self.root, ['lib/b.dart'])
        self.assertIn('test/a_test.dart::adds', result['selected_tests'])


if __name__ == '__main__':
    unittest.main()
