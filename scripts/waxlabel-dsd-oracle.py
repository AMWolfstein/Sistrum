#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Sistrum DSD oracle adapter; dsf-meta/dff-meta Apache-2.0, pinned WaxLabel MIT ID3 reader.
"""DSD reference composition: pinned Rust container behavior + pinned WaxLabel ID3 JSON.
DIIN is a Sistrum addition projected independently from owned chunk vectors.
All crate sources, fixtures, helper builds and extracted tags remain external.
"""
import hashlib, json, os, shutil, struct, subprocess, tarfile, urllib.request, zipfile
from pathlib import Path

PINS={'dsf-meta-0.3.0':'dfc2ace16a9905a870d157d87cbd179d701714fe6315a47e61fa194a3d6f7f1a','dff-meta-0.2.0':'06301c411497b9d471ba2aa728dc666fd4c5c300673c2f4d937eb6a737824a1b'}
RUST=r'''
// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum adapter invoking unmodified dsf-meta 0.3.0 and dff-meta 0.2.0.
use std::path::Path;
fn emit_dff(f:dff_meta::DffFile,tag_error:bool) {
    match (f.get_sample_rate(),f.get_num_channels()) {
        (Ok(r),Ok(c))=>println!("OK\tDFF\t{}\t{}\t{}\t{}\t{}",r,c,f.get_audio_length()*8/c as u64,f.get_audio_length(),tag_error),
        _=>println!("ERR\tgeometry")
    }
}
fn main() {
    let arg=std::env::args().nth(1).unwrap();let p=Path::new(&arg);
    if arg.ends_with(".dsf") {
        match dsf_meta::DsfFile::open(p) {
            Ok(f)=>println!("OK\tDSF\t{}\t{}\t{}\t{}\t{}",f.fmt_chunk().sampling_frequency(),f.fmt_chunk().channel_num(),f.fmt_chunk().sample_count(),f.data_chunk().chunk_size(),f.tag_read_err().is_some()),
            Err(e)=>println!("ERR\t{:?}",e)
        }
    } else {
        match dff_meta::DffFile::open(p) {
            Ok(f)=>emit_dff(f,false),
            Err(dff_meta::model::Error::Id3Error(_,f))=>emit_dff(f,true),
            Err(e)=>println!("ERR\t{:?}",e)
        }
    }
}
'''

def build(h):
    root=h.CACHE/'dsd-reference';root.mkdir(exist_ok=True)
    for name,digest in PINS.items():
        archive=root/(name+'.crate')
        if not archive.exists():
            crate,version=name.rsplit('-',1)
            cache_root=Path(os.environ.get('SISTRUM_ORACLE_DIR',Path.home()/'.cache/sistrum-waxflow-oracle'))/'dsd/cargo/registry/cache'
            cached=next(cache_root.glob('*/'+name+'.crate'),None)
            if cached:shutil.copy(cached,archive)
            else:
                with urllib.request.urlopen(f'https://static.crates.io/crates/{crate}/{name}.crate',timeout=60) as response:archive.write_bytes(response.read())
        if h.sha(archive)!=digest:raise ValueError('DSD crate checksum mismatch: '+name)
        target=root/name
        with tarfile.open(archive) as t:
            if not target.exists():t.extractall(root,filter='data')
            for member in t.getmembers():
                if member.isfile():
                    expected=t.extractfile(member).read();local=root/member.name
                    if not local.is_file() or local.read_bytes()!=expected:raise ValueError('DSD extracted source changed: '+member.name)
                    registry=Path(os.environ.get('SISTRUM_ORACLE_DIR',Path.home()/'.cache/sistrum-waxflow-oracle'))/'dsd/cargo/registry/src'
                    for cached in registry.glob('*/'+member.name):
                        if cached.read_bytes()!=expected:raise ValueError('DSD build source changed: '+str(cached))
    helper=h.CACHE/'dsd-helper';(helper/'src').mkdir(parents=True,exist_ok=True)
    shutil.copy(h.REPO/'scripts/dsd-oracle/Cargo.toml',helper/'Cargo.toml')
    shutil.copy(h.REPO/'scripts/dsd-oracle/Cargo.lock',helper/'Cargo.lock')
    (helper/'src/main.rs').write_text(RUST)
    cargo_root=Path(os.environ.get('SISTRUM_ORACLE_DIR',Path.home()/'.cache/sistrum-waxflow-oracle'))/'dsd'
    env={**os.environ,'CARGO_HOME':str(cargo_root/'cargo'),'RUSTUP_HOME':str(cargo_root/'rustup'),'CARGO_TARGET_DIR':str(h.CACHE/'dsd-target')}
    cargo=cargo_root/'cargo/bin/cargo'
    h.run([cargo,'+1.90.0','build','--locked','--release','--manifest-path',helper/'Cargo.toml'],env=env)
    test_root=root/'upstream-tests';test_root.mkdir(exist_ok=True)
    name='sweep-176400hz-0-22050hz-20s-D64-2.8mhz'
    archive=test_root/(name+'.zip')
    if not archive.exists():
        cached=next((cargo_root/'cargo/registry/src').glob('*/dsf-meta-0.3.0/'+name+'.zip'),None)
        if cached:shutil.copy(cached,archive)
        else:
            with urllib.request.urlopen('https://samplerateconverter.com/free-files/samples/dsf/'+name+'.zip',timeout=60) as response:archive.write_bytes(response.read())
    if h.sha(archive)!='69b3b1e389654b6a91b3a37bbfe05c7bcfee4985c468c95eedb932fe9e75e6ae':raise ValueError('DSF upstream test archive changed')
    with zipfile.ZipFile(archive) as z:
        for member in z.namelist():
            if Path(member).name!=member:raise ValueError('Unexpected upstream archive path')
            destination=test_root/member;expected=z.read(member)
            if destination.exists() and destination.read_bytes()!=expected:raise ValueError('DSF upstream fixture changed')
            if not destination.exists():destination.write_bytes(expected)
    return h.CACHE/'dsd-target/release/sistrum-dsd-oracle'

