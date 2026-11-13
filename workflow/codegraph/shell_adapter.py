#!/usr/bin/env python3
"""Pinned Tree-sitter Bash worker: reads/parses inputs; never executes shell code."""
import argparse
import json
import re
import sys
from importlib import metadata
from pathlib import Path

PINS = {'tree-sitter': '0.25.2', 'tree-sitter-bash': '0.25.1'}


def version():
    packages = {}
    for name in PINS:
        try: packages[name] = metadata.version(name)
        except metadata.PackageNotFoundError: packages[name] = None
    return {'available': packages == PINS, 'packages': packages,
            'reason': '' if packages == PINS else 'Install workflow/codegraph/requirements.txt in the parser Python environment'}


def literal(node):
    if node is None: return None
    text = node.text.decode('utf-8', errors='replace')
    if node.type == 'word' and re.fullmatch(r'[A-Za-z0-9_./+-]+', text): return text
    if node.type == 'raw_string': return text[1:-1]
    if node.type == 'string' and all(child.type == 'string_content' for child in node.named_children): return text[1:-1]
    return None


def extract(request):
    from tree_sitter import Parser, Language
    import tree_sitter_bash
    parser = Parser(Language(tree_sitter_bash.language()))
    results = []
    for item in json.loads(request.read_text()):
        tree = parser.parse(Path(item['path']).read_bytes())
        functions, commands, errors = [], [], []
        def walk(node, owner=None):
            if node.type == 'ERROR' or node.is_missing:
                errors.append({'line': node.start_point.row + 1, 'message': 'Bash syntax error/missing node'})
            if node.type == 'function_definition':
                name = node.child_by_field_name('name')
                if name:
                    owner = name.text.decode()
                    functions.append({'name': owner, 'line': node.start_point.row + 1,
                                      'end_line': node.end_point.row + 1, 'top_level': node.parent.type == 'program'})
            if node.type == 'command':
                name = node.child_by_field_name('name')
                value = literal(name.named_children[0]) if name and len(name.named_children) == 1 else None
                commands.append({'name': value, 'expression': name.text.decode(errors='replace') if name else '',
                                 'line': node.start_point.row + 1, 'owner': owner,
                                 'args': [literal(n) for n in node.children_by_field_name('argument')],
                                 'dynamic': value is None})
            for child in node.named_children: walk(child, owner)
        walk(tree.root_node)
        if tree.root_node.has_error and not errors:
            errors.append({'line': tree.root_node.end_point.row + 1, 'message': 'Bash parser reports missing syntax'})
        results.append({'file': item['file'], 'functions': functions, 'commands': commands, 'errors': errors})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['version', 'extract']);parser.add_argument('--request', type=Path)
    args = parser.parse_args()
    info = version()
    if args.action == 'version': print(json.dumps(info));return 0
    if not info['available']: print(info['reason'], file=sys.stderr);return 2
    try: print(json.dumps(extract(args.request)));return 0
    except (OSError, ValueError, RecursionError) as error: print(str(error), file=sys.stderr);return 2


if __name__ == '__main__': raise SystemExit(main())
