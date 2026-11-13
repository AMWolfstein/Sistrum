import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import code_tools as tools
import serena_adapter as serena


class NavigationTests(unittest.TestCase):
    def test_inputs_reject_escape_and_symlinks_and_exclude_builds(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'main.dart').write_text('void main() {}')
            (root/'linked.dart').symlink_to(root/'main.dart')
            self.assertEqual(tools.inputs(root,['main.dart','target/a.rs','README.md']),
                             (['main.dart'],['target/a.rs','README.md']))
            for path in ['../escape.rs','/tmp/escape.rs','linked.dart']:
                with self.assertRaises(ValueError): tools.inputs(root,[path])

    def test_native_owners_are_only_single_enclosing_classes(self):
        texts = {'Native.kt':'package app\nobject Native {\n external fun play(): Long\n class Nested {\n external fun skip(): Long\n }\n}'}
        nodes = [{'source_file':'Native.kt','source_location':'L3','label':'play'},
                 {'source_file':'Native.kt','source_location':'L5','label':'skip'}]
        self.assertEqual(tools.jvm_symbols(nodes,texts),[{'file':'Native.kt','line':3,'name':'play','owner':'Native'}])

    def test_channels_ignore_strings_and_report_dynamic_names(self):
        texts = {'main.dart':'''final a = MethodChannel('playback');
// MethodChannel('comment')
final fake = "MethodChannel('fake')";
final dynamic = MethodChannel(channelName);
''', 'Main.kt':'val channel = MethodChannel(messenger, "playback")'}
        records = tools.channels(texts)
        self.assertEqual([r['name'] for r in records],['playback',None,'playback'])
        self.assertEqual(records[1]['status'],'unresolved-channel-name')

    def test_query_keeps_incoming_edges_native_candidates_and_channels(self):
        data = {'scope':{},'nodes':[{'id':'a','label':'play','source_file':'main.dart'},
                {'id':'b','label':'run','source_file':'other.dart'}],
                'edges':[{'source':'b','target':'a'}],
                'resources':{'links':[{'from':'main.dart','to':'data.json'}], 'coverage':{}},
                'native_bindings':[{'file':'Main.kt','candidates':[{'file':'native.cpp'}]}],
                'platform_channels':[{'file':'main.dart','name':'audio'}, {'file':'Main.kt','name':'audio'}, {'file':'x.dart','name':'else'}],
                'coverage':[{'file':'main.dart','status':'partial'}], 'limitations':[]}
        result = tools.query(data,symbol='play')
        self.assertEqual(result['totals']['relationships'],1)
        self.assertEqual(result['totals']['platform_channels'],2)
        self.assertEqual(result['coverage'][0]['status'],'partial')
        self.assertEqual(len(tools.query(data,file='native.cpp')['native_bindings']),1)
        self.assertEqual(tools.query(data,file='data.json')['totals']['resources'],1)

    def test_failed_update_preserves_previous_map(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);cache=root/'.work/codegraph';cache.mkdir(parents=True)
            (cache/'map.json').write_text('previous map')
            with mock.patch.object(tools.graph,'extract',side_effect=RuntimeError('index failed')):
                with self.assertRaises(RuntimeError): tools.update(root,[])
            self.assertEqual((cache/'map.json').read_text(),'previous map')

    def test_serena_allowlist_rejects_write_tools_and_escaping_paths(self):
        for operation, file in [('replace_symbol_body','main.dart'),('references','../main.dart'),('symbols','/tmp/main.dart')]:
            with self.assertRaises(ValueError): serena.arguments(operation,file,'main')
        self.assertEqual(serena.arguments('references','main.dart','Player/play')['name_path'],'Player/play')
        self.assertEqual(serena.arguments('definition','main.dart','Player')['name_path_pattern'],'Player')

    def test_serena_home_defaults_disable_rust_execution_without_overwriting_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory)/'home'
            serena.prepare_home(home)
            file=home/'serena_config.yml';settings=json.loads(file.read_text())
            self.assertEqual(settings['projects'],[])
            rust=settings['ls_specific_settings']['rust']['initializationOptions']
            self.assertFalse(rust['cargo']['buildScripts']['enable'])
            self.assertFalse(rust['procMacro']['enable'])
            file.write_text('custom settings')
            serena.prepare_home(home)
            self.assertEqual(file.read_text(),'custom settings')
