#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Original deterministic synthetic audio and AIFF-C fixture generator.
# Generated audio is dedicated to the public domain under CC0-1.0.
"""Usage: waxflow-aiff-corpus.py EXTERNAL_CORPUS. Go supplies all expectations."""
import pathlib,struct,sys
root=pathlib.Path(sys.argv[1]).resolve()
if root.is_relative_to(pathlib.Path(__file__).resolve().parent.parent):
    raise SystemExit('Corpus must remain outside the repository')
out=root/'waxflow-aiff-tests/generated';out.mkdir(parents=True,exist_ok=True)
def chunk(tag,data):return tag+struct.pack('>I',len(data))+data+(b'\0' if len(data)%2 else b'')
def make(name,code,bits,channels,payload,frames=5003,offset=3):
    # 48000 exactly, in IEEE extended precision.
    comm=struct.pack('>hIh',channels,frames,bits)+bytes.fromhex('400ebb80000000000000')+code+b'\0\0'
    body=b'AIFC'+chunk(b'FVER',bytes.fromhex('a2805140'))+chunk(b'JUNK',b'odd')+chunk(b'COMM',comm)+chunk(b'SSND',struct.pack('>II',offset,0)+b'\0'*offset+payload)
    (out/name).write_bytes(b'FORM'+struct.pack('>I',len(body))+body)
for code,bits,wire,ch in [(b'sowt',24,24,2),(b'twos',20,24,2),(b'raw ',8,8,1),(b'in24',16,24,2),(b'in32',16,32,2),(b'fl64',64,64,2),(b'FL32',16,32,2)]:
    payload=bytearray()
    for i in range(5003*ch):
        v=((i*197+37)%65536)-32768
        if code.lower().startswith(b'fl'):payload+=struct.pack('>d' if wire==64 else '>f',v/65536)
        elif code==b'raw ':payload.append((v>>8)+128)
        else:
            value=v<<(wire-16);payload+=value.to_bytes(wire//8,'little' if code==b'sowt' else 'big',signed=True)
    make(code.decode().strip()+'-offset-tail.aifc',code,bits,ch,payload)
for code in [b'alaw',b'ulaw']:
    make(code.decode()+'-stereo.aifc',code,16,2,bytes((i*37)%256 for i in range(10006)))
make('refused-mace.aifc',b'MAC3',16,2,b'\0'*12,frames=2)
make('refused-unknown.aifc',b'zzzz',16,2,b'\0'*12,frames=2)
make('refused-channels.aifc',b'sowt',16,9,b'\0'*36,frames=2)
