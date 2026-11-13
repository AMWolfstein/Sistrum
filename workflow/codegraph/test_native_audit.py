import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import native_audit as native


class NativeAuditTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);(self.root/'android').mkdir()
        (self.root/'android/gradlew').write_text('#!/bin/sh\n')
        self.log=self.root/'run.log'

    def xml(self,path='android/app/src/main/java/Main.java',issue='UnusedAssignment',severity='Warning'):
        return f'<issues><issue id="{issue}" severity="{severity}" message="message"><location file="{path}" line="7"/></issue></issues>'

    def test_lint_parser_contains_paths_and_retains_internal_failures(self):
        report=self.root/'lint.xml';report.write_text('<issues><issue id="LintError" message="failed"/>'+self.xml()[8:-9]+self.xml('../outside.java')[8:-9]+'</issues>')
        found,errors=native.parse_lint(report,self.root)
        self.assertEqual(len(found),1);self.assertEqual(found[0]['line'],7)
        self.assertIn('failed',errors[0])
        report.write_text('<!DOCTYPE issues><issues/>')
        with self.assertRaises(ValueError):native.parse_lint(report,self.root)

    def test_android_never_consumes_stale_report(self):
        self.log.with_suffix('.xml').write_text(self.xml())
        with mock.patch.object(native.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','')):
            found,status=native.run_android(self.root,{},self.log)
        self.assertEqual(found,[]);self.assertEqual(status['status'],'INCOMPLETE')
        self.assertIn('fresh',status['reason'])

    def test_fresh_android_report_handles_code_findings_without_automatic_fixes(self):
        def process(argv,**kwargs):
            kwargs['stdout'].write('MORNYE_AUDIT_LINT checkTestSources=true\n')
            report=Path(next(a.split('=',1)[1] for a in argv if a.startswith('-Dmornye.audit.report=')))
            report.write_text(self.xml(severity='Error'))
            self.assertEqual(kwargs['cwd'],self.root/'android')
            self.assertIn(':app:lintDebug',argv)
            self.assertFalse(any('assemble' in a or '--fix' in a for a in argv))
            return subprocess.CompletedProcess(argv,0,'','')
        with mock.patch.object(native.subprocess,'run',side_effect=process):
            found,status=native.run_android(self.root,{},self.log)
        self.assertEqual(status['status'],'RAN');self.assertEqual(found[0]['severity'],'error')
        self.assertTrue(Path(status['report']).is_file())

    def test_gradle_failure_remains_incomplete_even_with_fresh_xml(self):
        def process(argv,**kwargs):
            Path(next(a.split('=',1)[1] for a in argv if a.startswith('-Dmornye.audit.report='))).write_text(self.xml())
            return subprocess.CompletedProcess(argv,1,'failed','')
        with mock.patch.object(native.subprocess,'run',side_effect=process):
            found,status=native.run_android(self.root,{},self.log)
        self.assertEqual(len(found),1);self.assertEqual(status['status'],'INCOMPLETE')

    def test_missing_wrapper_and_timeout_are_not_clean(self):
        with mock.patch.object(native.subprocess,'run',side_effect=subprocess.TimeoutExpired('gradle',1)):
            _,status=native.run_android(self.root,{},self.log)
        self.assertEqual(status['status'],'INCOMPLETE')
        (self.root/'android/gradlew').unlink()
        with mock.patch.object(native.shutil,'which',return_value=None):
            _,status=native.run_android(self.root,{},self.log)
        self.assertEqual(status['status'],'UNAVAILABLE')

    def test_gradle_on_path_replaces_an_untracked_wrapper(self):
        (self.root/'android/gradlew').unlink()
        def process(argv,**kwargs):
            Path(next(a.split('=',1)[1] for a in argv if a.startswith('-Dmornye.audit.report='))).write_text(self.xml())
            kwargs['stdout'].write('MORNYE_AUDIT_LINT checkTestSources=true\n')
            self.assertEqual(argv[0],'/usr/bin/gradle')
            return subprocess.CompletedProcess(argv,0,'','')
        with mock.patch.object(native.shutil,'which',return_value='/usr/bin/gradle'),mock.patch.object(native.subprocess,'run',side_effect=process):
            found,status=native.run_android(self.root,{},self.log)
        self.assertEqual(status['status'],'RAN');self.assertEqual(len(found),1)

    def database(self):
        source=self.root/'main.cpp';source.write_text('int a;')
        database=self.root/'compile_commands.json'
        database.write_text(json.dumps([{'directory':str(self.root),'file':str(source),'arguments':['clang++','-c',str(source)]}]))
        return database

    def test_cpp_real_compile_database_scope_and_header_gaps(self):
        database=self.database()
        output=f'{self.root}/main.cpp:1:2: warning: value unused [clang-diagnostic-unused-variable]\n'
        with mock.patch.object(native,'clang_tidy',return_value='clang-tidy'),mock.patch.object(native.subprocess,'run',return_value=subprocess.CompletedProcess([],0,output,'')) as run:
            found,status=native.run_cpp(self.root,{'database':str(database)},['main.cpp','main.h'],self.log)
        self.assertEqual(found[0]['code'],'clang-diagnostic-unused-variable')
        self.assertEqual(status['status'],'PARTIAL');self.assertEqual(status['uncovered_files'],['main.h'])
        argv=run.call_args.args[0];self.assertNotIn('--fix',argv);self.assertIn('-p',argv)
        self.assertEqual(status['covered_translation_units'],['main.cpp'])

    def test_cpp_compiler_errors_and_outside_paths(self):
        output=f'{self.root}/main.cpp:2:1: error: header missing [clang-diagnostic-error]\n/tmp/outside.cc:4:3: warning: ignored [performance-test]'
        found,errors=native.parse_clang(output,self.root,self.root)
        self.assertEqual(len(found),1);self.assertEqual(len(errors),1)
        database=self.database()
        with mock.patch.object(native,'clang_tidy',return_value='clang-tidy'),mock.patch.object(native.subprocess,'run',return_value=subprocess.CompletedProcess([],1,output,'')):
            _,status=native.run_cpp(self.root,{'database':str(database)},['main.cpp'],self.log)
        self.assertEqual(status['status'],'INCOMPLETE')

    def test_cpp_missing_invalid_or_unusable_database_is_explicit(self):
        with mock.patch.object(native,'clang_tidy',return_value='clang-tidy'):
            _,status=native.run_cpp(self.root,{},['main.cpp'],self.log)
            self.assertEqual(status['status'],'UNAVAILABLE')
            database=self.database();database.write_text('bad json')
            _,status=native.run_cpp(self.root,{'database':str(database)},['main.cpp'],self.log)
            self.assertEqual(status['status'],'INCOMPLETE')
            database.write_text('[]')
            _,status=native.run_cpp(self.root,{'database':str(database)},['main.cpp'],self.log)
            self.assertEqual(status['status'],'INCOMPLETE')

    def test_alternate_database_filename_cannot_validate_one_and_execute_another(self):
        self.database()
        alternate=self.root/'custom.json';alternate.write_text('[]')
        with mock.patch.object(native,'clang_tidy',return_value='clang-tidy'),mock.patch.object(native.subprocess,'run') as run:
            _,status=native.run_cpp(self.root,{'database':str(alternate)},['main.cpp'],self.log)
        self.assertEqual(status['status'],'INCOMPLETE');run.assert_not_called()
        self.assertIn('compile_commands.json',status['reason'])

    def test_android_test_source_coverage_requires_effective_configuration_evidence(self):
        def process(argv,**kwargs):
            report=Path(next(a.split('=',1)[1] for a in argv if a.startswith('-Dmornye.audit.report=')))
            report.write_text(self.xml(path='android/app/src/test/java/MainTest.java'))
            kwargs['stdout'].write('MORNYE_AUDIT_LINT checkTestSources=false\n')
            return subprocess.CompletedProcess(argv,0,'','')
        with mock.patch.object(native.subprocess,'run',side_effect=process):
            _,status=native.run_android(self.root,{},self.log)
        self.assertEqual(status['status'],'INCOMPLETE');self.assertFalse(status['test_sources_checked'])
