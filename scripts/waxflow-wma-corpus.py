#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Original deterministic signals; generated audio is dedicated to CC0-1.0.
"""Generate external WMA v1/v2 corpus using FFmpeg only as a black-box encoder.
Usage: waxflow-wma-corpus.py EXTERNAL_PINNED_WAXFLOW_CLONE EXTERNAL_CORPUS
PCM expectations must then come from scripts/waxflow-oracle.sh.
"""
import pathlib,sys,struct,wave,tempfile,subprocess,shutil
src,corpus=map(pathlib.Path,sys.argv[1:]);corpus=corpus.resolve()
if corpus.is_relative_to(pathlib.Path(__file__).resolve().parent.parent):
    raise SystemExit('Corpus must remain outside the repo')
root=corpus/'waxflow-wma-tests';root.mkdir(parents=True,exist_ok=True)
for name in ['sine-wmav1.wma','sine-wmav2.wma','chapters.wma','frag.wma','tagged.wma']:
    dst=root/'container/asf/testdata'/name;dst.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(src/'container/asf/testdata'/name,dst)
with tempfile.TemporaryDirectory(dir=corpus.parent, prefix='sistrum-wma-gen-') as temp:
    for rate in (8000,16000,22050,32000,44100,48000):
        for channels in (1,2):
            data=bytearray();seed=19
            for i in range(rate//3+37):
                for c in range(channels):
                    seed=(seed*1664525+1013904223)&0xffffffff
                    sample=(((i*(13+2*c))%4096)-2048)*7+((seed>>24)-128)
                    data.extend(struct.pack('<h',sample))
            wav=pathlib.Path(temp)/'signal.wav'
            with wave.open(str(wav),'wb') as w:
                w.setnchannels(channels);w.setsampwidth(2);w.setframerate(rate);w.writeframes(data)
            for version in (1,2):
                out=root/'generated'/f'v{version}-{rate}-{channels}ch.wma';out.parent.mkdir(parents=True,exist_ok=True)
                subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-i',str(wav),'-map_metadata','-1','-c:a',f'wmav{version}','-b:a',str(32000*channels),str(out)],check=True)
print('Added five free pinned ASF vectors and 24 original WMA v1/v2 signals.')
# Header-only refusal vectors derived from MIT WaxFlow's pinned ASF fixtures.
# Retain all packet bytes; only the named unsupported header feature is changed.
base=(src/'container/asf/testdata/sine-wmav2.wma').read_bytes()
v1=(src/'container/asf/testdata/sine-wmav1.wma').read_bytes()
stream_guid=bytes.fromhex('9107dcb7b7a9cf118ee600c00c205365')
file_guid=bytes.fromhex('a1dcab8c47a9cf118ee400c00c205365')
def refusal(name,original,changes):
    data=bytearray(original);sp=data.index(stream_guid)+24;fp=data.index(file_guid)+24
    for location,offset,fmt,value in changes:
        struct.pack_into(fmt,data,(sp if location=='stream' else fp)+offset,value)
    out=root/'refused'/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
refusal('channels-3.wma',base,[('stream',56,'<H',3)])
refusal('rate-96000.wma',base,[('stream',58,'<I',96000)])
refusal('v1-variable-blocks.wma',v1,[('stream',74,'<H',4)])
refusal('v1-stereo-reservoir.wma',v1,[('stream',56,'<H',2),('stream',74,'<H',2)])
refusal('encrypted-stream.wma',base,[('stream',48,'<H',0x8001)])
refusal('pcm-tag.wma',base,[('stream',54,'<H',1)])
fp=base.index(file_guid)+24
minimum=struct.unpack_from('<I',base,fp+68)[0]
refusal('variable-packet-size.wma',base,[('file',72,'<I',minimum+1)])
print('Added seven header-only ASF/WMA refusal vectors from MIT WaxFlow fixtures.')
