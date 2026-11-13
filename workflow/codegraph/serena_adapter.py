#!/usr/bin/env python3
"""Allowlisted semantic queries through Serena 2.1.0's MCP interface."""
from __future__ import annotations
import argparse
import asyncio
from importlib import metadata
import json
import os
from pathlib import Path
import sys

PINS = {'serena-agent': '2.1.0', 'mcp': '2.2.0'}
LANGUAGES = {'.dart':'dart', '.rs':'rust', '.kt':'kotlin', '.java':'java',
             '.cpp':'cpp', '.cc':'cpp', '.c':'cpp', '.h':'cpp', '.hpp':'cpp', '.py':'python', '.ts':'typescript', '.js':'typescript'}
TOOLS = {'symbols':'get_symbols_overview', 'definition':'find_symbol', 'references':'find_referencing_symbols'}


def versions():
    result = {}
    for package in PINS:
        try: result[package] = metadata.version(package)
        except metadata.PackageNotFoundError: result[package] = None
    return {'available': result == PINS, 'packages': result}


def arguments(operation, file, symbol):
    if operation not in TOOLS: raise ValueError('Only read operations are supported')
    path = Path(file or '')
    if not file or path.is_absolute() or '..' in path.parts:
        raise ValueError('A repository-relative --file is required')
    if path.suffix not in LANGUAGES: raise ValueError('Unsupported semantic-query language')
    if operation != 'symbols' and not symbol: raise ValueError('--symbol is required')
    if operation == 'symbols': return {'relative_path':file,'depth':1,'max_answer_chars':24000}
    if operation == 'definition': return {'relative_path':file,'name_path_pattern':symbol,'include_body':True,'max_answer_chars':24000}
    return {'relative_path':file,'name_path':symbol,'max_answer_chars':24000}


def prepare_home(home):
    home.mkdir(parents=True, exist_ok=True)
    config = home / 'serena_config.yml'
    if not config.exists():
        settings = {'projects': [], 'ls_specific_settings': {
            'dart': {'dart_sdk_version': os.environ.get('SERENA_DART_SDK_VERSION', '3.13.7')},
            'rust': {'initializationOptions': {'cargo': {'buildScripts': {'enable': False}, 'autoreload': False},
                                                'procMacro': {'enable': False}, 'checkOnSave': False}}}}
        with config.open('x') as output:
            json.dump(settings, output)
        config.chmod(0o600)


async def run(args):
    from mcp import ClientSession, StdioServerParameters
    from mcp.client.stdio import stdio_client
    kwargs = arguments(args.operation, args.file, args.symbol)
    project = args.project.resolve()
    # Restrict writes by the language server to the private source mirror, never the checkout.
    if project.name != 'source' or project.parent.name != 'index' or project.parent.parent.name != 'codegraph' or project.parent.parent.parent.name != '.work':
        raise ValueError('Serena requires the .work/codegraph/index/source mirror produced by update')
    file = project / args.file
    if not file.is_file() or file.is_symlink() or not file.resolve().is_relative_to(project):
        raise ValueError('Query file is missing from the indexed snapshot or is a symlink')
    config = project / '.serena'
    config.mkdir(exist_ok=True)
    # JSON is a YAML subset; use no application config or activation commands.
    (config / 'project.yml').write_text(json.dumps({'project_name':'code-tools',
        'language_servers':[LANGUAGES[file.suffix]], 'read_only':True,
        'ignored_paths':['.codegraph', '.git', 'build', 'target', 'vendor'],
        'ls_workspace_folders':['.'], 'initial_prompt':''}))
    home = Path(os.environ.get('SERENA_HOME', str(project.parents[2] / 'serena/home'))).resolve()
    prepare_home(home)
    argv = ['-c','from serena.cli import top_level; top_level()','start-mcp-server','--project',str(project),
            '--context','ide-assistant','--mode','planning','--agent-interface','tools',
            '--language-backend','LSP','--enable-web-dashboard','false','--enable-gui-log-window','false']
    parameters = StdioServerParameters(command=sys.executable, args=argv,
        env={**os.environ, 'SERENA_HOME':str(home)})
    log = home / 'code-tools-stderr.log'
    with log.open('w') as errors:
        async with stdio_client(parameters, errlog=errors) as (read, write):
            async with ClientSession(read, write, read_timeout_seconds=args.timeout) as session:
                await session.initialize()
                tools = {t.name:t for t in (await session.list_tools()).tools}
                tool = TOOLS[args.operation]
                if tool not in tools: raise RuntimeError(f'Serena does not expose {tool}')
                schema = tools[tool].input_schema
                if set(kwargs) - set(schema.get('properties', {})):
                    raise RuntimeError('Serena schema differs from the supported version')
                result = await session.call_tool(tool, kwargs)
                return {'engine':'serena-agent', 'version':PINS['serena-agent'], 'tool':tool,
                        'snapshot':str(project), 'is_error':bool(result.is_error),
                        'content':[c.model_dump(mode='json') for c in result.content], 'log':str(log)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=[*TOOLS,'version'])
    parser.add_argument('--project', type=Path, required=True)
    parser.add_argument('--file');parser.add_argument('--symbol')
    parser.add_argument('--timeout', type=float, default=120)
    args = parser.parse_args()
    info = versions()
    if args.operation == 'version':
        print(json.dumps(info));return 0 if info['available'] else 1
    if not info['available']:
        print(json.dumps({'error':'Install workflow/codegraph/requirements-serena.txt in a separate venv', **info}));return 1
    if args.timeout <= 0: parser.error('--timeout must be positive')
    try:
        result = asyncio.run(asyncio.wait_for(run(args), timeout=args.timeout))
        print(json.dumps(result, indent=2));return 1 if result['is_error'] else 0
    except Exception as error:
        import traceback
        traceback.print_exception(error, file=sys.stderr)
        print(json.dumps({'engine':'serena-agent', 'is_error':True, 'error':str(error)}));return 1

if __name__ == '__main__':
    raise SystemExit(main())
