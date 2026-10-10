#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Single rebuild/preflight for every external and owned test input."""
import csv,hashlib,os,pathlib,subprocess,sys
REPO=pathlib.Path(__file__).resolve().parent.parent
ROOT=pathlib.Path(os.environ.get('SISTRUM_ORACLE_DIR',pathlib.Path.home()/'.cache/sistrum-waxflow-oracle')).expanduser().resolve()
MANIFEST=REPO/'docs/waxflow/owned-manifest.tsv'
REBUILD='bash scripts/waxflow-oracle.sh --fetch-generate'
def run(script,*args):
    command=[sys.executable,str(REPO/script),*args]
    if '--verify' in args:
        result=subprocess.run(command,cwd=REPO,capture_output=True,text=True)
        if result.returncode:raise ValueError((result.stderr or result.stdout).strip().splitlines()[-1])
        print(result.stdout,end='')
    else:subprocess.run(command,check=True,cwd=REPO)
def digest(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def records():
    result=[]
    def add(path,command,sha):result.append({'path':path,'generator':command,'sha256':sha})
    for r in csv.DictReader(open(REPO/'docs/waxflow/corpus-manifest.tsv'),delimiter='\t'):
        if r['source'].startswith('python3 '):add('corpus/'+r['path'],r['source'].replace('{corpus}','{owned}/corpus'),r['sha256'])
    for r in csv.DictReader(open(REPO/'docs/waxflow/dsd-corpus-manifest.tsv'),delimiter='\t'):
        add('dsd/'+r['path'],r['source'].replace('{corpus}','{owned}/dsd'),r['sha256'])
    for r in csv.DictReader(open(REPO/'docs/wavpack/corpus-manifest.tsv'),delimiter='\t'):
        add('wavpack/generated/'+r['path'],r['generator']+'; '+r['encoder_command'],r['sha256'])
    for p in sorted((ROOT/'owned/wavpack/signals').glob('*')):
        if p.is_file():add('wavpack/signals/'+p.name,'python3 scripts/wavpack-oracle.py --generate',digest(p))
    for p in sorted((ROOT/'owned/wavpack').glob('extended-max-width.*')):
        add('wavpack/'+p.name,'python3 codecs/tools/generate-max-width.py',digest(p))
    return sorted(result,key=lambda r:r['path'])
def verify_owned():
    rows=list(csv.DictReader(MANIFEST.open(),delimiter='\t'))
    expected={r['path']:r['sha256'] for r in rows}
    if len(expected)!=len(rows):raise ValueError('Duplicate owned manifest paths')
    actual={str(p.relative_to(ROOT/'owned')):digest(p) for p in (ROOT/'owned').rglob('*') if p.is_file()}
    if actual!=expected:
        bad=sorted(k for k in actual.keys()|expected.keys() if actual.get(k)!=expected.get(k))
        raise ValueError('Missing, changed or unexpected owned input: '+', '.join(bad[:8]))
    print(f'Verified {len(rows)} owned inputs')
def main():
    if ROOT.is_relative_to(REPO):raise ValueError('Oracle directory must be outside the repository')
    mode=sys.argv[1]
    if mode=='--fetch-generate':
        run('scripts/waxflow-corpus.py','--fetch-generate')
        run('scripts/dsd-oracle.py','--fetch-generate')
        run('scripts/wavpack-oracle.py','--generate')
        run('codecs/tools/generate-max-width.py')
    elif mode=='--record-manifest':
        with MANIFEST.open('w') as f:
            w=csv.DictWriter(f,fieldnames=['path','generator','sha256'],delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(records())
        return
    elif mode!='--verify':raise ValueError('Use --fetch-generate or --verify')
    # Child diagnostics are captured so failures give one actionable message.
    run('scripts/waxflow-corpus.py','--verify',*sys.argv[2:4])
    run('scripts/dsd-oracle.py','--verify',*sys.argv[4:5])
    run('scripts/wavpack-oracle.py','--verify')
    verify_owned()
if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.CalledProcessError) as e:
        print(f'Oracle corpus missing or invalid. Rebuild with: {REBUILD}\n{e}',file=sys.stderr);sys.exit(1)
