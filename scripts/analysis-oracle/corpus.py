#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate/verify owned float WAV inputs; keep all audio in the external cache."""
import argparse, array, hashlib, json, math, os, pathlib, struct, subprocess, sys
from build import CACHE, ROOT
CODECS=pathlib.Path(os.environ.get('SISTRUM_ORACLE_DIR',str(pathlib.Path.home()/'.cache/sistrum-waxflow-oracle')))
MANIFEST=ROOT/'docs/analysis/corpus-manifest.json'
FIXTURES=ROOT/'docs/analysis/oracle.json'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block)
 return h.hexdigest()
def wav(path,samples,rate,channels):
 data=array.array('f',samples)
 if sys.byteorder!='little':data.byteswap()
 b=data.tobytes(); path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'RIFF'+struct.pack('<I',len(b)+36)+b'WAVEfmt '+struct.pack('<IHHIIHH',16,3,channels,rate,rate*channels*4,channels*4,32)+b'data'+struct.pack('<I',len(b))+b)
def generated():
 for rate in [8000,16000,22050,44100,48000,96000]:
  for freq in [100,997, min(10000,rate//3)]:
   yield f'sine-{rate}-{freq}',rate,1,(0.6*math.sin(2*math.pi*freq*i/rate) for i in range(rate*6))
 for channels in [1,2,6,8]:
  yield f'dynamic-{channels}ch',48000,channels,((0.01 if i//48000%6<3 else 0.8)*math.sin(2*math.pi*(440+120*c)*i/48000) for i in range(48000*18) for c in range(channels))
 yield 'silence',44100,1,(0.0 for i in range(44100*6))
 yield 'clipped',44100,2,(max(-1,min(1,2*math.sin(2*math.pi*997*i/44100))) for i in range(44100*8) for c in range(2))
 yield 'sweep',48000,1,(0.7*math.sin(2*math.pi*(40*i/48000+19960*(i/48000)**2/20)) for i in range(48000*10))
 # Integer PRNG plus a fixed IIR pink-noise approximation; generator is CC0.
 def pink():
  seed=123456789; b0=b1=b2=0.0
  for i in range(44100*12):
   seed=(1664525*seed+1013904223)&0xffffffff; white=seed/2147483648-1
   b0=.99765*b0+white*.099046; b1=.963*b1+white*.2965164; b2=.57*b2+white*1.0526913
   yield (b0+b1+b2+white*.1848)*.05
 yield 'pink',44100,1,pink()
 for bpm in range(60,201,10):
  step=44100*60/bpm
  yield f'click-{bpm}',44100,1,(0.8*math.exp(-(i%step)/120)*math.sin(2*math.pi*1500*(i%step)/44100) if i%step<2000 else 0 for i in range(44100*30))
def generate():
 rows=[]
 for name,rate,ch,data in generated():
  dest=CACHE/'owned/generated'/(name+'.wav'); wav(dest,data,rate,ch)
  rows.append(dict(file=str(dest.relative_to(CACHE)),sha256=sha(dest),source='generator: scripts/analysis-oracle/corpus.py '+name,licence='CC0-1.0',sample_rate=rate,channels=ch))
 # Every logical codec corpus input, including refusal vectors, is inventoried.
 for src in sorted(p for base in ['corpus','owned/dsd','owned/wavpack'] for p in (CODECS/base).rglob('*')):
  if not src.is_file():continue
  rel=str(src.relative_to(CODECS)); dest=CACHE/'owned/decoded'/(src.relative_to(CODECS/'corpus') if rel.startswith('corpus/') else pathlib.Path(rel)); dest=dest.with_name(dest.name+'.wav');dest.parent.mkdir(parents=True,exist_ok=True)
  row=dict(source=rel,source_sha256=sha(src),licence='inherited: docs/waxflow/corpus-manifest.tsv')
  p=subprocess.run([str(CACHE/'install/bin/ffmpeg'),'-v','error','-y','-i',str(src),'-map','0:a:0','-c:a','pcm_f32le',str(dest)],capture_output=True,text=True)
  if p.returncode:
   row['decode_error']=p.stderr.strip()
   if dest.exists():dest.unlink()
  else:
   # PCM API has channel count, not a channel mask: canonical default layout.
   blob=dest.read_bytes();offset=12;fmt=None;data=None
   while offset+8<=len(blob):
    tag=blob[offset:offset+4];size=struct.unpack_from('<I',blob,offset+4)[0]
    if tag==b'fmt ':fmt=blob[offset+8:offset+8+size]
    if tag==b'data':data=blob[offset+8:offset+8+size]
    offset+=8+size+(size&1)
   assert fmt is not None and data is not None
   ch,rate=struct.unpack_from('<HI',fmt,2)
   dest.write_bytes(b'RIFF'+struct.pack('<I',len(data)+36)+b'WAVEfmt '+struct.pack('<IHHIIHH',16,3,ch,rate,rate*ch*4,ch*4,32)+b'data'+struct.pack('<I',len(data))+data)
   row.update(file=str(dest.relative_to(CACHE)),sha256=sha(dest),sample_rate=rate,channels=ch)
  rows.append(row)
 fixtures=[]
 for row in rows:
  if 'file' not in row:continue
  p=subprocess.run([str(CACHE/'analyze'),str(CACHE/row['file'])],capture_output=True,text=True)
  if p.returncode:row['analysis_error']=p.stderr.strip()
  else:
   result=json.loads(p.stdout);result['file']=row['file'];fixtures.append(result)
 MANIFEST.write_text(json.dumps(rows,indent=2)+'\n');FIXTURES.write_text(json.dumps(fixtures,indent=2)+'\n')
 print(f'{len(rows)} inputs; {len(fixtures)} analyzed; {sum("decode_error" in r for r in rows)} decode refusals; {sum("analysis_error" in r for r in rows)} analysis refusals')
def verify():
 for row in json.loads(MANIFEST.read_text()):
  if 'source_sha256' in row:
   p=CODECS/row['source']; assert p.is_file() and sha(p)==row['source_sha256'],str(p)
  if 'file' in row:
   p=CACHE/row['file'];assert p.is_file() and sha(p)==row['sha256'],str(p)
 print('Analysis corpus hashes verified')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--generate',action='store_true');p.add_argument('--verify',action='store_true');a=p.parse_args()
 if a.generate:generate()
 elif a.verify:verify()
 else:p.error('choose --generate or --verify')