def chunks(data,start,end):
    out=[]
    while start+12<=end:
        kind=data[start:start+4].decode('latin1');size=int.from_bytes(data[start+4:start+12],'big');body=start+12
        out.append((kind,body,size));start=body+size+(size%2)
    return out

def tag_json(h,binary,payload,path):
    extracted=h.CACHE/'owned'/('dsd-tag-'+path.stem+'.wav')
    def chunk(k,p):return k+struct.pack('<I',len(p))+p+(b'\0' if len(p)%2 else b'')
    body=b'WAVE'+chunk(b'fmt ',struct.pack('<HHIIHH',1,1,8000,16000,2,16))+chunk(b'id3 ',payload)+chunk(b'data',b'')
    extracted.write_bytes(b'RIFF'+struct.pack('<I',len(body))+body)
    ref=subprocess.run([str(binary),'dump','--native','--json',str(extracted)],capture_output=True,text=True)
    if ref.returncode:return None,None
    doc=json.loads(ref.stdout)[0];supp=json.loads(subprocess.check_output([str(h.CACHE/'supplement'),str(extracted)],text=True))
    doc['warnings']=[w for w in doc['warnings'] if w['code'] not in ('no-audio','truncated-audio')]
    entries=doc.get('native',[])
    index=next((i for i,n in enumerate(entries) if n['kind']=='id3 chunk' and n.get('note','').startswith('ID3v2.')),None)
    if index is None:return None,None
    doc['native']=[entries[index]]+[n for n in entries[index+1:] if n['kind'].startswith('  ')]
    return doc,supp

