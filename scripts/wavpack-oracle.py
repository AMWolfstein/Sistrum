#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Original corpus tooling. Generated signals are CC0-1.0.
"""Build pinned upstream tools, generate owned vectors, hash raw wvunpack output."""
import argparse, csv, hashlib, importlib.util, json, os, pathlib, shutil, struct, subprocess
REPO = pathlib.Path(__file__).resolve().parents[1]
ROOT = pathlib.Path.home()/'.cache/sistrum-wavpack-oracle'
TAG = '5.8.1'
PIN = '4827b9889665b937b6ed71b9c6c0123152cd7a02'
VECTORS = REPO/'codecs/src/test/resources/wavpack/generated'
MANIFEST = REPO/'docs/wavpack/corpus-manifest.tsv'
FIXTURES = REPO/'codecs/src/test/resources/wavpack/libwavpack.tsv'
def run(*args): subprocess.run([str(x) for x in args], check=True)
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def build():
    src=ROOT/'src'; build=ROOT/'build'; ROOT.mkdir(parents=True,exist_ok=True)
    if not src.exists(): run('git','clone','--quiet','--depth','1','--branch',TAG,'https://github.com/dbry/WavPack.git',src)
    if subprocess.check_output(['git','-C',str(src),'rev-parse','HEAD'],text=True).strip()!=PIN: raise ValueError('libwavpack pin mismatch')
    if subprocess.check_output(['git','-C',str(src),'status','--porcelain','--untracked-files=no']).strip(): raise ValueError('Modified oracle source')
    run('cmake','-S',src,'-B',build,'-DCMAKE_BUILD_TYPE=Release','-DWAVPACK_BUILD_PROGRAMS=ON')
    run('cmake','--build',build,'-j4')
    return build

