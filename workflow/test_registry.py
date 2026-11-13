import concurrent.futures
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import registry as r


class RegistryTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        r.initialize(self.root)

    def mission(self):return r.new_mission(self.root,'Feature','specs/001-feature')

    def test_global_tor_counter_is_shared_and_task_counter_is_per_mission(self):
        mission=self.mission()
        self.assertEqual(r.reserve(self.root,'standalone')['id'],'TOR001')
        self.assertEqual(r.reserve(self.root,'extra',mission,tor=True)['id'],'M001-TOR002')
        self.assertEqual(r.reserve(self.root,'standalone two')['id'],'TOR003')
        self.assertEqual(r.reserve(self.root,'feature task',mission)['id'],'M001-T001')
        self.assertEqual(r.validate(self.root)['reserved'],4)

    def test_concurrent_reservations_never_share_a_number(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            ids=list(executor.map(lambda n:r.reserve(self.root,f'task {n}')['id'],range(18)))
        self.assertEqual(len(set(ids)),18);self.assertEqual(r.load(self.root)['next_tor'],19)
        r.validate(self.root)

    def test_completion_updates_only_matching_record_and_names_only_history(self):
        identifier=r.reserve(self.root,'Small change')['id']
        self.assertNotIn(identifier,(self.root/r.HISTORY).read_text())
        r.complete(self.root,identifier,'independent-reviewer',2,'targeted checks PASS')
        self.assertEqual((self.root/r.HISTORY).read_text(),'# Task history\n\n- TOR001 — Small change\n')
        self.assertIn('- Status: COMPLETE',(self.root/'.specify/quick-tasks/TOR001.md').read_text())
        with self.assertRaises(ValueError):r.complete(self.root,identifier,'reviewer',1,'PASS')
        r.validate(self.root)

    def test_final_task_waits_for_other_tasks_and_extras_are_inserted_before_it(self):
        mission=self.mission();task=r.reserve(self.root,'work',mission)['id'];final=r.reserve(self.root,'Review',mission,final=True)['id']
        extra=r.reserve(self.root,'extra',mission,tor=True)['id']
        text=(self.root/'specs/001-feature/tasks.md').read_text();self.assertLess(text.index(extra),text.index(final))
        with self.assertRaises(ValueError):r.complete(self.root,final,'reviewer',1,'PASS')
        for identifier in [task,extra,final]:r.complete(self.root,identifier,'reviewer',1,'PASS')
        with self.assertRaises(ValueError):r.reserve(self.root,'late extra',mission,tor=True)
        r.validate(self.root)

    def test_manual_duplicate_ids_counter_reset_and_history_drift_fail(self):
        mission=self.mission();r.reserve(self.root,'one');r.reserve(self.root,'two',mission,tor=True)
        state=r.load(self.root);state['tasks']['M001-TOR001']=state['tasks'].pop('M001-TOR002')
        with self.assertRaises(ValueError):r.check_data(state)
        state=r.load(self.root);state['next_tor']=1
        with self.assertRaises(ValueError):r.check_data(state)
        (self.root/r.HISTORY).write_text('hand edited')
        with self.assertRaises(ValueError):r.validate(self.root)

    def test_duplicate_json_keys_and_unregistered_records_are_rejected(self):
        file=self.root/r.REGISTRY;original=file.read_text();file.write_text('{"version":1,"version":1}')
        with self.assertRaises(ValueError):r.load(self.root)
        file.write_text(original)
        target=self.root/'.specify/quick-tasks/TOR777.md';target.parent.mkdir(parents=True);target.write_text('# TOR777 — manual\n')
        with self.assertRaises(ValueError):r.validate(self.root)

    def test_paths_and_multiline_titles_cannot_escape_or_inject_history(self):
        for name in ['', 'one\n- injected']:
            with self.assertRaises(ValueError):r.reserve(self.root,name)
        with self.assertRaises(ValueError):r.new_mission(self.root,'escape','../outside')
        with tempfile.TemporaryDirectory() as other:
            (self.root/'.specify/quick-tasks').symlink_to(other,target_is_directory=True)
            with self.assertRaises(ValueError):r.reserve(self.root,'escaped')

    def test_existing_registry_cannot_be_reset(self):
        with self.assertRaises(ValueError):r.initialize(self.root)

    def test_legacy_migration_preserves_completed_records_and_reserves_global_numbers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);file=root/'specs/001-feature/tasks.md';file.parent.mkdir(parents=True)
            file.write_text('# Tasks: Feature\n\n- [x] T001 Work\n- [x] T002 [FINAL] Review\n')
            quick=root/'.specify/quick-tasks/TOR001.md';quick.parent.mkdir(parents=True)
            quick.write_text('# TOR001 — Quick\n\n- Status: COMPLETE\n')
            result=r.initialize(root,migrate=True);self.assertEqual(result['completed'],3)
            self.assertIn('M001-T001',file.read_text());self.assertIn('TOR001 — Quick',(root/r.HISTORY).read_text())
            mission=r.new_mission(root,'Next','specs/002-next')
            self.assertEqual(r.reserve(root,'Extra',mission,tor=True)['id'],'M002-TOR002')

    def test_base_validation_detects_reassignment_or_removed_reservations(self):
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        subprocess.run(['git','config','user.email','test@example.invalid'],cwd=self.root,check=True)
        subprocess.run(['git','config','user.name','Test'],cwd=self.root,check=True)
        (self.root/'.gitignore').write_text('.work/\n')
        identifier=r.reserve(self.root,'Reserved')['id']
        subprocess.run(['git','add','.'],cwd=self.root,check=True)
        subprocess.run(['git','commit','-qm','baseline'],cwd=self.root,check=True)
        base=subprocess.check_output(['git','rev-parse','HEAD'],cwd=self.root,text=True).strip()
        r.validate(self.root,base)
        state=r.load(self.root);state['tasks'][identifier]['nonce']='different-clone';r.save(self.root,state)
        with self.assertRaises(ValueError):r.validate(self.root,base)

    def test_cli_uses_real_allocator_and_validation_exit_status(self):
        script=Path(__file__).with_name('work.py')
        done=subprocess.run([__import__('sys').executable,str(script),'--root',str(self.root),'task','reserve','--title','CLI task'],capture_output=True,text=True)
        self.assertEqual(done.returncode,0);self.assertEqual(json.loads(done.stdout)['id'],'TOR001')
        (self.root/r.HISTORY).write_text('wrong')
        done=subprocess.run([__import__('sys').executable,str(script),'--root',str(self.root),'validate'],capture_output=True,text=True)
        self.assertEqual(done.returncode,2)

    def test_separate_cli_processes_reserve_distinct_tor_ids(self):
        script=Path(__file__).with_name('work.py');python=__import__('sys').executable
        processes=[subprocess.Popen([python,str(script),'--root',str(self.root),'task','reserve','--title',f'Process {n}'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True) for n in range(6)]
        identifiers=[]
        for process in processes:
            out,err=process.communicate(timeout=15);self.assertEqual(process.returncode,0,err);identifiers.append(json.loads(out)['id'])
        self.assertEqual(len(set(identifiers)),6);r.validate(self.root)

    def test_ambiguous_standalone_status_and_final_marker_are_rejected(self):
        identifier=r.reserve(self.root,'quick')['id'];record=self.root/f'.specify/quick-tasks/{identifier}.md'
        record.write_text(record.read_text()+'\n- Status: COMPLETE\n')
        with self.assertRaises(ValueError):r.validate(self.root)
        with self.assertRaises(ValueError):r.reserve(self.root,'[FINAL] marker',self.mission())

    def test_migration_with_reused_global_tor_number_does_not_write_partial_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);file=root/'specs/001-feature/tasks.md';file.parent.mkdir(parents=True)
            text='# Feature\n\n- [x] TOR001 Extra\n';file.write_text(text)
            record=root/'.specify/quick-tasks/TOR001.md';record.parent.mkdir(parents=True)
            record.write_text('# TOR001 — Standalone\n\n- Status: COMPLETE\n')
            with self.assertRaises(ValueError):r.initialize(root,True)
            self.assertFalse((root/r.REGISTRY).exists());self.assertEqual(file.read_text(),text)

    def test_validate_against_base_rejects_allocations_in_previously_skipped_range(self):
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        for key,value in [('user.email','test@example.invalid'),('user.name','Test')]:subprocess.run(['git','config',key,value],cwd=self.root,check=True)
        (self.root/'.gitignore').write_text('.work/\n')
        state=r.load(self.root);state['next_tor']=8;r.save(self.root,state)
        subprocess.run(['git','add','.'],cwd=self.root,check=True);subprocess.run(['git','commit','-qm','base'],cwd=self.root,check=True)
        base=subprocess.check_output(['git','rev-parse','HEAD'],cwd=self.root,text=True).strip()
        allocated=r.reserve(self.root,'late')['id'];self.assertEqual(allocated,'TOR008')
        state=r.load(self.root);entry=state['tasks'].pop(allocated);entry['record']='.specify/quick-tasks/TOR003.md';state['tasks']['TOR003']=entry;r.save(self.root,state)
        old=self.root/'.specify/quick-tasks/TOR008.md';new=self.root/entry['record'];new.write_text(old.read_text().replace('TOR008','TOR003'));old.unlink()
        r.validate(self.root)
        with self.assertRaises(ValueError):r.validate(self.root,base)

    def test_malformed_task_checkboxes_are_not_ignored_in_any_scope(self):
        mission=self.mission();r.reserve(self.root,'valid',mission)
        record=self.root/'specs/001-feature/tasks.md';original=record.read_text()
        record.write_text(original+'\n- [ ] M001-TABC Manual invalid ID\n')
        with self.assertRaises(ValueError):r.validate(self.root)
        record.write_text(original)
        unregistered=self.root/'specs/002-other/tasks.md';unregistered.parent.mkdir(parents=True)
        unregistered.write_text('# Other\n\n- [ ] M002-TABC Invalid\n')
        with self.assertRaises(ValueError):r.validate(self.root)
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);file=root/'specs/001-invalid/tasks.md';file.parent.mkdir(parents=True)
            text='- [ ] M001-TABC Invalid\n';file.write_text(text)
            with self.assertRaises(ValueError):r.initialize(root,True)
            self.assertFalse((root/r.REGISTRY).exists());self.assertEqual(file.read_text(),text)

    def test_unicode_task_titles_and_review_evidence_roundtrip_in_utf8_records(self):
        name='تعديل زر التشغيل 🎵';identifier=r.reserve(self.root,name)['id']
        r.complete(self.root,identifier,'مراجع مستقل',1,'فحوصات PASS')
        self.assertIn(name,(self.root/r.HISTORY).read_text(encoding='utf-8'))
        self.assertEqual(r.load(self.root)['tasks'][identifier]['title'],name)
        r.validate(self.root)
