#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Persistent, pinned DSD oracle bootstrap/rebuild and test preflight."""
import csv,hashlib,os,pathlib,subprocess,sys
REPO=pathlib.Path(__file__).resolve().parent.parent
ROOT=pathlib.Path(os.environ.get('SISTRUM_ORACLE_DIR',pathlib.Path.home()/'.cache/sistrum-waxflow-oracle')).expanduser().resolve()/'dsd'
MANIFEST=REPO/'docs/waxflow/dsd-corpus-manifest.tsv'
FIXTURES=REPO/'androidApp/src/test/resources/dsd/oracle-fixtures.tsv'
PIN='79da4ed76557c8ddf534e898480dde66bcc90334'
FILES={'mod.rs':'92e791d131d1c361bc3ac1a37af63e995706f9852bb812647eaf9a2a2e178b79','coefficients.rs':'4e5c708a2225ce5a3c0970b1d8716356dfc4b31c9d1444c9faa9593b26f10726','dop.rs':'878bf8be389e7c225993e4e807dd1458bd4c78333fb55fcab4ae275f68b8de65'}
REBUILD='bash scripts/dsd-oracle.sh --fetch-generate'
def run(*args,**kwargs):return subprocess.run([str(x)for x in args],check=True,**kwargs)
def sha(path):
    with path.open('rb')as f:return hashlib.file_digest(f,'sha256').hexdigest()
def outside():
    if ROOT.is_relative_to(REPO):raise ValueError('DSD oracle directory must remain outside the repo')
def verify(corpus=None):
    corpus=pathlib.Path(corpus)if corpus else ROOT/'corpus'
    with MANIFEST.open()as f:rows=list(csv.DictReader(f,delimiter='\t'))
    with FIXTURES.open()as f:fixtures=list(csv.DictReader(f,delimiter='\t'))
    expected={r['file']:r['file_sha256']for r in fixtures}
    if {r['path']:r['sha256']for r in rows}!=expected:raise ValueError('DSD manifest and oracle source hashes differ')
    for r in rows:
        rel=pathlib.Path(r['path'])
        if rel.is_absolute()or '..'in rel.parts:raise ValueError('Invalid DSD path')
        p=corpus/rel
        if not p.is_file()or sha(p)!=r['sha256']:raise ValueError('Missing or changed DSD corpus: '+r['path'])
    print(f'Verified {len(rows)} DSD corpus files')
def build():
    outside();ROOT.mkdir(parents=True,exist_ok=True);src=ROOT/'sources/Flick'
    if not(src/'.git').is_dir():run('git','clone','--no-checkout','https://github.com/moss-apps/Flick.git',src)
    if subprocess.check_output(['git','-C',str(src),'status','--porcelain','--untracked-files=no']).strip():raise ValueError('Flick clone has local changes')
    run('git','-C',src,'fetch','--quiet','origin',PIN);run('git','-C',src,'checkout','--quiet','--detach',PIN)
    for name,digest in FILES.items():
        if sha(src/'rust/src/audio/dsd_engine/dsd'/name)!=digest:raise ValueError('Pinned Flick source mismatch: '+name)
    env=dict(os.environ,RUSTUP_HOME=str(ROOT/'rustup'),CARGO_HOME=str(ROOT/'cargo'),CARGO_TARGET_DIR=str(ROOT/'target'),SISTRUM_FLICK_SOURCE=str(src))
    cargo=ROOT/'cargo/bin/cargo'
    if not cargo.exists():
        init=ROOT/'rustup-init';run('curl','--fail','--max-time','60','--retry','2','-sSL','https://static.rust-lang.org/rustup/dist/x86_64-unknown-linux-gnu/rustup-init','-o',init)
        if sha(init)!='dda7234360b7f578ca8b0ddcb80145646fa61a67c1720a5abc7051b35c9fcb71':raise ValueError('Rustup bootstrap checksum changed; install Rust 1.90.0 into this cache explicitly')
        init.chmod(0o755);run(init,'-y','--no-modify-path','--profile','minimal','--default-toolchain','1.90.0',env=env)
    run(cargo,'+1.90.0','build','--locked','--release','--manifest-path',REPO/'scripts/dsd-oracle/Cargo.toml',env=env)
    return ROOT/'target/release/sistrum-dsd-oracle',env
def rebuild():
    binary,_=build();run(sys.executable,REPO/'scripts/dsd-oracle/generate.py',ROOT/'corpus');verify()
    generated=subprocess.check_output([str(binary),str(ROOT/'corpus')])
    if generated!=FIXTURES.read_bytes():raise ValueError('Rust oracle PCM/DoP fixtures changed; review required, expectations were not replaced')
    print('Rust PCM hashes, stream info and DoP window all match committed fixtures')
if __name__=='__main__':
    try:
        mode=sys.argv[1]if len(sys.argv)>1 else '--fetch-generate'
        if mode=='--verify':verify(sys.argv[2]if len(sys.argv)>2 else None)
        elif mode=='--fetch-generate':rebuild()
        elif mode=='--test':
            binary,env=build();run(ROOT/'cargo/bin/cargo','+1.90.0','test','--locked','--release','--manifest-path',REPO/'scripts/dsd-oracle/Cargo.toml',env=env)
        else:raise ValueError('Usage: dsd-oracle.sh --fetch-generate | --verify | --test')
    except (ValueError,OSError,subprocess.CalledProcessError)as e:
        print(f'DSD oracle unavailable: {e}. Rebuild with: {REBUILD}',file=sys.stderr);sys.exit(1)
