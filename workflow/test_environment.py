import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import environment as e
import registry as r


class EnvironmentTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        (self.root/'.gitignore').write_text('.work/\n')
        (self.root/'workflow/codegraph').mkdir(parents=True)
        bootstrap_mock=mock.patch('bootstrap.prepare',return_value={'status':'PASS','project':{'history':'preserved'}})
        bootstrap_mock.start();self.addCleanup(bootstrap_mock.stop)

    def test_prerequisites_refuse_unignored_or_escaped_managed_paths(self):
        self.assertIn('verified',e.prerequisites(self.root))
        (self.root/'.gitignore').write_text('')
        with self.assertRaises(ValueError):e.prerequisites(self.root)
        (self.root/'.gitignore').write_text('.work/\n')
        with tempfile.TemporaryDirectory() as other:
            (self.root/'.work').symlink_to(other,target_is_directory=True)
            with self.assertRaises(ValueError):e.prerequisites(self.root)

    def test_doctor_reports_missing_core_without_installing(self):
        with mock.patch.object(e,'run',side_effect=FileNotFoundError('tool absent')),mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e.subprocess,'run') as process:
            result=e.doctor(self.root)
        self.assertEqual(result['status'],'INCOMPLETE')
        self.assertTrue(any(check['status']=='UNAVAILABLE' for check in result['checks']))
        process.assert_not_called()

    def before(self,ready=False):
        checks=[{'name':name,'status':'PASS' if ready or name not in ('parsers',) else 'UNAVAILABLE'} for name in ['prerequisites','node','npm','codegraph','parsers']]
        return {'status':'PASS' if ready else 'INCOMPLETE','checks':checks,'optional':[]}

    def test_offline_setup_does_not_run_installers(self):
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'doctor',return_value=self.before()),mock.patch.object(e.subprocess,'run') as run:
            result=e.setup(self.root,offline=True)
        self.assertTrue(result['offline']);self.assertEqual(result['status'],'INCOMPLETE')
        run.assert_not_called()

    def test_setup_installs_only_managed_pinned_packages_and_is_idempotent(self):
        calls=[]
        def install(argv,**kwargs):
            calls.append(argv)
            if argv[1:3]==['-m','venv']:
                folder=Path(argv[3]);python=folder/('Scripts/python.exe' if e.os.name=='nt' else 'bin/python');python.parent.mkdir(parents=True);python.write_text('')
            return subprocess.CompletedProcess(argv,0)
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'verify_venv'),mock.patch.object(e,'doctor',side_effect=[self.before(),self.before(True)]),mock.patch.object(e.subprocess,'run',side_effect=install):
            result=e.setup(self.root)
        self.assertFalse(result['reused']);self.assertEqual(len(calls),2)
        self.assertIn('requirements.txt',calls[-1][-1]);self.assertTrue(str(e.interpreter(self.root,'code-tools-python')).startswith(str(self.root)))
        calls.clear()
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'doctor',return_value=self.before(True)),mock.patch.object(e.subprocess,'run',side_effect=install):
            result=e.setup(self.root)
        self.assertTrue(result['reused']);self.assertEqual(calls,[])

    def test_failed_provisioning_never_reports_success(self):
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'doctor',return_value=self.before()),mock.patch.object(e.subprocess,'run',return_value=subprocess.CompletedProcess([],1)):
            with self.assertRaises(ValueError):e.setup(self.root)

    def test_status_is_read_only_for_registry_and_shows_shared_next_numbers(self):
        r.initialize(self.root);mission=r.new_mission(self.root,'Feature','specs/001-feature')
        r.reserve(self.root,'Quick');r.reserve(self.root,'Extra',mission,tor=True)
        before=(self.root/r.REGISTRY).read_bytes()
        with mock.patch.object(e,'run',side_effect=[''+str(self.root),'branch','abc123','']):result=e.status(self.root)
        self.assertEqual(result['next_tor'],'TOR003');self.assertEqual(result['next_mission'],'M002')
        self.assertEqual((self.root/r.REGISTRY).read_bytes(),before)

    def test_full_doctor_adds_application_availability_requirements(self):
        with mock.patch.object(e,'run',side_effect=FileNotFoundError('missing')),mock.patch.object(e,'prerequisites',return_value='PASS'):
            result=e.doctor(self.root,full=True,serena=True)
        names={item['name'] for item in result['checks']}
        self.assertTrue({'dart','rust-clippy','java','android','clang-tidy/database','serena'}<=names)
        self.assertEqual(result['status'],'INCOMPLETE')

    def test_setup_refuses_python_outside_managed_prefix_before_pip(self):
        python=e.interpreter(self.root,'code-tools-python');python.parent.mkdir(parents=True)
        python.symlink_to(__import__('sys').executable)
        original=e.subprocess.run
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'doctor',return_value=self.before()),mock.patch.object(e.subprocess,'run',wraps=original) as run:
            with self.assertRaises(ValueError):e.setup(self.root)
        self.assertFalse(any('-m' in call.args[0] and 'pip' in call.args[0] for call in run.call_args_list))

    def test_serena_provisioning_is_explicit_and_uses_its_managed_venv(self):
        before=self.before(True);before['status']='INCOMPLETE';before['checks'].append({'name':'serena','status':'UNAVAILABLE'})
        after=self.before(True);after['checks'].append({'name':'serena','status':'PASS'})
        calls=[]
        def install(argv,**kwargs):
            calls.append(argv)
            if argv[1:3]==['-m','venv']:
                folder=Path(argv[3]);python=folder/('Scripts/python.exe' if e.os.name=='nt' else 'bin/python');python.parent.mkdir(parents=True);python.write_text('')
            return subprocess.CompletedProcess(argv,0)
        with mock.patch.object(e,'prerequisites',return_value='PASS'),mock.patch.object(e,'verify_venv'),mock.patch.object(e,'doctor',side_effect=[before,after]),mock.patch.object(e.subprocess,'run',side_effect=install):
            result=e.setup(self.root,serena=True)
        self.assertEqual(result['status'],'PASS');self.assertEqual(len(calls),2)
        self.assertIn('requirements-serena.txt',calls[-1][-1])
        self.assertIn('serena-python',calls[-1][0])
