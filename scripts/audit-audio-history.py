#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Prove a bounded filter-repo rewrite changed trees only by deleting audio."""
import argparse,pathlib,subprocess
import runpy
EXTENSIONS=runpy.run_path(str(pathlib.Path(__file__).with_name('check-no-audio.py')))['EXTENSIONS']

def git(*args):return subprocess.check_output(['git',*args],text=True)
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('old_tip');ap.add_argument('base');ap.add_argument('report');ap.add_argument('--map',default='.git/filter-repo/commit-map');a=ap.parse_args()
    mapping=dict(line.split() for line in pathlib.Path(a.map).read_text().splitlines()[1:])
    commits=git('rev-list',a.old_tip,'^'+a.base).splitlines();output=[];deletions=0
    for old in commits:
        new=mapping[old];assert new!='0'*40,old
        diff=git('diff','--name-status',old,new)
        for line in diff.splitlines():
            status,path=line.split('\t',1)
            assert status=='D' and pathlib.PurePath(path).suffix.lower().lstrip('.') in EXTENSIONS,(old,new,line)
            deletions+=1
        output.append(f'{old} -> {new}\n'+(diff or '(identical tree)\n'))
    pathlib.Path(a.report).write_text('\n'.join(output))
    print(f'History tree audit passed: {len(commits)} commits; {deletions} audio deletions across trees; no other tree differences. Diff: {a.report}')
