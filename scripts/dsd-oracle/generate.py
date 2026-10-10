#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Original generator. Generated signal/container files are CC0-1.0.
"""Deterministic first-order sigma-delta sine/sweep; no encoder binary required.
Integer phase and error feedback use a Decimal-generated Q24 sine table, avoiding
platform libm differences in the generated compressed corpus.
"""
import pathlib,struct,sys
from decimal import Decimal,localcontext,ROUND_HALF_EVEN

def sine_table():
    with localcontext() as ctx:
        ctx.prec=65
        pi=Decimal('3.1415926535897932384626433832795028841971693993751058209749445923078164')
        table=[]
        for i in range(1024):
            x=2*pi*Decimal(i)/1024
            if x>pi:x-=2*pi
            term=x;value=x
            for k in range(1,55):
                term*= -x*x/Decimal((2*k)*(2*k+1));value+=term
            table.append(int((value*(1<<24)).to_integral_value(rounding=ROUND_HALF_EVEN)))
        return table

def signal(rate,channels,nbytes,table):
    # Alternating fixed tones (997+137*c Hz) and 80..16080 Hz linear sweeps.
    planes=[];count=nbytes*8
    for c in range(channels):
        phase=c*(1<<32)//(channels+1);acc=0;data=bytearray(nbytes)
        for i in range(count):
            freq=(997+137*c) if c%2==0 else 80+16000*i//max(count-1,1)
            phase=(phase+(freq<<32)//rate)&0xffffffff
            acc+=table[phase>>22]//2
            bit=1 if acc>=0 else 0
            acc-=(1<<24) if bit else -(1<<24)
            data[i>>3]|=bit<<(7-(i&7))
        planes.append(data)
    return planes

def id3():return b'ID3\x04\x00\x00\x00\x00\x00\x00'
def dsf(rate,planes,tag=False,extra_bits=0,msb=False):
    channels=len(planes);n=len(planes[0]);payload=bytearray();rev=bytes(int(f'{b:08b}'[::-1],2)for b in range(256))
    for at in range(0,n,4096):
        for plane in planes:
            block=bytes(plane[at:at+4096]);payload+=(block if msb else block.translate(rev))+b'\x69'*(4096-len(block))
    ct={1:1,2:2,6:7}[channels]
    fmt=b'fmt '+struct.pack('<QIIIIIIQII',52,1,0,ct,channels,rate,8 if msb else 1,n*8+extra_bits,4096,0)
    data=b'data'+struct.pack('<Q',12+len(payload))+payload
    end=28+len(fmt)+len(data);metadata=id3()if tag else b''
    return b'DSD '+struct.pack('<QQQ',28,end+len(metadata),end if tag else 0)+fmt+data+metadata

def ck(name,data):return name+struct.pack('>Q',len(data))+data+(b'\0'if len(data)%2 else b'')
def dff(rate,planes,tag=False,diin=False,dst=False):
    ch=len(planes);ids={1:[b'C   '],2:[b'SLFT',b'SRGT'],6:[b'MLFT',b'MRGT',b'C   ',b'LFE ',b'LS  ',b'RS  ']}[ch]
    cmpr=b'DST '+bytes([11])+b'DST Encoded' if dst else b'DSD '+bytes([14])+b'not compressed'
    # Even CMPR payload keeps all declared property chunk boundaries unambiguous.
    if len(cmpr)%2:cmpr+=b'\0'
    prop=b'SND '+ck(b'FS  ',struct.pack('>I',rate))+ck(b'CHNL',struct.pack('>H',ch)+b''.join(ids))+ck(b'CMPR',cmpr)
    body=b'DSD '+ck(b'FVER',bytes.fromhex('01050000'))
    if diin:body+=ck(b'DIIN',ck(b'DITI',struct.pack('>I',9)+b'Synthetic'))
    body+=ck(b'PROP',prop)
    if dst:
        # A DST container refusal vector: FRTE declares zero compressed frames.
        # No fake raw DSD is presented as a valid compressed frame.
        body+=ck(b'DST ',ck(b'FRTE',struct.pack('>IH',0,75)))
    else:body+=ck(b'DSD ',bytes(planes[c][i]for i in range(len(planes[0]))for c in range(ch)))
    if tag:body+=ck(b'ID3 ',id3())
    return b'FRM8'+struct.pack('>Q',len(body))+body

def generate(root):
    root=pathlib.Path(root).resolve()
    if root.is_relative_to(pathlib.Path(__file__).resolve().parents[2]):raise ValueError('Corpus must remain outside the repository')
    root.mkdir(parents=True,exist_ok=True);table=sine_table()
    for mult in [64,128,256]:
        rate=44100*mult
        for ch in [1,2,6]:
            n=rate//(8*20)+3 # 50 ms plus an odd byte tail; several native DSF blocks.
            planes=signal(rate,ch,n,table);name=f'dsd{mult}-{ch}ch'
            (root/(name+'.dsf')).write_bytes(dsf(rate,planes,tag=ch==2))
            (root/(name+'.dff')).write_bytes(dff(rate,planes,tag=ch==2,diin=ch==1))
    planes=signal(2822400,2,4101,table)
    (root/'dsd64-msb-partial-bits.dsf').write_bytes(dsf(2822400,planes,extra_bits=3,msb=True))
    (root/'dst-compressed.dff').write_bytes(dff(2822400,planes,dst=True))
if __name__=='__main__':generate(sys.argv[1])
