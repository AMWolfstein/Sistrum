import concurrent.futures
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import bootstrap as b
import environment
import registry as r


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        subprocess.run(['git','init','-q',str(self.root)],check=True)

    def test_fresh_project_gets_history_empty_registry_and_portable_entrypoints(self):
        result=b.prepare(self.root)
        self.assertEqual(result['status'],'PASS');self.assertEqual(result['project']['history'],'created')
        self.assertEqual((self.root/r.HISTORY).read_text(),'# Task history\n\n')
        self.assertEqual(r.validate(self.root)['completed'],0)
        self.assertEqual(r.load(self.root)['next_tor'],1)
        self.assertIn('workflow/README.md',(self.root/'AGENTS.md').read_text())
        self.assertTrue((self.root/'.agents/skills/ponytail/SKILL.md').is_file())
        self.assertTrue((self.root/'.github/workflows/workflow-integrity.yml').is_file())
        self.assertEqual(subprocess.run(['git','check-ignore','-q','.work/probe'],cwd=self.root).returncode,0)

    def test_existing_history_bytes_and_mtime_survive_repeated_setup_even_offline(self):
        file=self.root/r.HISTORY;data='# My history\n\n- مهمتي القديمة 🎵\n'.encode();file.write_bytes(data)
        before=file.stat().st_mtime_ns
        ready={'status':'PASS','checks':[{'name':name,'status':'PASS'} for name in ['codegraph','parsers']],'optional':[]}
        with mock.patch.object(environment,'doctor',return_value=ready):
            first=environment.setup(self.root);second=environment.setup(self.root,offline=True)
        self.assertEqual(file.read_bytes(),data);self.assertEqual(file.stat().st_mtime_ns,before)
        self.assertEqual(first['bootstrap']['project']['history'],'preserved')
        self.assertEqual(second['bootstrap']['project']['history'],'preserved')
        self.assertFalse((self.root/r.REGISTRY).exists())
        self.assertEqual(first['status'],'INCOMPLETE') # Existing unknown history must not reset counters.

    def test_existing_custom_support_and_agent_instructions_are_not_replaced(self):
        file=self.root/'.specify/project-policy.md';file.parent.mkdir();file.write_text('Custom policy')
        agents=self.root/'AGENTS.md';agents.write_text('Owner instructions\n')
        b.prepare(self.root);after=agents.read_bytes();mtime=agents.stat().st_mtime_ns
        b.prepare(self.root)
        self.assertEqual(file.read_text(),'Custom policy');self.assertTrue(after.startswith(b'Owner instructions\n'))
        self.assertEqual(agents.read_bytes(),after);self.assertEqual(agents.stat().st_mtime_ns,mtime)

    def test_missing_history_reconstructs_only_valid_local_completed_records(self):
        r.initialize(self.root);identifier=r.reserve(self.root,'Local completed')['id']
        r.complete(self.root,identifier,'reviewer',1,'PASS')
        expected=(self.root/r.HISTORY).read_bytes();(self.root/r.HISTORY).unlink()
        before=(self.root/r.REGISTRY).read_bytes();result=b.prepare(self.root)
        self.assertEqual(result['status'],'PASS');self.assertEqual((self.root/r.HISTORY).read_bytes(),expected)
        self.assertEqual((self.root/r.REGISTRY).read_bytes(),before)

    def test_existing_valid_history_is_not_rewritten_by_prepare(self):
        r.initialize(self.root);file=self.root/r.HISTORY;before=file.read_bytes();mtime=file.stat().st_mtime_ns
        result=b.prepare(self.root)
        self.assertEqual(result['status'],'PASS');self.assertEqual(result['project']['history'],'preserved')
        self.assertEqual(file.read_bytes(),before);self.assertEqual(file.stat().st_mtime_ns,mtime)

    def test_task_state_accidentally_placed_in_assets_is_never_copied(self):
        with tempfile.TemporaryDirectory() as tmp:
            assets=Path(tmp)
            for path in [r.HISTORY,r.REGISTRY,'.specify/quick-tasks/TOR888.md','specs/001-old/tasks.md']:
                file=assets/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text('Do not import old task data')
            result=b.prepare(self.root,assets)
        self.assertEqual(result['status'],'PASS');self.assertEqual(r.validate(self.root)['completed'],0)
        self.assertFalse((self.root/'.specify/quick-tasks/TOR888.md').exists());self.assertFalse((self.root/'specs/001-old').exists())

    def test_concurrent_bootstrap_creates_one_coherent_local_history(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            results=list(executor.map(lambda _:b.prepare(self.root),range(4)))
        self.assertTrue(all(result['status']=='PASS' for result in results));r.validate(self.root)
        self.assertEqual(sum(result['project']['history']=='created' for result in results),1)

    def test_history_created_between_checks_and_open_is_preserved(self):
        original=b.create
        def race(root,path,data,mode=0o644):
            if path==r.HISTORY and not (root/path).exists():(root/path).write_bytes(b'External history wins\n')
            return original(root,path,data,mode)
        with mock.patch.object(b,'create',side_effect=race):result=b.prepare(self.root)
        self.assertEqual((self.root/r.HISTORY).read_bytes(),b'External history wins\n')
        self.assertEqual(result['status'],'INCOMPLETE');self.assertFalse((self.root/r.REGISTRY).exists())

    def test_existing_legacy_records_are_not_silently_reset(self):
        record=self.root/'specs/001-legacy/tasks.md';record.parent.mkdir(parents=True);record.write_text('- [x] T001 Old work\n')
        result=b.prepare(self.root)
        self.assertEqual(result['status'],'INCOMPLETE');self.assertFalse((self.root/r.REGISTRY).exists())
        self.assertEqual(record.read_text(),'- [x] T001 Old work\n')

    def test_history_symlink_cannot_be_followed_or_replaced(self):
        with tempfile.TemporaryDirectory() as tmp:
            outside=Path(tmp)/'history';outside.write_text('external')
            (self.root/r.HISTORY).symlink_to(outside)
            with self.assertRaises(ValueError):b.prepare(self.root)
            self.assertEqual(outside.read_text(),'external')

    def test_explicit_init_also_refuses_to_erase_unknown_existing_history(self):
        file=self.root/r.HISTORY;file.write_bytes(b'Owner history must survive\n')
        with self.assertRaises(ValueError):r.initialize(self.root,migrate=True)
        self.assertEqual(file.read_bytes(),b'Owner history must survive\n')
        self.assertFalse((self.root/r.REGISTRY).exists())

    def test_shared_instruction_assets_match_the_installed_copies(self):
        repository=Path(b.__file__).resolve().parents[1];assets=Path(b.__file__).with_name('assets')
        drifted=[]
        for source in sorted(assets.rglob('*')):
            if not source.is_file():continue
            relative=source.relative_to(assets).as_posix()
            if not (relative.startswith('.agents/') or relative=='.github/workflows/workflow-integrity.yml'):continue
            installed=repository/relative
            if not installed.is_file() or installed.read_bytes()!=source.read_bytes():drifted.append(relative)
        self.assertEqual(drifted,[])
