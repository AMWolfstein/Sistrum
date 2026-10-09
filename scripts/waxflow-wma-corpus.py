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