def generate(build):
    spec=importlib.util.spec_from_file_location('dsdgen',REPO/'scripts/dsd-oracle/generate.py')
    gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
    table=gen.sine_table(); work=ROOT/'signals';work.mkdir(exist_ok=True);VECTORS.mkdir(parents=True,exist_ok=True)
    entries=[]
    def encode(name,source,options,signal):
        dst=VECTORS/(name+'.wv')
        args=['-q','-y','--threads=1','--blocksize=512',*options,str(source),'-o',str(dst)]
        run(build/'wavpack',*args)
        command='wavpack '+ ' '.join(x.replace(str(work),'{signals}').replace(str(VECTORS),'{vectors}') for x in args)
        for p in [dst,dst.with_suffix('.wvc')]:
            if p.exists(): entries.append([p.name,'python3 scripts/wavpack-oracle.py --generate',signal,command,'CC0-1.0',sha(p)])
    for kind in ['sine','sweep','noise','silence']:
        data=bytearray();seed=0x12345678
        for i in range(4096):
            for ch in range(2):
                seed=(1664525*seed+1013904223)&0xffffffff
                v=0 if kind=='silence' else ((seed>>16)-32768 if kind=='noise' else table[((i*(31+ch*7)+(i*i//1024 if kind=='sweep' else 0)))&1023]//1024)
                data+=struct.pack('<h',v)
        src=work/(kind+'.raw');src.write_bytes(data)
        for bitrate in ['2','3.5','6']:
            for correction in [False,True]:
                encode(f'{kind}-b{bitrate}'+('-c' if correction else ''),src,[f'-b{bitrate}',*(['-c'] if correction else []),'--raw-pcm=44100,16,2'],kind)
    special=[0x00000000,0x80000000,0x00000001,0x80000001,0x007fffff,0x00800000,
             0x3f800000,0xbf800000,0x7f800000,0xff800000,0x7fc12345,0x7f812345,0xffc12345]
    src=work/'float.raw'
    src.write_bytes(b''.join(struct.pack('<I',special[(i//13)%len(special)]) if i%13==0 else struct.pack('<f',table[(i*31)&1023]/(1<<24)) for i in range(8192)))
    for name,options in [('float32',[]),('float32-b6',['-b6']),('float32-b6-c',['-b6','-c'])]:
        encode(name,src,[*options,'--raw-pcm=44100,32f,2'],'sine + signed zero/subnormals/infinity/NaN payloads')
    for ch,mask in [(3,7),(6,63),(8,0x63f)]:
        payload=b''.join(struct.pack('<h',table[(i*(31+c*7))&1023]//1024) for i in range(4096) for c in range(ch))
        fmt=struct.pack('<HHIIHHHHI',0xfffe,ch,44100,44100*ch*2,ch*2,16,22,16,mask)+bytes.fromhex('0100000000001000800000aa00389b71')
        body=b'WAVEfmt '+struct.pack('<I',len(fmt))+fmt+b'data'+struct.pack('<I',len(payload))+payload
        src=work/f'{ch}ch.wav';src.write_bytes(b'RIFF'+struct.pack('<I',len(body))+body)
        encode(f'{ch}ch-mask{mask:x}',src,[],f'{ch} independent sines; channel mask 0x{mask:x}')
        encode(f'{ch}ch-mask{mask:x}-b3-c',src,['-b3','-c','--cross-decorr'],f'{ch} independent sines; channel mask 0x{mask:x}')
    ch=6;mask=63
    payload=b''.join(struct.pack('<f',table[(i*(31+c*7))&1023]/(1<<24)) for i in range(4096) for c in range(ch))
    fmt=struct.pack('<HHIIHHHHI',0xfffe,ch,44100,44100*ch*4,ch*4,32,22,32,mask)+bytes.fromhex('0300000000001000800000aa00389b71')
    body=b'WAVEfmt '+struct.pack('<I',len(fmt))+fmt+b'data'+struct.pack('<I',len(payload))+payload
    src=work/'6ch-float.wav';src.write_bytes(b'RIFF'+struct.pack('<I',len(body))+body)
    encode('6ch-float-mask3f',src,[],'6 float sines; channel mask 0x3f')
    planes=gen.signal(2822400,2,4096,table)
    for ext in ['dsf','dff']:
        src=work/('owned.'+ext);src.write_bytes(getattr(gen,ext)(2822400,planes))
        for quality in ['', '-h']:
            encode('dsd-'+ext+('-high' if quality else ''),src,[quality] if quality else [],'scripts/dsd-oracle/generate.py signal + '+ext)
    if sum(p.stat().st_size for p in VECTORS.iterdir())>=5_000_000: raise ValueError('Owned vectors exceed 5 MB: use test-time generation')
    MANIFEST.parent.mkdir(parents=True,exist_ok=True)
    with MANIFEST.open('w') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(['path','generator','signal','encoder_command','license','sha256']);w.writerows(entries)

def oracle(build):
    external=pathlib.Path(os.environ.get('SISTRUM_ORACLE_DIR',pathlib.Path.home()/'.cache/sistrum-waxflow-oracle'))/'corpus'
    paths=[('generated/'+p.name,p) for p in sorted(VECTORS.glob('*.wv'))]
    paths += [(str(p.relative_to(external)),p) for p in sorted(external.rglob('*.wv'))]
    refs=ROOT/'raw';refs.mkdir(exist_ok=True); rows=[]
    for name,p in paths:
        companion=p.with_suffix('.wvc')
        if not companion.exists():companion=p.parent/'wvc_files'/p.with_suffix('.wvc').name
        for correction in [False,True] if companion.exists() else [False]:
            input_path=p
            if correction and companion.parent!=p.parent:
                stage=ROOT/'staged'/hashlib.sha256(name.encode()).hexdigest();stage.mkdir(parents=True,exist_ok=True)
                input_path=stage/p.name
                for link,target in [(input_path,p),(input_path.with_suffix('.wvc'),companion)]:
                    if link.is_symlink():link.unlink()
                    link.symlink_to(target)
            cmd=[str(build/'wvunpack'),'-q','-y','--threads=1','--raw','-b',*([] if correction else ['-i']),str(input_path),'-o','-']
            ref=hashlib.sha256((name+str(correction)).encode()).hexdigest()+'.raw'
            with (refs/ref).open('wb') as output:result=subprocess.run(cmd,stdout=output,stderr=subprocess.PIPE)
            info=subprocess.run([str(build/'wvunpack'),'-f',str(p)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True).stdout.strip()
            # Keep the exact tool diagnostic and exit status for damaged/legacy inputs.
            rows.append([name,sha(p),str(correction).lower(),str(companion.relative_to(VECTORS)) if correction and name.startswith('generated/') else str(companion.relative_to(external)) if correction else '',sha(companion) if correction else '',result.returncode,(refs/ref).stat().st_size,sha(refs/ref),ref,';'.join(info.split(';')[:9]),result.stderr.decode(errors='replace').strip().replace('\n',' | ') or '-'])
    with FIXTURES.open('w') as f:
        f.write('# libwavpack_tag\t'+TAG+'\n# libwavpack_commit\t'+PIN+'\n')
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(['file','sha256','correction','correction_file','correction_sha256','exit','raw_bytes','raw_sha256','reference','info','error']);w.writerows(rows)
    print('Oracle rows:',len(rows),'owned vector bytes:',sum(p.stat().st_size for p in VECTORS.iterdir()))
def verify():
    with MANIFEST.open() as f:
        rows=list(csv.DictReader(f,delimiter='\t'))
    expected={r['path']:r['sha256'] for r in rows}
    actual={p.name:sha(p) for p in VECTORS.iterdir() if p.is_file()}
    if expected!=actual:raise ValueError('Owned WavPack corpus drift; regenerate with pinned encoder')
    if sum(p.stat().st_size for p in VECTORS.iterdir())>=5_000_000:raise ValueError('Owned corpus exceeds 5 MB')
    print('Verified owned WavPack vectors:',len(rows))

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('--generate',action='store_true');ap.add_argument('--oracle',action='store_true');ap.add_argument('--verify',action='store_true');a=ap.parse_args()
    if a.verify:verify()
    b=build() if a.generate or a.oracle else None
    if a.generate:generate(b)
    if a.oracle:oracle(b)
