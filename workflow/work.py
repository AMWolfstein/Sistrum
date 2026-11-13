#!/usr/bin/env python3
"""Portable workflow helpers: status/doctor/setup, identity reservations, derived history and validation."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import registry


def parser():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    sub=p.add_subparsers(dest='command',required=True)
    init=sub.add_parser('init');init.add_argument('--migrate',action='store_true')
    mission=sub.add_parser('mission');m=mission.add_subparsers(dest='action',required=True).add_parser('new')
    m.add_argument('--title',required=True);m.add_argument('--path',required=True)
    task=sub.add_parser('task');ts=task.add_subparsers(dest='action',required=True)
    reserve=ts.add_parser('reserve');reserve.add_argument('--title',required=True);reserve.add_argument('--mission');reserve.add_argument('--tor',action='store_true');reserve.add_argument('--final',action='store_true')
    complete=ts.add_parser('complete');complete.add_argument('id');complete.add_argument('--reviewer',required=True);complete.add_argument('--round',type=int,choices=[1,2,3],required=True);complete.add_argument('--checks',required=True)
    validate=sub.add_parser('validate');validate.add_argument('--base')
    check=sub.add_parser('commit-check',help='Enforce Conventional Commit subjects (type(scope): summary)')
    check.add_argument('--message');check.add_argument('--message-file',type=Path)
    check.add_argument('--base',help='Check BASE..HEAD instead of HEAD only')
    check.add_argument('--range',dest='revision',help='Explicit Git revision/range, e.g. BASE..HEAD')
    check.add_argument('--install-hook',action='store_true',
                       help='Write .git/hooks/commit-msg to enforce the subject locally')
    sub.add_parser('history')
    sub.add_parser('status')
    doctor=sub.add_parser('doctor');doctor.add_argument('--full',action='store_true');doctor.add_argument('--with-serena',action='store_true')
    setup=sub.add_parser('setup');setup.add_argument('--with-serena',action='store_true');setup.add_argument('--offline',action='store_true')
    quality=sub.add_parser('quality',help='Pass through to codegraph/code_tools.py (audit, security, bridge, tests, optimize, plan-review)')
    quality.add_argument('arguments',nargs=argparse.REMAINDER)
    return p


def main():
    p=parser();a=p.parse_args();root=a.root.resolve()
    try:
        if a.command=='quality':
            return subprocess.call([sys.executable,str(Path(__file__).with_name('codegraph')/'code_tools.py'),
                                    '--root',str(root),*a.arguments])
        if a.command in ('status','doctor','setup'):
            import environment
            if a.command=='status':result=environment.status(root)
            elif a.command=='doctor':result=environment.doctor(root,a.full,a.with_serena)
            else:result=environment.setup(root,a.with_serena,a.offline)
        elif a.command=='init':result=registry.initialize(root,a.migrate)
        elif a.command=='mission':result={'id':registry.new_mission(root,a.title,a.path)}
        elif a.command=='task' and a.action=='reserve':result=registry.reserve(root,a.title,a.mission,a.tor,a.final)
        elif a.command=='task':result=registry.complete(root,a.id,a.reviewer,a.round,a.checks)
        elif a.command=='validate':result=registry.validate(root,a.base)
        elif a.command=='commit-check':
            import commits
            if a.install_hook:
                result=commits.install_hook(root)
            else:
                revision=a.revision or (f'{a.base}..HEAD' if a.base else 'HEAD')
                result=commits.check(root,message=a.message,message_file=a.message_file,revision=revision)
        else:
            with registry.lock(root):state=registry.load(root);registry.save(root,state)
            result={'status':'PASS','history':registry.HISTORY}
        print(json.dumps(result));return 2 if result.get('status') in ('INCOMPLETE','FAIL') else 0
    except (ValueError,KeyError,TypeError,OSError,subprocess.CalledProcessError) as error:
        print(json.dumps({'status':'ERROR','error':str(error)}),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
