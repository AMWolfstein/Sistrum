import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import audit
import test_checks as checks


class TestAuditChecks(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        self.config=json.loads(Path(audit.__file__).with_name('audit-config.json').read_text())

    def write(self,path,text):
        file=self.root/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);return file

    def test_assertions_in_comments_strings_do_not_hide_missing_assertion(self):
        self.write('test/a.dart', '''// test('fake', () {});
test('first', () { final text = 'expect(x)'; /* assert(true); */ print(text); });
test('second', () { expect(1, 1); });
''')
        findings,status=checks.inspect_tests(self.root,{'dart':['test/a.dart']},{},lambda p:True)
        self.assertEqual(status['tests_read'],2)
        self.assertEqual([f['code'] for f in findings],['assertion_free'])

    def test_duplicate_bodies_are_leads_and_string_whitespace_is_significant(self):
        body="() { expect(transform('a b'), equals('c')); }"
        self.write('test/a.dart',f"test('one', {body});\ntest('two', {body});\ntest('three', {body.replace('a b','ab')});\n")
        findings,_=checks.inspect_tests(self.root,{'dart':['test/a.dart']},{},lambda p:True)
        self.assertEqual([f['code'] for f in findings],['same_body'])
        self.assertIn('setup',findings[0]['message'])

    def test_rust_inline_and_junit_expected_exceptions(self):
        self.write('src/lib.rs','#[test]\n#[should_panic]\nfn panics() { panic!("yes"); }\n')
        self.write('test/Main.java','@Test(expected = Exception.class)\npublic void throwsError() { action(); }\n')
        findings,status=checks.inspect_tests(self.root,{'rust':['src/lib.rs'],'java':['test/Main.java']},{},lambda p:p.startswith('test/'))
        self.assertEqual(status['tests_read'],2);self.assertEqual(findings,[])

    def test_unsupported_test_syntax_is_explicit_partial_coverage(self):
        self.write('tests/a.cpp','TEST(Audio, Plays) { EXPECT_TRUE(play()); }')
        _,status=checks.inspect_tests(self.root,{'cpp':['tests/a.cpp']},{},lambda p:True)
        self.assertEqual(status['status'],'PARTIAL');self.assertIn('tests/a.cpp',status['syntax_limits'])

    def test_direct_reference_check_reads_tests_beyond_selected_source_scope(self):
        self.write('lib/main.dart','void main() {}')
        self.write('test/main_test.dart',"import '../lib/main.dart';\ntest('x', () { expect(1,1); });")
        with mock.patch.object(audit,'run_tool',return_value=([],{'status':'RAN'})):
            result,_=audit.scan(self.root,paths=['lib/main.dart'])
        self.assertNotIn('no_direct_test',[f['code'] for f in result['findings']])
        self.assertIn('coverage-dart',result['gaps'])
        self.assertEqual(result['status'],'INCOMPLETE')

    def test_combined_default_collects_code_and_test_findings_without_running_tests(self):
        self.write('lib/main.dart','void main() {}')
        self.write('test/a.dart',"test('empty', () { work(); });")
        diagnostic={'path':'lib/main.dart','line':1,'code':'unused_import','message':'unused'}
        with mock.patch.object(audit,'run_tool',return_value=([diagnostic],{'status':'RAN'})) as run:
            result,report=audit.scan(self.root,paths=['.'])
        self.assertEqual(run.call_count,1)
        self.assertEqual(result['kind'],'all')
        categories={c for finding in result['findings'] for c in finding['categories']}
        self.assertEqual(categories,{'dead-code','tests','gaps'})
        self.assertIn('INCOMPLETE',report.read_text())

    def test_tests_only_skips_compilers_and_missing_coverage(self):
        self.write('test/a.dart',"test('empty', () { work(); });")
        with mock.patch.object(audit,'run_tool') as run:
            result,_=audit.scan(self.root,kind='tests',paths=['.'])
        run.assert_not_called();self.assertEqual(result['status'],'FINDINGS');self.assertEqual(result['gaps'],[])

    def test_lcov_aggregates_records_and_rejects_stale_or_malformed_evidence(self):
        source=self.write('lib/a.dart','void a() {}\n')
        report=self.write('coverage/lcov.info','SF:lib/a.dart\nDA:1,0\nend_of_record\nSF:lib/a.dart\nDA:1,2\nDA:2,0\nend_of_record\n')
        settings={'dart':{'lcov':'coverage/lcov.info','produce':'flutter test --coverage'}}
        findings,status=checks.inspect_coverage(self.root,{'dart':['lib/a.dart']},settings)
        self.assertEqual([f['line'] for f in findings],[2]);self.assertEqual(status['dart']['status'],'RAN')
        os.utime(source,(report.stat().st_mtime+10,report.stat().st_mtime+10))
        findings,status=checks.inspect_coverage(self.root,{'dart':['lib/a.dart']},settings)
        self.assertEqual(findings,[]);self.assertEqual(status['dart']['status'],'PARTIAL')
        report.write_text('SF:lib/a.dart\nDA:bad,0\n')
        _,status=checks.inspect_coverage(self.root,{'dart':['lib/a.dart']},settings)
        self.assertEqual(status['dart']['status'],'INCOMPLETE')

    def test_cli_default_combines_checks_and_can_target_whole_repo(self):
        args=audit.configure_parser(__import__('argparse').ArgumentParser()).parse_args([])
        self.assertEqual(args.kind,'all')
        args.root=self.root
        with mock.patch.object(audit,'scan',return_value=({'status':'PASS','findings':[],'gaps':[]},self.root/'report.md')) as scan:
            audit.execute(args)
        self.assertEqual(scan.call_args.args[3],['.'])

    def test_gaps_only_test_scope_does_not_claim_sources_were_checked(self):
        self.write('test/a.dart',"test('check', () { expect(1,1); });")
        result,_=audit.scan(self.root,kind='gaps',paths=['test'])
        self.assertEqual(result['status'],'NO_FILES')
