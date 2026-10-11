#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Owned multichannel DFF/DSF pairs and pinned libwavpack DFF header oracle.
Audio, encoded files and tool output stay in the external cache. Existing Flick
fixtures are immutable: this extension has its own source-hash manifest.
"""
import argparse,csv,hashlib,importlib.util,json,os,pathlib,struct,subprocess
REPO=pathlib.Path(__file__).resolve().parent.parent
CACHE=pathlib.Path(os.environ.get('SISTRUM_ORACLE_DIR',pathlib.Path.home()/'.cache/sistrum-waxflow-oracle')).expanduser().resolve()
ROOT=CACHE/'owned/dsd-multichannel'
MANIFEST=REPO/'docs/waxflow/dsd-multichannel-manifest.tsv'
PIN='4827b9889665b937b6ed71b9c6c0123152cd7a02'
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def generate():
    if ROOT.is_relative_to(REPO):raise ValueError('Owned audio must remain outside the repository')
    spec=importlib.util.spec_from_file_location('dsdgen',REPO/'scripts/dsd-oracle/generate.py')
    gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
    ROOT.mkdir(parents=True,exist_ok=True);table=gen.sine_table();rows=[]
    source=CACHE/'wavpack/src';tools=CACHE/'wavpack/build'
    if subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()!=PIN:raise ValueError('libwavpack pin mismatch')
    if subprocess.check_output(['git','-C',str(source),'status','--porcelain','--untracked-files=no']).strip():raise ValueError('Modified libwavpack oracle source')
    def pair(name,rate,planes,ids=None,order=None):
        count=len(planes);order=list(range(count)) if order is None else order
        dsf=ROOT/(name+'.dsf');dff=ROOT/(name+'.dff')
        dsf.write_bytes(gen.dsf(rate,planes));dff.write_bytes(gen.dff(rate,[planes[i] for i in order],ids=ids))
        encoded=ROOT/(name+'.wv')
        result=subprocess.run([str(tools/'wavpack'),'-q','-y','--threads=1','--blocksize=511',str(dff),'-o',str(encoded)],capture_output=True,text=True)
        row={'name':name,'rate':rate,'channels':count,'order':order,'libwavpack_commit':PIN,'accepted':result.returncode==0}
        if result.returncode==0:
            info=subprocess.check_output([str(tools/'wvunpack'),'-f',str(encoded)],text=True).strip().split(';')
            row.update(oracle_rate=int(info[0]),oracle_channels=int(info[3]),oracle_mask=int(info[4],0),oracle_bits=int(info[5]))
            # Verify that the DFF reader passes through the declared physical channel order.
            raw=subprocess.check_output([str(tools/'wvunpack'),'-q','--raw','-b',str(encoded),'-o','-'])
            expected=bytes(planes[c][i] for i in range(len(planes[0])) for c in order)
            if raw!=expected:raise ValueError('libwavpack DFF raw-bit oracle mismatch: '+name)
            row['raw_sha256']=hashlib.sha256(raw).hexdigest()
        else:
            row['reason']=result.stderr.strip()
            if encoded.exists():encoded.unlink()
        rows.append(row)
    for mult in [64,128,256]:
        for count in [3,5,6]:
            rate=44100*mult;planes=gen.signal(rate,count,rate//160+3,table)
            pair(f'dsd{mult}-{count}ch',rate,planes)
        planes=gen.signal(44100*mult,6,(44100*mult)//160+3,table)
        pair(f'dsd{mult}-6ch-reordered',44100*mult,planes,
             [b'RS  ',b'LFE ',b'MRGT',b'LS  ',b'MLFT',b'C   '],[5,3,1,4,0,2])
    planes=gen.signal(2822400,6,17643,table)
    pair('generic-6ch',2822400,planes,[b'c000',b'c001',b'c123',b'c500',b'c998',b'c999'])
    pair('mixed-3ch',2822400,planes[:3],[b'c123',b'SRGT',b'SLFT'],[2,1,0])
    (ROOT/'headers.json').write_text(json.dumps(rows,indent=2,sort_keys=True)+'\n')
    fields=['name','rate','channels','order','libwavpack_commit','accepted','oracle_rate','oracle_channels','oracle_mask','oracle_bits','raw_sha256']
    with (ROOT/'headers.tsv').open('w') as f:
        writer=csv.DictWriter(f,fieldnames=fields,delimiter='\t',lineterminator='\n',extrasaction='ignore');writer.writeheader()
        writer.writerows(dict(row,order=','.join(map(str,row['order']))) for row in rows)
    base=planes[:3]
    invalid={
        'missing-chnl':dict(include_chnl=False),
        'invalid-id':dict(ids=[b'MLFT',b'MRGT',b'NOPE']),
        'invalid-generic':dict(ids=[b'MLFT',b'MRGT',b'c00x']),
        'duplicate-id':dict(ids=[b'MLFT',b'MRGT',b'MLFT']),
        'duplicate-alias':dict(ids=[b'MLFT',b'MRGT',b'SLFT']),
        'missing-id':dict(ids=[b'MLFT',b'MRGT']),
        'extra-id':dict(ids=[b'MLFT',b'MRGT',b'C   ',b'LFE ']),
        'zero-channels':dict(chnl_payload=struct.pack('>H',0)),
        'seven-channels':dict(chnl_payload=struct.pack('>H',7)+b'c000'*7),
        'dst':dict(dst=True),
    }
    for name,options in invalid.items():(ROOT/(name+'.dff')).write_bytes(gen.dff(2822400,base,**options))
    print(f'Generated {len(rows)} multichannel DSF/DFF pairs; pinned libwavpack accepted {sum(r["accepted"] for r in rows)}')
def record():
    with MANIFEST.open('w') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(['path','sha256'])
        w.writerows((p.name,sha(p)) for p in sorted(ROOT.iterdir()) if p.is_file())
def verify():
    expected={r['path']:r['sha256'] for r in csv.DictReader(MANIFEST.open(),delimiter='\t')}
    actual={p.name:sha(p) for p in ROOT.iterdir() if p.is_file()}
    if expected!=actual:raise ValueError('Multichannel DFF corpus drift; rebuild with scripts/dsd-multichannel-oracle.py --generate')
    print(f'Verified {len(actual)} owned multichannel DFF inputs and pinned header results')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--generate',action='store_true');parser.add_argument('--record-manifest',action='store_true');parser.add_argument('--verify',action='store_true');args=parser.parse_args()
    if args.generate:generate()
    if args.record_manifest:record()
    if args.verify or args.generate:verify()
