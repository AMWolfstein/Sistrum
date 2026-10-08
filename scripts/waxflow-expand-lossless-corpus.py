#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Original deterministic signals and corpus tooling. Generated audio is CC0-1.0.
# Legacy framing follows MIT WaxFlow codec/ape/ape.go, decode.go and pack.go,
# fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe,
# Copyright (c) 2026 Cole Springer, MIT License (see codecs/THIRD-PARTY-NOTICES).
"""Generate external APE/ALAC coverage; expectations come later from Go oracle.
Usage: waxflow-expand-lossless-corpus.py EXTERNAL_CORPUS MAC_13_26_EXECUTABLE
Requires FFmpeg (used only as a black-box ALAC encoder).
"""
import pathlib, struct, subprocess, sys, tempfile, wave, zlib
corpus=pathlib.Path(sys.argv[1]).resolve()
mac=str(pathlib.Path(sys.argv[2]).resolve())
repo=pathlib.Path(__file__).resolve().parent.parent
if corpus.is_relative_to(repo):
    raise SystemExit('Corpus must remain outside the repository')
ape=corpus/'waxflow-ape-tests'/'generated'
alac=corpus/'waxflow-alac-tests'/'generated'
ape.mkdir(parents=True,exist_ok=True);alac.mkdir(parents=True,exist_ok=True)

def signal(path,depth,channels):
    state=0x7139;data=bytearray();frames=8194
    for i in range(frames):
        for c in range(channels):
            state ^= (state<<13)&0xffffffff;state ^= state>>17;state ^= (state<<5)&0xffffffff
            triangle=((i*(37+2*c))%2048)-1024
            value=(triangle*(1<<(depth-12)))+((state&1023)-512)*(1<<(depth-15))
            data.extend(value.to_bytes(depth//8,'little',signed=True))
    with wave.open(str(path),'wb') as out:
        out.setnchannels(channels);out.setsampwidth(depth//8);out.setframerate(44100);out.writeframes(data)
    return bytes(data)

with tempfile.TemporaryDirectory(prefix='sistrum-lossless-') as temp:
    temp=pathlib.Path(temp)
    for depth in (16,24):
        for channels in (1,2):
            wav=temp/f's{depth}-{channels}ch.wav';signal(wav,depth,channels)
            for level in (1000,2000,3000,4000,5000):
                out=ape/f'signal-c{level}-s{depth}-{channels}ch.ape'
                subprocess.run([mac,str(wav),str(out),f'-c{level}'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    for channels in (1,2,4,6,8):
        wav=temp/f's24-{channels}ch.wav';signal(wav,24,channels)
        layout={1:'mono',2:'stereo',4:'4.0',6:'5.1',8:'7.1(wide)'}[channels]
        subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-channel_layout',layout,'-i',str(wav),'-map_metadata','-1','-ac',str(channels),'-channel_layout',layout,'-c:a','alac',str(alac/f'signal-s24-{channels}ch.m4a')],check=True)
    # A genuine old header and one special-silence frame, constructed directly.
    # No modern stream's version field is relabelled. The old entropy/predictor
    # non-silence path is not exercised by this deliberately narrow legacy vector.
    frames=5000;pcm=bytes(frames*2)
    header=struct.pack('<4sHHHHIIIII',b'MAC ',3970,2000,32,1,44100,0,0,1,frames)
    crc=(zlib.crc32(pcm)>>1)|0x80000000
    logical=struct.pack('>II',crc,1)+bytes([0,1,0,0,0]);logical+=bytes((-len(logical))%4)
    physical=b''.join(logical[i:i+4][::-1] for i in range(0,len(logical),4))
    old=ape/'legacy-3970-silence-s16-mono.ape';old.write_bytes(header+struct.pack('<I',36)+physical)
    decoded=temp/'legacy.wav'
    subprocess.run([mac,str(old),str(decoded),'-d'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    with wave.open(str(decoded),'rb') as inp:
        assert inp.getnchannels()==1 and inp.getsampwidth()==2 and inp.getnframes()==frames
        assert inp.readframes(frames)==pcm
print('Generated 21 APE and 5 ALAC files; legacy APE independently decoded by Monkey\'s Audio.')
