import unittest
import native_bindings

class NativeBoundaryTests(unittest.TestCase):
    def test_explicit_jni_macro_is_a_candidate_but_not_ordinary_call(self):
        texts = {'Native.kt': 'package test.app\nobject Native {\n external fun openOboe(): Long\n}\n',
                 'native.cpp': '#define JNI_AUDIO(name) Java_test_app_Native_##name\nJNIEXPORT long JNI_AUDIO(openOboe)(void *env) { return 1; }\n'}
        symbols = [{'file': 'Native.kt', 'line': 3, 'name': 'openOboe', 'owner': 'Native'}]
        data = native_bindings.inventory(texts, symbols)
        self.assertEqual(data[0]['status'], 'binding-candidate')
        self.assertEqual(data[0]['candidates'][0]['file'], 'native.cpp')
        self.assertEqual(data[0]['fact'], 'inferred')

    def test_strings_do_not_create_jni_implementations_and_uniffi_stays_unresolved(self):
        texts = {'Native.kt': 'package test.app\nobject Native {\n external fun openOboe(): Long\n}\n',
                 'native.cpp': 'const char *text = "Java_test_app_Native_openOboe()";\n',
                 'lib.rs': '#[uniffi::export]\npub fn exported() {}\n'}
        data = native_bindings.inventory(texts, [{'file': 'Native.kt', 'line': 3, 'name': 'openOboe', 'owner': 'Native'}])
        self.assertEqual(next(n for n in data if n['kind'] == 'JNI declaration')['status'], 'unresolved-native-implementation')
        self.assertTrue(any(n['status'] == 'unresolved-generated-binding' for n in data))
