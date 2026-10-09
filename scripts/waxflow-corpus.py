#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Manifest-driven external corpus rebuild and validation. No expected hashes are generated."""
import csv
import hashlib
import os
import re
from pathlib import Path
import shlex
import subprocess
import sys
import zipfile

REPO = Path(__file__).resolve().parent.parent
ROOT = Path(os.environ.get('SISTRUM_ORACLE_DIR', Path.home()/'.cache/sistrum-waxflow-oracle')).expanduser().resolve()
MANIFEST = REPO/'docs/waxflow/corpus-manifest.tsv'
FIXTURES = REPO/'androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv'
REBUILD = 'bash scripts/waxflow-oracle.sh --fetch-generate'
PIN = re.search(r'^WAXFLOW_COMMIT="([0-9a-f]{40})"$',
                (REPO/'scripts/waxflow-oracle.sh').read_text(), re.MULTILINE).group(1)
SUITE_URL = 'https://www.rarewares.org/wavpack/test_suite.zip'
MAC_URL = 'https://monkeysaudio.com/files/MAC_1326_SDK.zip'


def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def rows():
    with MANIFEST.open() as f:
        result = list(csv.DictReader(f, delimiter='\t'))
    names = set()
    for r in result:
        p = Path(r['path'])
        if p.is_absolute() or '..' in p.parts or r['path'] in names:
            raise ValueError('Invalid or duplicate manifest path: '+r['path'])
        names.add(r['path'])
    with FIXTURES.open() as f:
        expected = {r['file']: r['file_sha256'] for r in csv.DictReader((l for l in f if not l.startswith('#')), delimiter='\t')}
    if {r['path']: r['sha256'] for r in result} != expected:
        raise ValueError('Manifest and oracle fixture source hashes differ')
    return result


def outside(path):
    if path.resolve().is_relative_to(REPO):
        raise ValueError('Oracle storage must be outside the repository: '+str(path))


def validate(corpus, packets=None):
    entries = rows()
    failures = []
    for r in entries:
        path = corpus/r['path']
        if not path.is_file() or sha(path) != r['sha256']:
            failures.append(r['path']+' <- '+r['source'])
    if failures:
        raise ValueError('Missing or changed corpus files:\n'+'\n'.join(failures))
    if packets is not None:
        with (REPO/'docs/waxflow/alac-packets-manifest.tsv').open() as f:
            expected_packets = {r['path']: r['sha256'] for r in csv.DictReader(f, delimiter='\t')}
        if set(expected_packets) != alac_names():
            raise ValueError('ALAC packet inventory differs from successful ALAC fixtures')
        hashes = {r['path']:r['sha256'] for r in entries}
        for name, digest in expected_packets.items():
            p = packets/name
            if not p.is_file() or sha(p) != digest:
                raise ValueError('Missing or changed ALAC packet dump: '+name)
            with p.open('rb') as f:
                if f.read(32).hex() != hashes[name.removesuffix('.packets')]:
                    raise ValueError('ALAC dump source hash differs: '+name)
    return len(entries)


def alac_names():
    with FIXTURES.open() as f:
        return {r['file']+'.packets' for r in csv.DictReader((l for l in f if not l.startswith('#')), delimiter='\t')
                if r['status']=='ok' and (r['file'].startswith('waxflow-alac-tests/') or r['file']=='waxflow-testdata/chapters.m4b')}


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def fetch(url, dest, digest=None):
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and (digest is None or sha(dest)==digest):
        return
    partial=dest.with_name(dest.name+'.download')
    run('curl','-fLsS','--retry','2',url,'-o',partial)
    if digest and sha(partial)!=digest:
        raise ValueError('Downloaded checksum mismatch: '+url)
    partial.replace(dest)


def source_clone():
    src=ROOT/'oracle-work/src'
    if not (src/'.git').is_dir():
        run('git','clone','--quiet','https://github.com/AMWolfstein/WaxFlow.git',src)
    if subprocess.check_output(['git','-C',str(src),'status','--porcelain','--untracked-files=no']).strip():
        raise ValueError('WaxFlow clone has local changes: '+str(src))
    run('git','-C',src,'fetch','--quiet','origin',PIN)
    run('git','-C',src,'checkout','--quiet','--detach',PIN)
    return src


