"""Evidence inventory for native boundaries; never guess a cross-language call by name.

Direct JVM JNI names and simple token-paste JNI macros are recognized. Generated
UniFFI surfaces and dynamic RegisterNatives remain explicitly unresolved.
"""
from __future__ import annotations
import re

TOKENS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*')


def masked(text):
    return TOKENS.sub(lambda m: ''.join('\n' if c == '\n' else ' ' for c in m.group()), text)


def line(text, offset):
    return text.count('\n', 0, offset) + 1


def inventory(texts, symbols):
    records, implementations = [], {}
    for path, original in texts.items():
        if not path.endswith(('.cpp', '.c', '.h', '.hpp', '.cc', '.cxx', '.rs')):
            continue
        code = masked(original)
        for found in re.finditer(r'\b(Java_[A-Za-z0-9_]+)\s*\(', code):
            implementations.setdefault(found.group(1), []).append({'file': path, 'line': line(code, found.start()),
                                                                   'via': 'literal-jni-name'})
        for macro in re.finditer(r'^\s*#define\s+(\w+)\((\w+)\)\s+(Java_\w+_)\s*##\s*\2\b', code, re.M):
            for call in re.finditer(r'\b' + re.escape(macro.group(1)) + r'\(\s*(\w+)\s*\)\s*\(', code):
                implementations.setdefault(macro.group(3) + call.group(1), []).append(
                    {'file': path, 'line': line(code, call.start()), 'via': 'explicit-jni-token-paste',
                     'macro_line': line(code, macro.start())})
        if 'RegisterNatives' in code:
            records.append({'kind': 'JNI dynamic registration', 'file': path, 'line': line(code, code.index('RegisterNatives')),
                            'status': 'unresolved-registration', 'fact': 'extracted', 'confidence': 'medium'})
        if path.endswith('.rs'):
            for found in re.finditer(r'#\[\s*uniffi::export\s*(?:\([^\]]*\))?\s*\]', code):
                records.append({'kind': 'UniFFI export surface', 'file': path, 'line': line(code, found.start()),
                                'status': 'unresolved-generated-binding', 'fact': 'extracted', 'confidence': 'medium'})
    jvm_code = {p: masked(t) for p, t in texts.items() if p.endswith(('.kt', '.java'))}
    jvm_lines = {p: code.splitlines() for p, code in jvm_code.items()}
    for symbol in symbols:
        path = symbol['file']
        if not path.endswith(('.kt', '.java')) or path not in texts or not symbol.get('owner'):
            continue
        code = jvm_code[path];lines = jvm_lines[path];number = symbol.get('line') or 0
        if not number or number > len(lines) or not re.search(r'\b(external|native)\b', lines[number - 1]):
            continue
        package = re.search(r'^\s*package\s+([\w.]+)', code, re.M)
        if not package:
            continue
        # Standard JNI escaping: underscores are encoded, package separators are not.
        escaped = lambda value: value.replace('_', '_1').replace('.', '_')
        name = 'Java_' + escaped(package.group(1)) + '_' + escaped(symbol['owner']) + '_' + escaped(symbol['name'])
        candidates = implementations.get(name, [])
        records.append({'kind': 'JNI declaration', 'name': symbol['name'], 'file': path, 'line': number,
                        'jni_name': name, 'candidates': candidates,
                        'status': 'binding-candidate' if candidates else 'unresolved-native-implementation',
                        'fact': 'inferred' if candidates else 'extracted', 'confidence': 'medium',
                        'limit': 'Verify overload signature, enclosing JVM class and library loading before claiming runtime linkage'})
    return records