def oracle_rows(h,binary):
    rust=build(h);rows=[]
    # Upstream DFF fixture remains external and hashed, like WaxLabel testdata.
    for source in list((h.CACHE/'dsd-reference/dff-meta-0.2.0').glob('*.dff'))+list((h.CACHE/'dsd-reference/upstream-tests').glob('*.dsf')):
        shutil.copy(source,h.CACHE/'owned'/('dsd-upstream-'+source.name))
    for path in sorted(p for p in (h.CACHE/'owned').iterdir() if p.name.startswith('dsd-') and p.suffix in ('.dsf','.dff')):
        raw=path.read_bytes();result=subprocess.check_output([str(rust),str(path)],text=True,timeout=15).strip().split('\t')
        row={'path':path.relative_to(h.CACHE).as_posix(),'sha256':h.sha(path),'containerReference':result}
        if raw[:4] not in (b'DSD ',b'FRM8'):
            row['errorClass']='UNSUPPORTED_FORMAT';rows.append(row);continue
        if result[0]!='OK':row['errorClass']='INVALID_DATA';rows.append(row);continue
        _,fmt,rate,ch,samples,audio,tag_error=result;rate,ch,samples,audio=map(int,(rate,ch,samples,audio))
        ns=samples*1000000000//rate if rate else 0;ms=(ns+500000)//1000000
        props={'container':fmt,'codec':'DSD','bitsPerSample':1}
        for k,v in [('sampleRate',min(rate,2147483647)),('channels',ch),('durationMs',ms),('bitrateBps',min(rate*ch,2147483647))]:
            if v and (k!='bitrateBps' or ms):props[k]=v
        doc={'schemaVersion':1,'format':fmt,'subformat':fmt,'properties':props,'tags':[],'pictures':[],'chapters':[],'syncedLyrics':[],'warnings':[],'native':[]}
        supplement={'totalSamples':samples,'pictureSha256':[]}
        native=doc['native'];payload=None;region=None;diin=[]
        if fmt=='DSF':
            native.extend([{'kind':'DSD ','size':28},{'kind':'fmt ','size':52},{'kind':'data','size':max(0,min(audio-12,len(raw)-92))}])
            pointer=int.from_bytes(raw[20:28],'little')
            if pointer:
                if pointer<92+native[-1]['size'] or pointer<92 or pointer>len(raw):
                    doc['warnings'].append({'code':'malformed-tag-entry','message':'DSF metadata offset does not identify a tag region outside audio'})
                else:payload=raw[pointer:];region=('ID3 chunk',pointer,len(payload))
            if not native[2]['size']:doc['warnings'].append({'code':'no-audio','message':'no audio essence found; file may be tag-only or truncated'})
        else:
            all_chunks=chunks(raw,32,len(raw));native.extend([{'kind':'FRM8','size':int.from_bytes(raw[4:12],'big')},{'kind':'FVER','size':4}])
            prop=next(c for c in all_chunks if c[0]=='PROP');audio_chunk=next(c for c in all_chunks if c[0]=='DSD ' and c[1]>prop[1])
            region=next((c for c in all_chunks if c[0]=='ID3 '),None)
            if region:payload=raw[region[1]:region[1]+region[2]]
            for c in all_chunks:
                kind,body,size=c
                native.append({'kind':'ID3 chunk' if kind=='ID3 ' else kind,'size':size,**({'note':'unparsed'} if kind=='ID3 ' else {})})
                if kind=='PROP':native.extend({'kind':'  '+k,'size':n} for k,_,n in chunks(raw,body+4,body+size))
                if kind=='DIIN':
                    for k,p,n in chunks(raw,body,body+size):
                        native.append({'kind':'  '+k,'size':n})
                        if k in ('DIAR','DITI'):
                            if n<4 or p+n>body+size or int.from_bytes(raw[p:p+4],'big')>n-4:
                                row['errorClass']='INVALID_DATA';break
                            length=int.from_bytes(raw[p:p+4],'big');value=raw[p+4:p+4+length].decode('utf-8',errors='replace').rstrip('\0')
                            diin.append(('ARTIST' if k=='DIAR' else 'TITLE',value))
                    if 'errorClass' in row:break
            if 'errorClass' in row:rows.append(row);continue
        if payload is not None:
            tagdoc,tagsupp=tag_json(h,binary,payload,path)
            if len(payload)<10:doc['warnings'].insert(0,{'code':'malformed-tag-entry','message':'DSD ID3 region is shorter than its header'})
            elif tagdoc is None:doc['warnings'].insert(0,{'code':'malformed-tag-entry','message':'DSD ID3 region could not be parsed'})
            else:
                for key in ['tags','pictures','chapters','syncedLyrics','sources','legacyOnly']:
                    if key in tagdoc:doc[key]=tagdoc[key]
                doc['warnings']=tagdoc['warnings']+doc['warnings'];supplement['pictureSha256']=tagsupp['pictureSha256']
            if fmt=='DSF':entry={'kind':'ID3 chunk','size':region[2]};native.append(entry)
            else:entry=next(n for n in native if n['kind']=='ID3 chunk')
            entry['note']='unparsed'
            if tagdoc is not None:
                entry['note']=tagdoc['native'][0]['note']
                index=native.index(entry)+1;native[index:index]=tagdoc['native'][1:]
        for key in dict.fromkeys(k for k,_ in diin):
            values=[v for k,v in diin if k==key];selected=next((t['values'] for t in doc['tags'] if t['key']==key),None)
            if selected is None:doc['tags'].append({'key':key,'values':values})
            doc.setdefault('sources',[]).append({'key':key,'family':'dff','scope':'track','values':values,'selected':selected is None or all(v.strip().lower() in [s.strip().lower() for s in selected] for v in values)})
        row.update(oracle=doc,supplement=supplement);rows.append(row)
    return rows