def mac_encoder():
    archive=ROOT/'downloads/MAC_1326_SDK.zip'
    fetch(MAC_URL,archive,'3fdb516db15cc754eb2db1d255e405a8142fbb115eccdf51b0fa07b84305b6ac')
    folder=ROOT/'encoders/mac-1326'
    with zipfile.ZipFile(archive) as z:
        z.extractall(folder)
    run('cmake','-S',folder,'-B',folder/'build','-DCMAKE_BUILD_TYPE=Release')
    run('cmake','--build',folder/'build','-j2')
    return folder/'build/mac'


def rebuild():
    outside(ROOT)
    ROOT.mkdir(parents=True,exist_ok=True)
    entries=rows();corpus=ROOT/'corpus';corpus.mkdir(exist_ok=True)
    src=source_clone()
    generators={}
    for r in entries:
        dst=corpus/r['path'];source=r['source']
        if dst.is_file() and sha(dst)==r['sha256']:
            continue
        dst.parent.mkdir(parents=True,exist_ok=True)
        if source.startswith('python3 '):
            generators[source]=True
        elif source.startswith(SUITE_URL+'#'):
            archive=ROOT/'downloads/test_suite.zip'
            fetch(SUITE_URL,archive,'cfeee02f6f873f10da127603898546b03d9a1f7d3db1fbd0395b6c526696d675')
            with zipfile.ZipFile(archive) as z:
                dst.write_bytes(z.read(source.split('#',1)[1]))
        elif source.startswith('https://raw.githubusercontent.com/AMWolfstein/WaxFlow/'):
            revision,relative=source.split('/WaxFlow/',1)[1].split('/',1)
            dst.write_bytes(subprocess.check_output(['git','-C',str(src),'show',revision+':'+relative]))
        else:
            raise ValueError('Unrecognized manifest source: '+source)
    for command in generators:
        # Commands are a small explicit allowlist, never shell-evaluated.
        args=shlex.split(command)
        allowed={'scripts/waxflow-expand-lossless-corpus.py','scripts/waxflow-g711-corpus.py','scripts/waxflow-wma-corpus.py','scripts/waxflow-wmalossless-corpus.py'}
        if args[1] not in allowed:
            raise ValueError('Unrecognized generator: '+command)
        values={'{src}':str(src),'{corpus}':str(corpus)}
        if '{mac}' in args:
            values['{mac}']=str(mac_encoder())
        run(sys.executable,REPO/args[1],*(values[a] for a in args[2:]),cwd=REPO)
    count=validate(corpus)
    packets=ROOT/'alac-packets'
    env=dict(os.environ,SISTRUM_ORACLE_DIR=str(ROOT),SISTRUM_WAXFLOW_DIR=str(ROOT/'oracle-work'))
    run('bash',REPO/'scripts/waxflow-alac-packets.sh',corpus,packets,env=env)
    validate(corpus,packets)
    print(f'Verified {count} corpus files and {len(alac_names())} ALAC packet dumps at {ROOT}')



if __name__=='__main__':
    try:
        if sys.argv[1]=='--fetch-generate':
            rebuild()
        elif sys.argv[1]=='--verify':
            corpus=Path(sys.argv[2]) if len(sys.argv)>2 else ROOT/'corpus'
            packets=Path(sys.argv[3]) if len(sys.argv)>3 else ROOT/'alac-packets'
            print(f'Verified {validate(corpus,packets)} corpus files and ALAC packet dumps')
        else:
            raise ValueError('Use --fetch-generate or --verify')
    except (ValueError,OSError,KeyError,subprocess.CalledProcessError,zipfile.BadZipFile) as e:
        print(f'WaxFlow corpus unavailable or invalid. Rebuild with: {REBUILD}\n{e}',file=sys.stderr)
        sys.exit(2)
