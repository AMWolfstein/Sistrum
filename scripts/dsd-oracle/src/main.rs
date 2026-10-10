// SPDX-License-Identifier: GPL-3.0-or-later
// Original standalone harness using pinned MIT Flick and MIT/Apache-2.0 dsf-meta/dff-meta.
include!(concat!(env!("OUT_DIR"), "/flick.rs"));
use std::{env,fs,io::{Read,Seek,SeekFrom},path::Path};
use dsd_source::{DsdSource,Endianness};
use sha2::{Digest,Sha256};
use dsd::{DsdRate,DsdDecimationPipeline,dop::DopPacker};

type E=Box<dyn std::error::Error+Send+Sync>;
fn hex(b:&[u8])->String {b.iter().map(|v|format!("{v:02x}")).collect()}
fn channel_ids(path:&Path)->Result<String,E>{
    let mut f=fs::File::open(path)?;f.seek(SeekFrom::Start(32))?;
    let end=f.metadata()?.len();let mut h=[0u8;12];
    while f.stream_position()?+12<=end {
        f.read_exact(&mut h)?;let size=u64::from_be_bytes(h[4..12].try_into()?);let at=f.stream_position()?;
        if &h[..4]==b"PROP" {
            let mut tag=[0u8;4];f.read_exact(&mut tag)?;
            while f.stream_position()?+12<=at+size {
                f.read_exact(&mut h)?;let n=u64::from_be_bytes(h[4..12].try_into()?);
                if &h[..4]==b"CHNL" {let mut b=vec![0u8;n as usize];f.read_exact(&mut b)?;return Ok(b[2..].chunks_exact(4).map(|id|String::from_utf8_lossy(id).trim().to_string()).collect::<Vec<_>>().join(","));}
                f.seek(SeekFrom::Current((n+(n&1)) as i64))?;
            }
        }
        f.seek(SeekFrom::Start(at+size+(size&1)))?;
    }
    Err("No DFF channel layout".into())
}
fn run(path:&Path)->Result<String,E>{
    let is_dsf=path.extension().is_some_and(|s|s=="dsf");
    let (source,layout):(Box<dyn DsdSource>,String)=if is_dsf {
        let f=dsf_meta::DsfFile::open(path).map_err(|e|format!("dsf: {e}"))?;
        use dsf_meta::ChannelType::*;
        let layout=match f.fmt_chunk().channel_type(){Mono=>"C",Stereo=>"FL,FR",ThreeChannels=>"FL,FR,C",Quad=>"FL,FR,BL,BR",FourChannels=>"FL,FR,C,LFE",FiveChannels=>"FL,FR,C,BL,BR",FivePointOneChannels=>"FL,FR,C,LFE,BL,BR"}.to_string();
        (Box::new(f),layout)
    }else{let f=dff_meta::DffFile::open(path).map_err(|e|format!("dff: {e}"))?;(Box::new(f),channel_ids(path)?)};
    let info=source.info()?;let rate=info.sample_rate.ok_or("No source rate")?;let channels=info.channels.ok_or("No channels")?;
    let dsd_rate=DsdRate::from_sample_rate(rate).ok_or("dsd: unsupported sample rate")?;
    let pcm_rate=176400;
    let mut pipeline=DsdDecimationPipeline::new(dsd_rate,pcm_rate,channels);
    let mut packer=DopPacker::new(dsd_rate,channels);let mut dop=Vec::new();let mut dop_window=Vec::new();
    let per_channel=info.audio_length/channels as u64;
    let mut input=fs::File::open(path)?;let mut pcm=Vec::new();let mut hash=Sha256::new();let mut frames=0u64;
    let reverse=matches!(info.endianness,Some(Endianness::LsbFirst));
    let mut pos=0u64;
    while pos<per_channel {
        let n=std::cmp::min(4096,per_channel-pos) as usize;let mut planar=vec![0u8;n*channels];let offsets:Vec<usize>=(0..channels).map(|c|c*n).collect();
        if is_dsf {
            for ch in 0..channels {input.seek(SeekFrom::Start(info.data_offset+(pos/4096)*(4096*channels) as u64+(ch*4096) as u64))?;input.read_exact(&mut planar[ch*n..(ch+1)*n])?;}
        }else {
            input.seek(SeekFrom::Start(info.data_offset+pos*channels as u64))?;let mut interleaved=vec![0u8;n*channels];input.read_exact(&mut interleaved)?;
            for c in 0..channels {for i in 0..n {planar[c*n+i]=interleaved[i*channels+c];}}
        }
        if reverse {for b in &mut planar {*b=b.reverse_bits();}}
        pipeline.process_bytes(&planar,&offsets,&mut pcm);
        frames+=(pcm.len()/channels) as u64;for x in &pcm {hash.update(x.to_le_bytes());}
        packer.pack_to_i32(&planar,&offsets,&mut dop);
        for x in dop.iter().take((8*channels-dop_window.len()/4).min(dop.len())) {dop_window.extend(x.to_le_bytes());}
        pos+=n as u64;
    }
    Ok(format!("ok\t{rate}\t{pcm_rate}\t{channels}\t{layout}\t{frames}\t{}\t{}\t{}",hex(&hash.finalize()),packer.carrier_rate(),hex(&dop_window)))
}
fn main()->Result<(),E>{
    let args:Vec<String>=env::args().collect();
    if args.get(1).is_some_and(|s|s=="--coefficients") {
        for mult in [64,128,256] {let rate=44100*mult;let c=dsd::coefficients::generate_sinc_filter(512,18000f64/(rate/4) as f64,dsd::coefficients::WindowFunction::Kaiser{beta:10.0});println!("{mult}\t{}",c.iter().map(|v|format!("{:016x}",v.to_bits())).collect::<Vec<_>>().join(","));}return Ok(())
    }
    println!("file\tfile_sha256\tstatus\tdsd_rate\tpcm_rate\tchannels\tlayout\tframes\tpcm_sha256\tdop_rate\tdop_first8_le32");
    let root=Path::new(args.get(1).ok_or("Usage: sistrum-dsd-oracle CORPUS")?);
    let mut files:Vec<_>=fs::read_dir(root)?.map(|f|f.map(|f|f.path())).collect::<Result<_,_>>()?;files.sort();
    for p in files {if !p.is_file(){continue}let name=p.file_name().unwrap().to_string_lossy();let digest=hex(&Sha256::digest(fs::read(&p)?));match run(&p){Ok(line)=>println!("{name}\t{digest}\t{line}"),Err(e)=>println!("{name}\t{digest}\trefused: {e}\t\t\t\t\t\t\t\t")}}
    Ok(())
}
