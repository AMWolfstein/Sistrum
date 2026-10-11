#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Sistrum oracle harness for WaxLabel cfbb45d13135a76f5df6781fdeec75f5cfe63bf9.
# Copyright (c) 2026 Cole Springer (WaxLabel, MIT); Sistrum harness additions, GPL-3.0-or-later.
"""Fetch/build the exact WaxLabel CLI, generate owned inputs outside git, dump or verify.
Run --fetch-generate with a Python containing mutagen (1.47.0); --verify needs stdlib only.
No audio, upstream tests, or build products are written into the Sistrum repository.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tarfile
import tempfile
import urllib.request
import zlib

PIN = "cfbb45d13135a76f5df6781fdeec75f5cfe63bf9"
REPO = Path(__file__).resolve().parent.parent
CACHE = Path(os.environ.get("SISTRUM_WAXLABEL_ORACLE_DIR", Path.home()/".cache/sistrum-waxlabel-oracle")).resolve()
DOCS = REPO/"docs/waxlabel"
MANIFEST = DOCS/"corpus-manifest.tsv"
HEADER = "SPDX-License-Identifier: GPL-3.0-or-later; WaxLabel MIT, Copyright (c) 2026 Cole Springer"


def run(args, **kw):
    return subprocess.run([str(x) for x in args], check=True, **kw)


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def safe_cache():
    if CACHE == REPO or REPO in CACHE.parents:
        raise SystemExit("WaxLabel cache must be outside the repository")
    CACHE.mkdir(parents=True, exist_ok=True)


def fetch():
    source = CACHE/"src"
    if not source.exists():
        with tempfile.TemporaryDirectory(dir=CACHE) as work:
            archive = Path(work)/"source.tar.gz"
            urllib.request.urlretrieve(f"https://codeload.github.com/AMWolfstein/WaxLabel/tar.gz/{PIN}", archive)
            with tarfile.open(archive) as tar:
                tar.extractall(work, filter="data")
            shutil.move(str(Path(work)/f"WaxLabel-{PIN}"), source)
        (CACHE/"source-pin").write_text(PIN+"\n")
    elif (source/".git").exists():
        actual = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
        dirty = subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=all"], text=True)
        if actual != PIN or dirty:
            raise SystemExit("Refusing an unpinned or modified upstream checkout")
    elif not (CACHE/"source-pin").exists() or (CACHE/"source-pin").read_text().strip() != PIN:
        raise SystemExit("Unrecognized source cache; move it aside and fetch the pin")
    if MANIFEST.exists() and f"# WaxLabel commit {PIN};" in MANIFEST.read_text():
        recorded = set()
        for line in MANIFEST.read_text().splitlines():
            if not line.startswith("src/"):
                continue
            rel,digest = line.split("\t")
            path = CACHE/rel
            if not path.is_file() or sha(path) != digest:
                raise SystemExit(f"Modified pinned source: {rel}")
            recorded.add(rel)
        actual = {p.relative_to(CACHE).as_posix() for p in source.rglob("*") if p.is_file() and ".git" not in p.parts}
        if actual != recorded:
            raise SystemExit("Pinned source file inventory changed")
    binary = CACHE/"waxlabel"
    run([os.environ.get("GO", "go"), "build", "-trimpath", "-o", binary, "./cmd/waxlabel"], cwd=source,
        env={**os.environ, "CGO_ENABLED": "0"})
    return source, binary


def sync(size):
    return bytes((size >> shift) & 127 for shift in (21, 14, 7, 0))


def frame(identifier, body, version=4, flags=0):
    if version == 2:
        return identifier.encode()+len(body).to_bytes(3,"big")+body
    size = sync(len(body)) if version == 4 else struct.pack(">I",len(body))
    return identifier.encode()+size+bytes((0,flags))+body


def tag(frames, version=4, flags=0, padding=0):
    body = b"".join(frames)+bytes(padding)
    return b"ID3"+bytes((version,0,flags))+sync(len(body))+body


def strip_id3(data):
    if data[:3] == b"ID3":
        n = sum((data[i+6]&127) << shift for i,shift in enumerate((21,14,7,0)))
        data = data[n+10:]
    if data[-128:-125] == b"TAG":
        data = data[:-128]
    return data


def chunk(identifier, body, big):
    return identifier.encode()+struct.pack(">I" if big else "<I",len(body))+body+bytes(len(body)&1)


def png():
    def pchunk(k,v):
        return struct.pack(">I",len(v))+k+v+struct.pack(">I",zlib.crc32(k+v))
    return b"\x89PNG\r\n\x1a\n"+pchunk(b"IHDR",struct.pack(">IIBBBBB",2,3,8,2,0,0,0))+pchunk(b"IDAT",zlib.compress((b"\0"+b"\x33\x66\x99"*2)*3))+pchunk(b"IEND",b"")


def generate(source):
    from mutagen.id3 import ID3, TIT2, TPE1, TALB, TXXX, APIC, COMM, USLT, TRCK, TPOS, TDRC, TIPL, SYLT, CHAP, CTOC, UFID
    import mutagen
    if mutagen.version_string != "1.47.0":
        raise SystemExit("Owned ID3 vectors require mutagen 1.47.0")
    owned = CACHE/"owned"
    owned.mkdir(exist_ok=True)
    base = owned/"base.mp3"
    run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=44100:cl=mono",
         "-t","0.2","-c:a","libmp3lame","-b:a","64k","-map_metadata","-1","-y",base])
    audio = strip_id3(base.read_bytes())
    base.write_bytes(audio)
    image = png()
    (owned/"cover.png").write_bytes(image)
    for v in (3,4):
        path = owned/f"mutagen-v2{v}.mp3"
        path.write_bytes(audio)
        t = ID3()
        for f in [TIT2(encoding=3,text=["Sistrum — عنوان"]), TPE1(encoding=3,text=["One","Two"]),
                  TALB(encoding=3,text=["Owned"]), TXXX(encoding=3,desc="MusicBrainz Album Id",text=["release-1"]),
                  TXXX(encoding=1,desc="custom.key",text=["a","b"]), APIC(encoding=3,mime="image/jpeg",type=3,desc="Own cover",data=image),
                  COMM(encoding=3,lang="eng",desc="comment",text=["first","second"]),
                  COMM(encoding=3,lang="eng",desc="iTunNORM",text=["technical"]),
                  USLT(encoding=3,lang="ara",desc="",text="سطر\nثان"), TRCK(encoding=3,text=["03/12"]),
                  TPOS(encoding=3,text=["1/2"]), TDRC(encoding=3,text=["2021-02-03T04:05"]),
                  TIPL(encoding=3,people=[["mix","Alice"],["DJ-mix","Bob"],["producer","C"]]),
                  SYLT(encoding=3,lang="eng",format=2,type=1,desc="main",text=[("\nlate",100),("\r\nearly",20)]),
                  CHAP(element_id="c1",start_time=0,end_time=100,start_offset=0xffffffff,end_offset=0xffffffff,sub_frames=[TIT2(encoding=3,text=["First"])]),
                  CTOC(element_id="toc",flags=3,child_element_ids=["c1"]), UFID(owner="http://musicbrainz.org",data=b"recording-1")]:
            t.add(f)
        t.save(path,v2_version=v,v23_sep=None,padding=lambda _: 64)
    variants = {
        "v22":tag([frame("TT2",b"\0Title",2),frame("TP1",b"\0One\0Two\0\0",2),frame("PIC",b"\0PNG\x03owned\0"+image,2),frame("TXY",b"opaque",2)],2,padding=7),
        "v22-compressed":tag([],2,64,5),
        "invalid-utf8":tag([frame("TIT2",b"\3a\xff\xffb\xf0\x80\x80\x80c"),frame("TXXX",b"\3CUSTOM\0\xff\xffvalue")]),
        "utf16-carry":tag([frame("TXXX",b"\1\xff\xfe"+"CUSTOM".encode("utf-16le")+b"\0\0"+"one".encode("utf-16le")+b"\0\0"+"two".encode("utf-16le"))]),
        "utf16be-bom":tag([frame("TIT2",b"\2\xfe\xff\0A")]),
        "genre-date":tag([frame("TCON",b"\0(17)Rock\0(51)(39)\0RX\0(999)\0((live)"),frame("TYER",b"\0"+b"2021"),frame("TDAT",b"\0"+b"0302"),frame("TIME",b"\0"+b"0405"),frame("TRCK",b"\0bad/2/3")]),
        "signed-numbers":tag([frame("TRCK",b"\0-1/+2"),frame("TPOS",b"\0 4294967296 / 9223372036854775807 "),frame("MVIN",b"\0+1/2")]),
        "invalid-picture":tag([frame("APIC",b"\0bad")]),
        "opaque":tag([frame("TIT2",b"compressed",4,8),frame("TPE1",b"\3visible"),frame("PRIV",b"binary")]),
        "grouped-dli":tag([frame("TIT2",b"\xff"+sync(6)+b"\3Hello",4,65)]),
        "frame-unsync":tag([frame("TIT2",b"\0A\xff\0B",4,2)]),
        "tag-unsync":tag([frame("TIT2",b"\0A\xffB",3)],3).replace(b"\xff",b"\xff\0"),
        "malformed-frame":tag([frame("TIT2",b"\3before")])+b"",  # Replaced below with a real overrun.
        "unsupported-sylt":tag([frame("SYLT",b"\0eng\1\1desc\0line\0"+struct.pack(">I",2)),frame("SYLT",b"\0eng\2\3desc\0line\0"+struct.pack(">I",2))]),
        "invalid-key":tag([frame("TXXX",b"\3bad=key\0value")]),
        "source-conflict":tag([frame("TPE1",b"\3One"),frame("TXXX",b"\3LEAD_PERFORMER\0Two"),frame("TIT2",b"\3A\0B")]),
        "bad-version":b"ID3\x05\0\0"+sync(0),
        "truncated-tag":b"ID3\x04\0\0"+sync(len(audio)*2),
        "footer":tag([frame("TIT2",b"\3Footer")],4,16)+b"3DI\x04\0\x10"+sync(17),
        "ext-v23":tag([],3),
        "ext-v24":tag([],4),
    }
    normal = frame("TIT2",b"\3before")
    malformed = b"TALB"+sync(1000)+b"\0\0\3short"
    variants["malformed-frame"] = tag([normal,malformed])
    unsync = bytearray(variants["tag-unsync"])
    unsync[5] = 128
    unsync[6:10] = sync(len(unsync)-10)
    variants["tag-unsync"] = bytes(unsync)
    variants["ext-v23"] = tag([struct.pack(">I",6)+bytes(6)+frame("TIT2",b"\0Extended",3)],3,64)
    variants["ext-v24"] = tag([sync(6)+bytes((1,0))+frame("TIT2",b"\3Extended")],4,64)
    for name, data in variants.items():
        (owned/f"{name}.mp3").write_bytes(data+audio)
    # Tag-only / legacy-only, stacked trailers, strict false-tag detection.
    (owned/"tag-only.mp3").write_bytes(tag([frame("TIT2",b"\3tag only")]))
    v1 = bytearray(128)
    v1[:3]=b"TAG"; v1[3:8]=b"Title"; v1[33:39]=b"Artist"; v1[93:97]=b"2026"; v1[126]=3; v1[127]=17
    (owned/"v1-only.mp3").write_bytes(audio+v1)
    (owned/"v1-stacked.mp3").write_bytes(audio+v1+v1)
    v1[93:97]=b"90s!"
    (owned/"v1-rejected.mp3").write_bytes(audio+v1)
    embedded = tag([frame("TIT2",b"\3Embedded"),frame("APIC",b"\0image/png\0\3owned\0"+image),frame("SYLT",b"\0eng\2\1\0\nline\0"+struct.pack(">I",10))])
    wavfmt = struct.pack("<HHIIHH",1,1,44100,88200,2,16)
    for suffix,big in (("wav",False),("aiff",True)):
        if big:
            # SANE 44100 exactly, mono, 8 samples, 16-bit.
            comm = struct.pack(">HIH",1,8,16)+bytes.fromhex("400eac44000000000000")
            body = b"AIFF"+chunk("COMM",comm,True)+chunk("SSND",bytes(8)+bytes(16),True)+chunk("ID3 ",embedded,True)
            result = b"FORM"+struct.pack(">I",len(body))+body
        else:
            body = b"WAVE"+chunk("fmt ",wavfmt,False)+chunk("data",bytes(16),False)+chunk("id3 ",embedded,False)
            result = b"RIFF"+struct.pack("<I",len(body))+body
        (owned/f"embedded.{suffix}").write_bytes(result)
    # Streaming art/unsynchronization vectors and every header truncation boundary.
    large_picture = image+bytes((255,0,17,255,224))*220000
    (owned/"large-cover.png").write_bytes(large_picture)
    plain_apic = b"\0image/png\0\3large\0"+large_picture
    (owned/"large-picture.mp3").write_bytes(tag([frame("APIC",plain_apic)])+audio)
    clean = frame("APIC",plain_apic,3)
    stuffed = clean.replace(b"\xff",b"\xff\0")
    (owned/"large-tag-unsync.mp3").write_bytes(b"ID3\3\0\x80"+sync(len(stuffed))+stuffed+audio)
    stuffed = plain_apic.replace(b"\xff",b"\xff\0")
    (owned/"large-frame-unsync.mp3").write_bytes(tag([frame("APIC",stuffed,4,2)])+audio)
    for stem in ("v22","mutagen-v23","mutagen-v24"):
        data = (owned/f"{stem}.mp3").read_bytes()
        tag_length = 10+sum((data[i+6]&127) << shift for i,shift in enumerate((21,14,7,0)))
        for cut in sorted(set(range(0,20)) | {tag_length//2,tag_length-1,tag_length}):
            (owned/f"truncated-{stem}-{cut}.mp3").write_bytes(data[:cut])
    # Literal text sanitization and byte-bounded warnings.
    (owned/"warning-snippet.mp3").write_bytes(tag([frame("TXXX",b"\3"+("bad=\n"+"ع"*60).encode()+b"\0value")])+audio)
    (owned/"descriptor-controls.mp3").write_bytes(tag([frame("COMM",b"\3eng"+"x\n\u202e".encode()+b"\0comment")])+audio)
    # Image reference-data cases from the pinned bits/image.go signature readers.
    jpeg = bytes.fromhex("ffd8ffe000040000ffc00008080003000203ffd9")
    gif = b"GIF89a"+struct.pack("<HHBBB",2,3,0,0,0)
    tiff = b"II*\0"+struct.pack("<I",8)+struct.pack("<H",2)+struct.pack("<HHII",256,4,1,2)+struct.pack("<HHII",257,4,1,3)+bytes(4)
    webp = b"RIFF"+struct.pack("<I",22)+b"WEBPVP8X"+struct.pack("<I",10)+bytes(4)+bytes((1,0,0,2,0,0))
    bmp = b"BM"+bytes(12)+struct.pack("<IiiHH",40,-2,-3,1,24)+bytes(4)
    images = {"jpeg":jpeg,"gif":gif,"tiff":tiff,"webp":webp,"bmp":bmp,
              "avif":struct.pack(">I",20)+b"ftypavif"+bytes(4)+b"mif1",
              "jxl":bytes.fromhex("0000000c4a584c200d0a870a"),"jpeg-truncated":b"\xff\xd8\xff\xe0"}
    for name,payload in images.items():
        (owned/f"picture-{name}.mp3").write_bytes(tag([frame("APIC",b"\0image/junk\0\3\0"+payload)])+audio)
    (owned/"picture-link.mp3").write_bytes(tag([frame("APIC",b"\0-->\0\3\0https://example.test/cover.png")])+audio)
    return owned


def generate_vorbis(source):
    """Owned vectors: ffmpeg encoders, mutagen 1.47.0 comments/art, structural mutations."""
    import base64
    import mutagen
    from mutagen.flac import FLAC, Picture
    from mutagen.oggvorbis import OggVorbis
    from mutagen.oggopus import OggOpus
    from mutagen.oggflac import OggFLAC
    if mutagen.version_string != "1.47.0":
        raise SystemExit("Owned Vorbis vectors require mutagen 1.47.0")
    owned = CACHE/"owned"
    owned.mkdir(exist_ok=True)
    image = png()
    def picture(payload=image, typ=3, mime=b"image/jpeg", desc="Owned — صورة".encode(), dims=(99,88,1,7)):
        return struct.pack(">II",typ,len(mime))+mime+struct.pack(">I",len(desc))+desc+struct.pack(">IIIII",*dims,len(payload))+payload
    def comments(entries, vendor=b"Sistrum"):
        return struct.pack("<I",len(vendor))+vendor+struct.pack("<I",len(entries))+b"".join(struct.pack("<I",len(e))+e for e in entries)
    def block(code,body,last=False):
        return bytes((code|(128 if last else 0),))+len(body).to_bytes(3,"big")+body
    common=[b"TITLE=Owned",b"ARTIST=One",b"ARTIST=Two",b"DATE=2026-10-11",b"TRACKNUMBER=03/12",b"CHAPTER001=00:00:00.025",b"CHAPTER001NAME=First",b"SYNCEDLYRICS=[offset:5]\n[00:00.050] Hello\n[00:00.025] Early",b"CUSTOM=a",b"CUSTOM=b"]
    bases={}
    for suffix,codec,klass in (("flac","flac",FLAC),("ogg","libvorbis",OggVorbis),("opus","libopus",OggOpus),("oga","flac",OggFLAC)):
        base=owned/f"vorbis-base.{suffix}"
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.2","-c:a",codec,"-map_metadata","-1","-y",base])
        bases[suffix]=base.read_bytes()
        dest=owned/f"vorbis-mutagen.{suffix}"; dest.write_bytes(bases[suffix])
        tags=klass(dest)
        for entry in common:
            name,value=entry.decode().split("=",1)
            tags.setdefault(name,[]).append(value)
        if suffix=="flac":
            art=Picture(); art.data=image; art.mime="image/jpeg"; art.type=3; art.desc="Owned — صورة"; art.width=99; art.height=88; art.depth=1; art.colors=7
            tags.add_picture(art)
        else: tags["METADATA_BLOCK_PICTURE"]=[base64.b64encode(picture()).decode()]
        tags.save()
    def flac_parts(data):
        p=4; blocks=[]
        while True:
            h=data[p:p+4]; n=int.from_bytes(h[1:4],"big"); blocks.append((h[0]&127,data[p+4:p+4+n])); p+=4+n
            if h[0]&128: return blocks,data[p:]
    blocks,audio=flac_parts(bases["flac"])
    si=blocks[0][1]
    variants={
        "structured":[(4,comments(common)),(6,picture())],
        "conflicts":[(4,comments([b"ALBUMARTIST=A",b"ALBUM ARTIST=B",b"TRACKNUMBER=2/9",b"TOTALTRACKS=7",b"=empty",b"bad~key=x",b"unseparated",b"another",b"TITLE=a\xff\xffb"]))],
        "duplicates":[(4,comments([b"TITLE=first"])),(4,comments([b"TITLE=second"])),(42,b"unknown"),(1,bytes(11)),(1,bytes(5))],
        "comment-picture":[(4,comments(common+[b"METADATA_BLOCK_PICTURE="+base64.b64encode(picture())]))],
        "bad-base64":[(4,comments([b"METADATA_BLOCK_PICTURE=not base64!"]))],
        "bad-picture":[(4,comments([])),(6,b"short")],
        "oversize-type":[(6,picture(typ=259))],
        "unknown-image":[(6,picture(payload=b"arbitrary"))],
        "linked-image":[(6,picture(payload=b"https://example.test/cover",mime=b"-->"))],
        "large-native-picture":[(6,picture(payload=image+bytes(1100000)))],
        "large-comment-picture":[(4,comments([b"METADATA_BLOCK_PICTURE="+base64.b64encode(picture(payload=image+bytes(1100000)))]))],
        "encoder-noise":[(4,comments([b"ENCODER=Lavf61",b"ENCODER=Lavc61"],b"Lavf61"))],
        "lyric-edge-cases":[(4,comments([b"SYNCEDLYRICS=[ OFFSET: 9223372036854775807 ]\n[01:02:03.456] hour\n[00:60] invalid\n[12] invalid\n[00:01.5][00:00.050] doubled",b"CHAPTER0=0.5",b"CHAPTER0NAME=Zero",b"CHAPTER1=00:00:00.05",b"CHAPTER1NAME=Earlier",b"CHAPTER2=00:00:00.05",b"CHAPTER2NAME=Tie"]))],
        "lyric-cap":[(4,comments([b"SYNCEDLYRICS="+b"[00:00.001] a\n"*65537]))],
        "empty-comments":[(4,comments([]))],
        "no-comments":[],
    }
    for name,extra in variants.items():
        parts=[(0,si)]+extra
        data=b"fLaC"+b"".join(block(code,body,i==len(parts)-1) for i,(code,body) in enumerate(parts))+audio
        (owned/f"vorbis-{name}.flac").write_bytes(data)
    structured=(owned/"vorbis-structured.flac").read_bytes()
    (owned/"vorbis-leading-id3.flac").write_bytes(tag([frame("TIT2",b"\3Legacy"),frame("TPE1",b"\3One")])+structured)
    v1=bytearray(128);v1[:3]=b"TAG";v1[3:9]=b"Legacy";v1[93:97]=b"2026";v1[127]=17
    (owned/"vorbis-trailing-v1.flac").write_bytes(structured+v1)
    (owned/"vorbis-tail-junk.flac").write_bytes(structured+b"junk"*1024)
    (owned/"vorbis-truncated-audio.flac").write_bytes(structured[:-3])
    parts,unused=flac_parts(structured)
    metadata=b"fLaC"+b"".join(block(code,body,i==len(parts)-1) for i,(code,body) in enumerate(parts))
    (owned/"vorbis-tag-only.flac").write_bytes(metadata)
    (owned/"vorbis-invalid-block.flac").write_bytes(b"fLaC"+block(127,b""))
    (owned/"vorbis-zero-rate.flac").write_bytes(b"fLaC"+block(0,bytes(34),True))
    (owned/"vorbis-wrong-first.flac").write_bytes(b"fLaC"+block(4,comments([]),True))
    for suffix in bases:
        data=(owned/f"vorbis-mutagen.{suffix}").read_bytes()
        for cut in sorted(set(range(0,34))|{len(data)//2,len(data)-1}):
            (owned/f"vorbis-truncated-{cut}.{suffix}").write_bytes(data[:cut])
    def ogg_page(body,lacing,serial,seq,flags=0,granule=0):
        data=bytearray(b"OggS"+bytes((0,flags))+struct.pack("<QIII",granule,serial,seq,0)+bytes((len(lacing),))+bytes(lacing)+body)
        crc=0
        for value in data:
            crc ^= value << 24
            for unused in range(8): crc=((crc<<1)^ (0x04c11db7 if crc&0x80000000 else 0))&0xffffffff
        data[22:26]=struct.pack("<I",crc)
        return bytes(data)
    def packet_pages(packet,serial,seq,flags=0):
        lacing=[255]*(len(packet)//255)+[len(packet)%255]
        data=b""; p=0; page_count=0
        for i in range(0,len(lacing),255):
            lace=lacing[i:i+255]; n=sum(lace)
            data+=ogg_page(packet[p:p+n],lace,serial,seq+page_count,flags if i==0 else 1)
            p+=n;page_count+=1
        return data,page_count
    def ogg_headers(data,need):
        p=0;packets=[];current=b""
        while p<len(data):
            count=data[p+26]; lace=data[p+27:p+27+count];body=p+27+count;n=sum(lace);pos=body
            for value in lace:
                current+=data[pos:pos+value];pos+=value
                if value<255: packets.append(current);current=b""
            p=body+n
            if len(packets)>=need: return packets[:need],data[p:]
    for suffix,need in (("ogg",3),("opus",2),("oga",2)):
        original,audio_pages=ogg_headers(bases[suffix],need)
        serial=struct.unpack_from("<I",bases[suffix],14)[0]
        versions={"large-packet":[b"METADATA_BLOCK_PICTURE="+base64.b64encode(picture(payload=image+bytes(180000)))],
                  "bad-picture":[b"METADATA_BLOCK_PICTURE=bad!"],"chapter-lyrics":common+[b"CHAPTERS=custom",b"CHAPTER999NAME=orphan",b"CHAPTER4=99:99:99.5",b"SYNCEDLYRICS=untimed"]}
        for name,entries in versions.items():
            pkt=comments(entries)
            headers=original[:]
            headers[1]=(b"\x03vorbis"+pkt+b"\1" if suffix=="ogg" else b"OpusTags"+pkt+bytes(13) if suffix=="opus" else block(4,pkt,True))
            result=b"";seq=0
            for i,pkt in enumerate(headers):
                raw,n=packet_pages(pkt,serial,seq,2 if i==0 else 0);result+=raw;seq+=n
            (owned/f"vorbis-ogg-{name}.{suffix}").write_bytes(result+audio_pages)
        # Structurally valid page with header completion and audio in the same body.
        if suffix=="opus":
            first,n=packet_pages(original[0],serial,0,2)
            tags=b"OpusTags"+comments(common)
            lace=[255]*(len(tags)//255)+[len(tags)%255,3]
            shared=ogg_page(tags+b"\xf8\xff\xfe",lace,serial,n,4,9912)
            (owned/"vorbis-shared-page.opus").write_bytes(first+shared)
            (owned/"vorbis-chained.opus").write_bytes(bases[suffix]+bases[suffix])
            (owned/"vorbis-ogg-tail.opus").write_bytes(bases[suffix]+b"junk")
        if suffix=="oga":
            headers=[original[0],block(4,comments(common)),block(6,picture()),block(1,bytes(9),True)]
            raw=b"";seq=0
            for i,pkt in enumerate(headers):
                data,n=packet_pages(pkt,serial,seq,2 if i==0 else 0);raw+=data;seq+=n
            (owned/"vorbis-ogg-native-picture.oga").write_bytes(raw+audio_pages)

    # Pinned CLI is also an owned-vector writer, exercising Ogg output-gain headers.
    for gain in ("-3.5","0.00390625","12.25"):
        path=owned/f"vorbis-gain-{gain}.opus";path.write_bytes(bases["opus"])
        run([CACHE/"waxlabel","set","--output-gain",gain,path])
    return owned


HELPER = '''// SPDX-License-Identifier: GPL-3.0-or-later
// Sistrum oracle supplement; WaxLabel MIT, Copyright (c) 2026 Cole Springer.
package main
import("context";"crypto/sha256";"encoding/json";"fmt";"os";wl "github.com/colespringer/waxlabel")
func main(){d,e:=wl.ParseFile(context.Background(),os.Args[1]);if e!=nil{fmt.Fprintln(os.Stderr,e);os.Exit(1)}
p:=d.Properties().First(); hashes:=[]string{};for _,pic:=range d.Pictures(){h:=sha256.Sum256(pic.Data);hashes=append(hashes,fmt.Sprintf("%x",h))}
json.NewEncoder(os.Stdout).Encode(map[string]any{"totalSamples":p.TotalSamples,"pictureSha256":hashes})}
'''


def generate_mp4(source):
    """Owned silence and deterministic MP4 metadata/structure vectors, always external."""
    import mutagen
    from mutagen.mp4 import MP4, MP4Cover, MP4FreeForm
    if mutagen.version_string != "1.47.0":
        raise SystemExit("Owned MP4 vectors require mutagen 1.47.0")
    owned = CACHE/"owned"
    owned.mkdir(exist_ok=True)
    def atom(name, body=b"", extended=False):
        name = name.encode("latin1") if isinstance(name,str) else name
        return (struct.pack(">I4sQ",1,name,len(body)+16) if extended else struct.pack(">I4s",len(body)+8,name))+body
    def data(value,typ=1):
        value=value.encode() if isinstance(value,str) else value
        return atom("data",struct.pack(">II",typ,0)+value)
    def item(name,*values,typ=1):
        return atom(name,b"".join(data(v,typ) for v in values))
    def free(name,*values,mean="com.apple.iTunes",typ=1):
        return atom("----",atom("mean",bytes(4)+mean.encode())+atom("name",bytes(4)+name.encode())+b"".join(data(v,typ) for v in values))
    ftyp=atom("ftyp",b"M4A "+bytes(4)+b"isomM4A ")
    hdlr=atom("hdlr",bytes(8)+b"mdir"+bytes(13))
    def minimal(items=b"",other=b"",meta_extra=b"",prefix=b"",body=None):
        return prefix+ftyp+atom("moov",body if body is not None else atom("udta",atom("meta",bytes(4)+hdlr+atom("ilst",items)+meta_extra)+other))
    def put(name,body):
        (owned/("mp4-"+name+".m4a")).write_bytes(body)
    base=owned/"mp4-base.m4a"
    run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.2","-c:a","aac","-map_metadata","-1","-y",base])
    tagged=owned/"mp4-mutagen.m4a"; tagged.write_bytes(base.read_bytes()); m=MP4(tagged)
    m["\xa9nam"]=["Owned — صورة"]; m["\xa9ART"]=["One","Two"]; m["\xa9alb"]=["Album"]
    m["trkn"]=[(3,12)]; m["disk"]=[(1,2)]; m["cpil"]=[True]; m["tmpo"]=[125]
    m["\xa9lyr"]=["unsynced\nlyrics"]; m["covr"]=[MP4Cover(png(),imageformat=MP4Cover.FORMAT_PNG)]
    m["----:com.apple.iTunes:MusicBrainz Track Id"]=[MP4FreeForm(b"owned-recording-id")]
    m["----:com.apple.iTunes:CUSTOM"]=[MP4FreeForm(b"a"),MP4FreeForm(b"b")]; m.save()
    for codec,suffix,opts in [("alac","m4a",[]),("flac","mp4",["-strict","-2"]),("pcm_s24be","mov",[]),("pcm_f64le","mov",[])]:
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=96000:cl=stereo","-t","0.2","-c:a",codec,*opts,"-metadata","title=Owned","-y",owned/f"mp4-{codec}.{suffix}"])
    for suffix,flags in [("mdta","+use_metadata_tags"),("fragmented","+frag_keyframe+empty_moov")]:
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.2","-c:a","aac","-movflags",flags,"-metadata","title=Owned","-metadata","custom=value","-y",owned/f"mp4-{suffix}.m4a"])
    for codec in ("pcm_s24le","pcm_f32le"):
        for rate in (44100,96000):
            run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i",f"anullsrc=r={rate}:cl=stereo","-t","0.2","-c:a",codec,"-y",owned/f"mp4-{codec}-{rate}.mp4"])
    meta=owned/"mp4-chapters.ffmeta"
    meta.write_text(";FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=25\nEND=100\ntitle=First\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=100\nEND=200\ntitle=Second\n")
    run(["ffmpeg","-nostdin","-v","error","-i",base,"-i",meta,"-map_metadata","1","-map_chapters","1","-c","copy","-y",owned/"mp4-chapters.m4b"])
    text_names=["\xa9nam","\xa9ART","aART","\xa9alb","\xa9wrt","\xa9day","\xa9cmt","\xa9gen","\xa9too","cprt","\xa9grp","\xa9lyr","desc","ldes","soal","soaa","soar","sonm","soco","\xa9wrk","\xa9mvn","\xa9enc"]
    put("all-text",minimal(b"".join(item(n,"First","Second") for n in text_names)))
    put("conflict",minimal(item("\xa9nam","A")+item("\xa9nam","B")+item("gnre",struct.pack(">H",18),typ=0)+item("\xa9gen","Other")))
    put("pairs",minimal(item("trkn",bytes(2)+struct.pack(">HHH",3,12,0),typ=0)+item("disk",bytes(2)+struct.pack(">HH",0,2),typ=21)))
    for name,typ,values in [("cpil",21,[b"\xff"]),("pgap",0,[b"\0"]),("shwm",21,[b"\1"]),("stik",21,[b"\xff\xff\xff\xff"]),("tmpo",21,[b"\0\x80"]),("\xa9mvi",21,[b"\x03"]),("\xa9mvc",0,[b"\x04"]),("rtng",21,[b"\x02"])]:
        put("int-"+name.replace("\xa9","c"),minimal(item(name,*values,typ=typ)))
    names=["MusicBrainz Track Id","musicbrainz album id","replaygain_track_gain","DJ MIXER","MUSICBRAINZ_ALBUMSTATUS","LEAD_PERFORMER","DATE_RELEASED","ENCODED_BY","CUSTOM","iTunNORM","Bad=Key","lowercase","MusicBrainz Album Release Country","NARRATOR"]
    put("freeforms",minimal(b"".join(free(n,"one","two") for n in names)+free("TITLE","foreign",mean="org.example")))
    put("freeform-binary",minimal(free("CUSTOM",b"\0\xff",typ=21)))
    put("freeform-invalid-utf8",minimal(free("CUSTOM",b"\xff")))
    put("covers",minimal(item("covr",png(),b"junk",bytes.fromhex("47494638396102000300800000"),typ=13)))
    put("large-cover",minimal(item("covr",png()+bytes(1100000),typ=14)))
    put("padding",minimal(item("\xa9nam","Owned"),meta_extra=atom("skip",bytes(128))))
    put("leading-padding",minimal(item("\xa9nam","Owned"),prefix=atom("wide")+atom("free",bytes(8))))
    for ver in (0,1):
        ch=bytes((ver,0,0,0))+(bytes(4) if ver else b"")+bytes((2,))+struct.pack(">QB",250000,5)+b"First"+struct.pack(">QB",1000000,6)+b"Second"
        put("nero-v"+str(ver),minimal(other=atom("chpl",ch)))
    for name,version,starts in [("nero-v2",2,[250000,1000000]),("nero-fractional",1,[255001,1005001]),("nero-saturation",1,[0,0xffffffffffffffff]),("nero-unordered",1,[1000000,250000])]:
        ch=bytes((version,0,0,0))+bytes(4)+bytes((2,))+b"".join(struct.pack(">QB",start,len(title))+title for start,title in zip(starts,[b"First",b"Second"]))
        put(name,minimal(other=atom("chpl",ch)))
    put("nero-invalid-utf8",minimal(other=atom("chpl",bytes(4)+bytes((1,))+struct.pack(">QB",0,1)+b"\xff")))
    put("nero-malformed",minimal(other=atom("chpl",b"\1\0\0\0")))
    qttext=lambda v,lang=0: struct.pack(">HH",len(v.encode()),lang)+v.encode()
    put("udta",minimal(item("\xa9nam","ilst"),other=atom("\xa9nam",qttext("different"))+atom("\xa9ART",qttext("Arabic",0x1601)+qttext("English",0x15c7))+atom("\xa9swr",qttext("Lavf owned"))))
    put("udta-data",minimal(other=item("\xa9nam","title")))
    put("udta-invalid",minimal(other=atom("\xa9nam",b"\0\1\0\0\xff")))
    keynames=["title","com.apple.quicktime.artist","custom_key","bad=key","covr"]
    keys=atom("keys",bytes(4)+struct.pack(">I",len(keynames))+b"".join(atom("mdta",n.encode()) for n in keynames))
    indexed=b"".join(item(struct.pack(">I",i+1),png() if n=="covr" else "value",typ=14 if n=="covr" else 1) for i,n in enumerate(keynames))
    put("indexed",ftyp+atom("moov",atom("udta",atom("meta",bytes(4)+atom("hdlr",bytes(8)+b"mdta"+bytes(13))+keys+atom("ilst",indexed)))))
    for name,body in [("unknown",item("abcd",b"xyz",typ=99)),("text-type",item("\xa9nam","text",typ=21)),("text-utf8",item("\xa9nam",b"\xff")),("int-type",item("cpil",b"0",typ=1)),("int-width",item("tmpo",bytes(5),typ=21)),("int-multiple",item("tmpo",b"\1",b"\2",typ=21)),("pair-short",item("trkn",bytes(5))), ("genre-out",item("gnre",b"\xff\xff")),("data-short",atom("\xa9nam",atom("data",bytes(4)))),("data-tail",atom("\xa9nam",data("a")+b"x")),("data-zero",atom("\xa9nam",bytes(16)))]:
        put(name,minimal(body))
    put("no-moov",ftyp+atom("mdat",bytes(10)))
    put("segment",ftyp+atom("moof"))
    put("nested-overrun",ftyp+atom("moov",struct.pack(">I4s",1000,b"udta")))
    put("nested-zero",ftyp+atom("moov",bytes(8)))
    put("meta-zero-gap",ftyp+atom("moov",atom("udta",atom("meta",bytes(4)+hdlr+bytes(4)))))
    put("moov-nonzero-tail",ftyp+atom("moov",b"abc"))
    put("moov-extended",ftyp+atom("moov",atom("udta",atom("meta",bytes(4)+hdlr+atom("ilst",item("\xa9nam","extended"),True))),True))
    put("top-zero",minimal()+bytes(4)+b"mdat"+bytes(10))
    put("top-overrun",minimal()+struct.pack(">I4s",100,b"junk")+bytes(10))
    put("mdat-overrun",minimal()+struct.pack(">I4s",100,b"mdat")+bytes(10))
    put("moov-overrun",ftyp+struct.pack(">I4s",100,b"moov"))
    put("extended-short",ftyp+struct.pack(">I4s",1,b"moov"))
    # Synthetic sample entries isolate the pinned high-rate PCM quirk without relying on a ffmpeg version.
    for codec in ["ipcm","fpcm","alac","fLaC","mp4a","sowt","fl64","ulaw"]:
        for version in [0,1,2]:
            fixed=bytearray(28); struct.pack_into(">H",fixed,8,version); struct.pack_into(">HH",fixed,16,2,16); struct.pack_into(">I",fixed,24,48000<<16)
            ext=b""
            if version==1: fixed+=bytes(16)
            if version==2: fixed+=struct.pack(">IdIIIII",72,96000.0,2,0x7f000000,32,0,0)+bytes(8)
            if codec in ["ipcm","fpcm"]: ext=atom("pcmC",bytes(4)+bytes((0,32)))
            if codec=="alac":
                cookie=bytearray(28); cookie[9]=24;cookie[13]=2;struct.pack_into(">I",cookie,24,96000);ext=atom("alac",cookie)
            if codec=="fLaC":
                si=bytearray(34);struct.pack_into(">Q",si,10,(96000<<44)|(1<<41)|(23<<36)|19200);ext=atom("dfLa",bytes(4)+b"\x80\0\0\x22"+si)
            if codec=="mp4a":
                desc=lambda t,v:bytes((t,len(v)))+v
                ext=atom("esds",bytes(4)+desc(3,b"\0\1\0"+desc(4,b"\x40\x15"+bytes(11)+desc(5,b"\x11\x90"))))
            stsd=atom("stsd",bytes(4)+struct.pack(">I",1)+atom(codec,fixed+ext))
            mdhd=atom("mdhd",bytes(12)+struct.pack(">II",96000,19200)+bytes(4))
            trak=atom("trak",atom("mdia",mdhd+atom("hdlr",bytes(8)+b"soun"+bytes(13))+atom("minf",atom("stbl",stsd))))
            put(f"entry-{codec}-v{version}",minimal(body=trak)+atom("mdat",bytes(10)))
    original=tagged.read_bytes()
    for cut in sorted(set([1,3,4,7,8,12,20,28,40,64,128,256,512,1024,len(original)-1,len(original)//2])):
        put(f"truncated-{cut}",original[:cut])
    # Mutate metadata paths only; sample data remains generated silence except the explicit hostile vector.
    containers={b"moov",b"trak",b"mdia",b"minf",b"stbl",b"udta",b"meta",b"ilst"}
    def boxes(blob,start=0,end=None):
        end=len(blob) if end is None else end
        out=[]; pos=start
        while pos+8<=end:
            size,kind=struct.unpack_from(">I4s",blob,pos); header=8
            if size==1: size=struct.unpack_from(">Q",blob,pos+8)[0];header=16
            if size==0: size=end-pos
            if size<header or size>end-pos:break
            child=pos+header+(4 if kind==b"meta" and blob[pos+header:pos+header+4]==bytes(4) else 0)
            out.append((kind,pos,size,header,boxes(blob,child,pos+size) if kind in containers else []));pos+=size
        return out
    def flatten(nodes):
        return [n for node in nodes for n in [node,*flatten(node[4])]]
    chbytes=(owned/"mp4-chapters.m4b").read_bytes()
    tree=boxes(chbytes); flat=flatten(tree)
    chpl=next(n for n in flat if n[0]==b"chpl")
    # Remove chpl without shifting any sample offset: the same-sized unknown atom is preserved.
    patched=bytearray(chbytes);patched[chpl[1]+4:chpl[1]+8]=b"zzzz";put("qt-only",patched)
    patched=bytearray(chbytes);title=chpl[1]+chpl[3]+9+9;patched[title:title+5]=b"Other";put("chapter-conflict",patched)
    traks=[n for n in flat if n[0]==b"trak"]
    audio=next(t for t in traks if any(n[0]==b"hdlr" and chbytes[n[1]+n[3]+8:n[1]+n[3]+12]==b"soun" for n in flatten(t[4])))
    text=next(t for t in traks if t!=audio)
    audio_stco=next(n for n in flatten(audio[4]) if n[0]==b"stco")
    text_stco=next(n for n in flatten(text[4]) if n[0]==b"stco")
    text_stsz=next(n for n in flatten(text[4]) if n[0]==b"stsz")
    first_audio=struct.unpack_from(">I",chbytes,audio_stco[1]+audio_stco[3]+8)[0]
    patched=bytearray(chbytes);struct.pack_into(">I",patched,text_stco[1]+text_stco[3]+8,first_audio)
    patched[first_audio:first_audio+6]=b"\0\4LEAK";put("chapter-audio-alias",patched)
    for name,box,offset,value in [("stco-count",audio_stco,4,0xffffffff),("stsz-count",text_stsz,8,0xffffffff)]:
        patched=bytearray(chbytes);struct.pack_into(">I",patched,box[1]+box[3]+offset,value);put(name,patched)
    for name,offset,value in [("stco-outside",text_stco[1]+text_stco[3]+8,len(chbytes)+100),("stco-structural",text_stco[1]+text_stco[3]+8,0)]:
        patched=bytearray(chbytes);struct.pack_into(">I",patched,offset,value);put(name,patched)
    # Property configs from the pinned tests: hierarchical/sync AAC, integer float rate overflow, wave ALAC.
    def sound(codec,ext=b"",version=0,rate=48000,ch=2,depth=16,mdversion=0):
        fixed=bytearray(28);struct.pack_into(">H",fixed,8,version);struct.pack_into(">HH",fixed,16,ch,depth);struct.pack_into(">I",fixed,24,rate<<16)
        if version==1:fixed+=bytes(16)
        if version==2:fixed+=struct.pack(">IdIIIIII",72,float(rate),ch,0x7f000000,depth,0,0,0)+bytes(8)
        stsd=atom("stsd",bytes(4)+struct.pack(">I",1)+atom(codec,fixed+ext))
        md=bytes((mdversion,0,0,0))+ (bytes(8)+struct.pack(">II",48000,9600) if mdversion==0 else bytes(16)+struct.pack(">IQ",48000,9600))+bytes(4)
        trak=atom("trak",atom("mdia",atom("mdhd",md)+atom("hdlr",bytes(8)+b"soun"+bytes(13))+atom("minf",atom("stbl",stsd))))
        return minimal(body=trak)+atom("mdat",bytes(10))
    def bits(seq):
        b="".join(f"{n:0{width}b}" for n,width in seq);return int(b.ljust((len(b)+7)//8*8,"0"),2).to_bytes((len(b)+7)//8,"big")
    def esds(config,oti=0x40):
        desc=lambda t,v:bytes((t,len(v)))+v
        return atom("esds",bytes(4)+desc(3,b"\0\1\0"+desc(4,bytes((oti,0x15))+bytes(11)+desc(5,config))))
    configs={"lc":bits([(2,5),(3,4),(2,4),(0,3)]),"he":bits([(5,5),(6,4),(1,4),(3,4),(2,5),(0,3)]),"hev2":bits([(29,5),(6,4),(1,4),(3,4),(2,5),(0,3)]),"main":bits([(1,5),(3,4),(2,4),(0,3)]),"explicit-rate":bits([(2,5),(15,4),(96000,24),(2,4),(0,3)]),"sync-he":bits([(2,5),(6,4),(1,4),(0,3),(0x2b7,11),(5,5),(1,1),(3,4),(0x548,11),(1,1)]),"denied":bits([(2,5),(6,4),(1,4),(0,3),(0x2b7,11),(5,5),(0,1)]),"pce":bits([(2,5),(3,4),(0,4),(0,3)]),"empty":b"", "escaped":bits([(31,5),(10,6),(3,4),(2,4)])}
    for name,cfg in configs.items():put("asc-"+name,sound("mp4a",esds(cfg)))
    put("asc-wave",sound("mp4a",atom("wave",atom("mp4a",bytes(4))+esds(configs["lc"])),version=1))
    put("asc-oti-mp3",sound("mp4a",esds(b"",0x6b)))
    put("asc-malformed-descriptor",sound("mp4a",atom("esds",bytes(4)+b"\3\x80\x80\x80\x80")))
    cookie=bytearray(28);cookie[9]=24;cookie[13]=6;struct.pack_into(">I",cookie,24,96000)
    put("alac-wave",sound("alac",atom("wave",atom("alac",cookie)),version=1))
    put("alac-v2",sound("alac",atom("alac",cookie),version=2,rate=48000))
    put("mdhd-v1",sound("sowt",mdversion=1))
    put("ima4-v1",sound("ima4",version=1))
    put("wave-mp3",sound("ms\x00\x55"))
    put("fpcm-64",sound("fpcm",atom("pcmC",bytes(4)+bytes((0,64)))))
    put("unknown-codec",sound("ZZZZ"))
    for codec in ("ipcm","fpcm"):
        put(codec+"-hires-pinned",sound(codec,atom("pcmC",bytes(4)+bytes((0,32))),rate=48000))
    for name,size in [("extended-small",15),("extended-negative",0xffffffffffffffff)]:
        put(name,ftyp+struct.pack(">I4sQ",1,b"moov",size))
    put("nested-short-size",ftyp+atom("moov",struct.pack(">I4s",4,b"udta")))
    put("bare-meta",ftyp+atom("moov",atom("udta",atom("meta",hdlr+atom("ilst",item("\xa9nam","QuickTime"))))))
    return owned


def generate_ape(source):
    """APEv1/v2 vectors, structural audio headers and ffmpeg/mutagen inputs, all external."""
    import mutagen
    from mutagen.apev2 import APEv2, APEBinaryValue
    if mutagen.version_string != "1.47.0":
        raise SystemExit("Owned APE vectors require mutagen 1.47.0")
    owned=CACHE/"owned";owned.mkdir(exist_ok=True)
    def entry(name,value,flags=0):
        name=name.encode() if isinstance(name,str) else name
        value=value.encode() if isinstance(value,str) else value
        return struct.pack("<II",len(value),flags)+name+b"\0"+value
    def ape(items,version=2000,header=True,count=None,flags=0):
        body=b"".join(items); count=len(items) if count is None else count
        record=lambda f:b"APETAGEX"+struct.pack("<IIII",version,len(body)+32,count,f)+bytes(8)
        return (record(0xa0000000) if header else b"")+body+record(flags|(0x80000000 if header else 0))
    def mac(version=3990,frames=1,final=9600,flags=0,rate=48000):
        if version>=3980:
            desc=b"MAC "+struct.pack("<HHIIIIII",version,0,52,24,0,0,512,0)+bytes(20)
            h=struct.pack("<HHIIIHHI",2000,flags,73728*4,final,frames,24,2,rate)
            return desc+h+bytes(512)
        return b"MAC "+struct.pack("<HHHHIIIII",version,4000,flags,2,rate,0,0,frames,final)+bytes(512)
    def wv(samples=9600,flags=(10<<23)|(1<<12)|1,version=0x410,body=bytes(64),index=0,high=0):
        return b"wvpk"+struct.pack("<IHBBIIIII",24+len(body),version,(index>>32)&255,high,samples,index&0xffffffff,9600,flags,0)+body
    def var(n):
        out=[n&127];n>>=7
        while n:out.insert(0,(n&127)|128);n>>=7
        return bytes(out)
    def packet(key,body):
        n=len(body)+3
        while n!=len(body)+2+len(var(n)):n=len(body)+2+len(var(n))
        return key.encode()+var(n)+body
    def sv8(samples=9600,silence=0,rate=1,ch=2,version=8,extra=b""):
        return b"MPCK"+packet("SH",bytes(4)+bytes((version,))+var(samples)+var(silence)+bytes((rate<<5,(ch-1)<<4)))+extra+packet("AP",bytes(64))+packet("SE",b"")
    def sv7(frames=8,version=7,rate=1):
        h=bytearray(24);h[:4]=b"MP+"+bytes((version,));struct.pack_into("<I",h,4,frames);h[10]=rate
        return bytes(h)+bytes(64)
    def put(name,suffix,body):
        (owned/f"ape-{name}.{suffix}").write_bytes(body)
    bases={"ape":mac(),"wv":wv(),"mpc":sv8(),"mp3":(owned/"base.mp3").read_bytes()}
    common=[entry("Title","Owned — صورة"),entry("Artist","One\0Two"),entry("Track","03/12"),entry("Disc","1/2"),entry("Album Artist","Artist"),entry("CUSTOM","a\0b"),entry("Cover Art (Front)",b"front.png\0"+png(),2)]
    for suffix,body in bases.items():
        put("base",suffix,body);put("structured",suffix,body+ape(common))
        put("mutagen",suffix,body);path=owned/f"ape-mutagen.{suffix}";tags=APEv2();tags["Title"]="Mutagen — صورة";tags["Artist"]=["One","Two"];tags["Track"]="3/12";tags["Cover Art (Back)"]=APEBinaryValue(b"back.png\0"+png());tags.save(path)
        put("id3v1",suffix,body+ape(common)+b"TAG"+b"Legacy".ljust(30,b"\0")+b"Artist".ljust(30,b"\0")+b"Album".ljust(30,b"\0")+b"2026"+bytes(28)+b"\0\x03\x11")
        put("footer-only",suffix,body+ape(common,header=False))
        put("v1",suffix,body+ape(common,version=1000,header=False))
    ff=owned/"ape-ffmpeg.wv"
    run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.2","-c:a","wavpack","-map_metadata","-1","-y",ff])
    for rate in [12345,96000]:
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i",f"anullsrc=r={rate}:cl=stereo","-t","0.2","-c:a","wavpack","-map_metadata","-1","-y",owned/f"ape-ffmpeg-{rate}.wv"])
    for ch in ["mono","5.1"]:
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i",f"anullsrc=r=48000:cl={ch}","-t","0.2","-c:a","wavpack","-y",owned/f"ape-ffmpeg-{ch}.wv"])
    aliases=["album artist","MusicBrainz_AlbumStatus","MUSICBRAINZ_ALBUMTYPE","LEAD_PERFORMER","DATE_RECORDED","DATE_RELEASED","DATE_ORIGINAL","ENCODED_BY","PART_NUMBER","TOTAL_PARTS","TOTAL_DISCS","CATALOG_NUMBER","PUBLISHER","REMIXED_BY","CONTENT_GROUP","DJMIXER","MusicBrainz Track Id","YEAR","DISC","ISRC","LABEL"]
    put("aliases","ape",mac()+ape([entry(n,"value") for n in aliases]))
    put("conflicts","ape",mac()+ape([entry("Artist","A"),entry("artist","B"),entry("Track","3/12"),entry("TrackTotal","20"),entry("Title","a\0b"),entry("TITLE","a"),entry("Empty",""),entry("Nulls","\0\0"),entry("Runs","\0a\0\0b\0")]))
    put("invalid-keys","ape",mac()+ape([entry(n,"value") for n in ["Bad=Key","~","", "TITLE\n","صورة","ID3","TAG","OggS","MP+"]]))
    put("latin1","ape",mac()+ape([entry("Title",b"caf\xe9"),entry("Artist",b"\xff\0\xfe"),entry("Bad=Key",b"\xff")],version=1000))
    put("noise","ape",mac()+ape([entry("Encoder","Lavf owned"),entry("Tool Name","Lavc owned"),entry("Encoded_By","libavcodec owned"),entry("Encoder",b"Lavf\0stamp")]))
    for flags in [1,2,4,6,0x80000000]:put(f"flags-{flags}","ape",mac()+ape([entry("Title","value",flags),entry("Cover Art (Front)",b"art\0"+png(),flags)]))
    for name,values in [("cover-empty",[entry("Cover Art (Front)",b"art\0",2)]),("cover-unterminated",[entry("Cover Art (Front)",b"art",2)]),("cover-unknown",[entry("Cover Art (Front)",b"art\0junk",2)]),("cover-duplicates",[entry("Cover Art (Front)",b"art\0"+png(),2),entry(" cover art (back) ",b"art\0"+png(),4)]),("cover-large",[entry("Cover Art (Front)",b"art\0"+png()+bytes(1100000),2)]),("binary-large",[entry("Binary",bytes(1100000),2)])]:put(name,"ape",mac()+ape(values))
    for version in [3790,3800,3890,3900,3950,3970,3980,3990,5000]:
        for flags in ([0,1,8] if version<3980 else [0]):put(f"mac-{version}-{flags}","ape",mac(version,flags=flags)+ape(common))
    for name,off,n in [("descriptor-small",8,0),("descriptor-huge",8,0xffffffff),("header-huge",12,0xffffffff),("header-small",12,0)]:
        b=bytearray(mac());struct.pack_into("<I",b,off,n);put(name,"ape",b+ape(common))
    put("mac-zero","ape",mac(frames=0)+ape(common))
    put("wv-unknown-samples","wv",wv(samples=0xffffffff)+ape(common))
    put("wv-high-count","wv",wv(samples=5,high=0x12)+ape(common))
    put("wv-float","wv",wv(flags=(10<<23)|(1<<12)|3|128)+ape(common))
    put("wv-shift","wv",wv(flags=(10<<23)|(1<<12)|3|(8<<13))+ape(common))
    put("wv-multichannel","wv",wv(flags=(10<<23)|1)+wv(flags=(10<<23)|(1<<12)|1|4)+ape(common))
    put("wv-zero","wv",wv(samples=0)+ape(common))
    sub=lambda i,b:bytes((i|(64 if len(b)%2 else 0),(len(b)+1)//2))+b+bytes(len(b)%2)
    put("wv-custom-rate","wv",wv(flags=(15<<23)|(1<<12)|1,body=sub(0x27,(12345).to_bytes(3,"little"))+sub(0x0a,bytes(40)))+ape(common))
    put("wv-dsd","wv",wv(flags=(15<<23)|(1<<12)|(1<<31),body=sub(0x27,(2822400).to_bytes(3,"little"))+sub(0x0e,bytes(40)))+ape(common))
    put("wv-subblock-overrun","wv",wv(flags=(15<<23)|(1<<12),body=b"\x27\xff"+bytes(10))+ape(common))
    for version in [0x401,0x402,0x410,0x411]:put(f"wv-version-{version}","wv",wv(version=version))
    for version in [7,23,6]:put(f"sv7-{version}","mpc",sv7(version=version)+ape(common))
    put("sv7-zero","mpc",sv7(frames=0)+ape(common))
    put("sv8-unknown-rate","mpc",sv8(rate=7)+ape(common))
    put("sv8-silence","mpc",sv8(silence=1000)+ape(common))
    put("sv8-no-samples","mpc",sv8(silence=10000)+ape(common))
    put("sv8-version","mpc",sv8(version=9)+ape(common))
    put("sv8-leading-id3","mpc",tag([frame("TIT2",b"\x03Legacy"),frame("TPE1",b"\x03One")])+sv8()+ape(common))
    put("mp3-id3-authority","mp3",(owned/"mutagen-v24.mp3").read_bytes()+ape([entry("Title","APE title"),entry("Artist","One\0Different"),entry("Track","03/12"),entry("Cover Art (Front)",b"art\0"+png(),2)]))
    def ct(start,title=None,tail=None):
        payload=var(start)+bytes(4)
        if title is not None:payload+=struct.pack("<IIII",2000,0,1,0)+bytes(8)+entry("Title",title)
        if tail is not None:payload+=tail
        return packet("CT",payload)
    sh=packet("SH",bytes(4)+b"\x08"+var(9600)+var(0)+b"\x20\x10")
    put("chapters","mpc",b"MPCK"+sh+packet("AP",bytes(64))+ct(1200,"First")+ct(4800,"Second")+packet("SE",b"")+ape(common))
    put("chapters-invisible","mpc",b"MPCK"+sh+ct(1200,"Invisible")+packet("AP",bytes(64))+packet("SE",b"")+ape(common))
    put("chapters-faults","mpc",b"MPCK"+sh+packet("AP",bytes(64))+packet("CT",b"\x80")+ct(1000,tail=b"short")+ct(2000,"A\0B")+packet("SE",b"")+ape(common))
    # SO points to an actual ST packet; titles after it are read even without a final SE.
    st=packet("ST",bytes(8));so=packet("SO",var(3+1+len(packet("AP",bytes(64)))))
    put("chapters-seek","mpc",b"MPCK"+sh+so+packet("AP",bytes(64))+st+ct(1200,"First")+ct(4800,"Second")+packet("SE",b"")+ape(common))
    # Malicious SO aliases an ST/CT sequence inside encoded AP audio.
    hidden=bytes(8)+st+ct(1200,"LEAK")+packet("SE",b"")+bytes(8)
    so=packet("SO",var(4+3+8))
    put("chapter-audio-alias","mpc",b"MPCK"+sh+so+packet("AP",hidden)+packet("SE",b"")+ape(common))
    for name,tagbytes in [("lying-header",ape(common,header=False,flags=0x80000000)),("header-record",ape(common,header=False,flags=0x20000000)),("count-too-many",ape(common,count=0xffffffff)),("count-zero",ape(common,count=0)),("item-size-overrun",ape([struct.pack("<II",0xffffffff,0)+b"Title\0value"])),("item-key-unterminated",ape([struct.pack("<II",3,0)+b"Title"])),("item-short",ape([b"tiny"])),("unknown-tag-version",ape(common,version=9999))]:put(name,"ape",mac()+tagbytes)
    for suffix in ["ape","wv","mpc","mp3"]:
        data=bases[suffix]+ape(common)
        for cut in sorted(set([1,3,4,5,6,10,24,31,32,51,52,75,76,100,len(data)-1,len(data)-16,len(data)-32])):
            put(f"truncated-{cut}",suffix,data[:cut])
    return owned


def generate_iff(source):
    """Owned RIFF/RF64/BW64 and AIFF/C structural vectors; external only."""
    import math
    owned=CACHE/"owned";owned.mkdir(exist_ok=True)
    def chunk(key,body,be=False,size=None,pad=True):
        return key.encode()+struct.pack(">I" if be else "<I",len(body) if size is None else size)+body+(bytes(len(body)%2) if pad else b"")
    def container(chunks,form="WAVE",declared=None,tail=b""):
        be=form!="WAVE";body=form.encode()+b"".join(chunks)
        return (b"FORM" if be else b"RIFF")+struct.pack(">I" if be else "<I",len(body) if declared is None else declared)+body+tail
    def fmt(tag=1,rate=48000,ch=2,bits=16,byteRate=None,align=None):
        align=ch*((bits+7)//8) if align is None else align
        return struct.pack("<HHIIHH",tag,ch,rate,rate*align if byteRate is None else byteRate,align,bits)
    def rate80(rate):
        if rate==0:return bytes(10)
        exp=int(math.floor(math.log2(abs(rate))));mant=int(abs(rate)*2**(63-exp))
        return struct.pack(">HQ",exp+16383+(0x8000 if rate<0 else 0),mant)
    def comm(comp=None,count=16,rate=48000,bits=16,ch=2):
        return struct.pack(">HIH",ch,count,bits)+rate80(rate)+(comp if comp else b"")
    def info(items,pads=True):
        return b"INFO"+b"".join(chunk(k,v if isinstance(v,bytes) else v.encode()+b"\0",pad=pads) for k,v in items)
    def wav(extra=[],f=None,data=bytes(64),declared=None,tail=b""):
        return container([chunk("fmt ",fmt() if f is None else f),*extra,chunk("data",data)],declared=declared,tail=tail)
    def aiff(extra=[],comp=None,c=None,data=bytes(64),offset=0,declared=None,tail=b""):
        return container([chunk("COMM",comm(comp) if c is None else c,True),*extra,chunk("SSND",struct.pack(">II",offset,0)+data,True)],form="AIFC" if comp else "AIFF",declared=declared,tail=tail)
    def put(name,body,suffix="wav"):(owned/f"iff-{name}.{suffix}").write_bytes(body)
    keys=["INAM","IART","IPRD","ICRD","IGNR","ICMT","ICOP","IPRT","ITRK","ISFT","ITCH","IENG","ILNG","ISBJ"]
    common=info([(k,"4/9" if k=="IPRT" else "Owned — صورة") for k in keys])
    embedded=tag([frame("TIT2",b"\x03ID3 authoritative"),frame("APIC",b"\0image/png\0\x03owned\0"+png()),frame("SYLT",b"\0eng\x02\x01\0line\0"+struct.pack(">I",10)),frame("CHAP",b"one\0"+struct.pack(">IIII",0,1000,0xffffffff,0xffffffff)+frame("TIT2",b"\x03Chapter"))])
    for suffix in ["wav","aiff"]:
        be=suffix=="aiff";base=aiff if be else wav
        texts=[chunk("NAME",b"Native",True),chunk("AUTH",b"Artist",True),chunk("(c) ",b"Copyright",True),chunk("ANNO",b"Comment",True)] if be else [chunk("LIST",common)]
        put("base",base(),suffix);put("native",base(texts),suffix);put("id3",base(texts+[chunk("ID3 " if be else "id3 ",embedded,be)]),suffix)
        put("duplicate-id3",base([chunk("ID3 ",embedded,be),chunk("id3 ",tag([frame("TIT2",b"\x03Second")]),be)]),suffix)
        put("corrupt-then-id3",base([chunk("ID3 ",b"bad",be),chunk("id3 ",embedded,be)]),suffix)
        put("no-valid-id3",base([chunk("ID3 ",b"bad",be)]),suffix)
        put("opaque",base([chunk(k,bytes(100),be) for k in (["bext","iXML","cue ","LIST","JUNK"] if not be else ["FVER","MARK","COMT","INST","JUNK"])]),suffix)
        for n in [0,1,4,8,20,0xffffffff]:put(f"declared-{n}",base(texts,declared=n),suffix)
        for tail in [b"junk",b"TAG"+bytes(125),bytes(7)]:
            put(f"outer-{len(tail)}",base(tail=tail),suffix)
            b=base()+tail;b=b[:4]+struct.pack(">I" if be else "<I",len(b)-8)+b[8:];put(f"inner-{len(tail)}",b,suffix)
        for size in [1,10,0xffffffff]:
            body=(chunk("COMM",comm(),True) if be else chunk("fmt ",fmt()))+chunk("SSND" if be else "data",bytes(8)+bytes(16) if be else bytes(16),be,size=size)
            put(f"audio-size-{size}",container([body],form="AIFF" if be else "WAVE"),suffix)
        for key in ["JUNK","NAME" if be else "LIST"]:
            put(f"oversized-{key}",container([chunk(key,b"INFOjunk" if key=="LIST" else b"value",be,size=5000)],form="AIFF" if be else "WAVE"),suffix)
        seed=base(texts+[chunk("ID3 ",embedded,be)])
        for cut in [1,3,4,8,11,12,15,19,25,31,39,50,80,len(seed)-1]:put(f"cut-{cut}",seed[:cut],suffix)
    for name,items in [("empty",[("INAM",b"\0"),("IART",b"")]),("duplicate",[("INAM","One"),("INAM","Two"),("IPRT","03/12"),("ITRK","9/99")]),("latin1",[("INAM",b"caf\xe9\0")]),("nul-tail",[("INAM",b"Title\0lost")]),("zeros",[("INAM",b"Title\0\0\0")]),("noise",[("ISFT","Lavf owned")]),("total-only",[("IPRT","/12")])]:put(f"info-{name}",wav([chunk("LIST",info(items))]))
    put("info-duplicates",wav([chunk("LIST",info([("INAM","One")])),chunk("LIST",info([("INAM","Two")]))]))
    put("info-missing-pad",wav([chunk("LIST",info([("INAM",b"abc"),("IART",b"def")],False))]))
    for tail in [b"bad",bytes(9),b"INAM\xff\xff\xff\xffbad",b"\x01xxx"+bytes(4)]:put(f"info-tail-{tail.hex()}",wav([chunk("LIST",info([("INAM","Title")])+tail)]))
    for n in [0,1,2,3,6,7,10,17,80,85,255,0x160,0x161,0x162,0x163,65534,0x1234]:
        for fact in [None,160,0,0xffffffff]:
            extras=[] if fact is None else [chunk("fact",struct.pack("<I",fact)+bytes(100))]
            put(f"fmt-{n}-fact-{fact}",wav(extras,fmt(n,byteRate=1600),data=bytes(320)))
    put("fmt-invalid-first",container([chunk("fmt ",b"short"),chunk("fmt ",fmt()),chunk("data",bytes(64))]))
    put("fmt-missing",container([chunk("data",bytes(64))]))
    put("fmt-zero-rate",wav(f=fmt(rate=0)))
    put("fmt-high-rate",wav(f=fmt(rate=0xffffffff,byteRate=0xffffffff)))
    for comp in [b"NONE",b"sowt",b"twos",b"in24",b"in32",b"fl32",b"FL64",b"ulaw",b"alaw",b"ima4",b"MAC3",b"mac6",b"zzzz",b"alac",b"fLaC",b"mp4a",b"ac-3",b"ec-3",b".mp3",b"ms\0\x06",b"ms\0\x02",b"\x01\x02\x03\x04",bytes(4)]:
        put(f"aifc-{comp.hex()}",aiff(comp=comp,data=bytes(512)),"aifc")
    for name,c,data,offset in [("overstates",comm(count=100),bytes(64),0),("offset",comm(),bytes(64),8),("offset-past",comm(),bytes(64),100),("no-channel",comm(ch=0),bytes(64),0),("no-bits",comm(bits=0),bytes(64),0),("negative-rate",comm(rate=-48000),bytes(64),0),("zero-rate",comm(rate=0),bytes(64),0),("odd-depth",comm(bits=17),bytes(64),0)]:put(f"aiff-{name}",aiff(c=c,data=data,offset=offset),"aiff")
    put("aiff-latin1",aiff([chunk("NAME",b"caf\xe9\0",True),chunk("NAME",b"",True)]),"aiff")
    def rf64(extra=[],form=b"RF64",data=bytes(64),table=[],sample=16,riff=None,dataSize=None):
        entries=b"".join(k.encode()+struct.pack("<Q",n) for k,n in table)
        ds=struct.pack("<QQQI",0,len(data) if dataSize is None else dataSize,sample,len(table))+entries
        body=chunk("ds64",ds)+chunk("fmt ",fmt())+b"".join(extra)+chunk("data",data,size=0xffffffff)
        ds=struct.pack("<QQQI",len(body)+4 if riff is None else riff,len(data) if dataSize is None else dataSize,sample,len(table))+entries
        body=chunk("ds64",ds)+body[len(chunk("ds64",ds)):]
        return form+struct.pack("<I",0xffffffff)+b"WAVE"+body
    for form in [b"RF64",b"BW64"]:
        put(form.decode().lower(),rf64([chunk("LIST",common),chunk("id3 ",embedded)],form))
    put("rf64-table",rf64([chunk("JUNK",bytes(6),size=0xffffffff),chunk("JUNK",bytes(10),size=0xffffffff)],table=[("JUNK",6),("JUNK",10)]))
    put("rf64-data-overrun",rf64(dataSize=10000));put("rf64-no-ds64",b"RF64"+struct.pack("<I",0xffffffff)+b"WAVE"+chunk("fmt ",fmt()))
    put("rf64-short-ds64",b"RF64"+struct.pack("<I",0xffffffff)+b"WAVE"+chunk("ds64",bytes(8)))
    put("rf64-high-riff",rf64(riff=0xffffffffffffffff));put("rf64-high-data",rf64(dataSize=0xffffffffffffffff))
    put("rf64-trusted-end",rf64(riff=40))
    for codec,suffix in [("pcm_s16le","wav"),("pcm_f32le","wav"),("adpcm_ima_wav","wav"),("pcm_s16be","aiff"),("pcm_alaw","aifc"),("adpcm_ima_qt","aifc")]:
        target=owned/f"iff-ffmpeg-{codec}.{suffix}"
        run(["ffmpeg","-nostdin","-v","error","-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.2","-c:a",codec,"-metadata","title=Owned","-f","wav" if suffix=="wav" else "aiff","-y",target])
    large=tag([frame("TIT2",b"\x03Large cover"),frame("APIC",b"\0image/png\0\x03owned\0"+png()+bytes(1100000))])
    put("large-art",wav([chunk("id3 ",large)]))
    put("large-art",aiff([chunk("ID3 ",large,True)]),"aiff")
    put("large-opaque",wav([chunk("bext",bytes(1100000)),chunk("LIST",b"adtl"+bytes(1100000))]))
    put("track-number-only",wav([chunk("LIST",info([("IPRT","03/")]))]))
    for name,ratebytes in [("nan",bytes.fromhex("7fff8000000000000000")),("zero-mantissa",bytes.fromhex("7fff0000000000000000")),("huge",bytes.fromhex("41008000000000000000"))]:
        c=comm();put(f"rate-{name}",aiff(c=c[:8]+ratebytes),"aiff")
    for name,sample in [("normal",160),("zero",0),("huge",0xffffffffffffffff)]:
        b=bytearray(rf64([chunk("fact",struct.pack("<I",0xffffffff))],data=bytes(320),sample=sample))
        b[56:72]=fmt(17,byteRate=1600);put(f"rf64-fact-{name}",b)
    put("fact-accepted",wav([chunk("fact",struct.pack("<I",160))],fmt(17,byteRate=96000),data=bytes(320)))
    b=bytearray(rf64([chunk("fact",struct.pack("<I",0xffffffff))],data=bytes(320),sample=160));b[56:72]=fmt(17,byteRate=96000)
    put("rf64-fact-accepted",b)
    for name,native,title in [("turkish-i","ı","I"),("dotted-i","İ","i"),("sigma","ς","Σ"),("kelvin","K","k"),("nel","\u0085Title\u0085","Title")]:
        id3=tag([frame("TIT2",b"\x03"+title.encode())]);put(f"fold-{name}",wav([chunk("LIST",info([("INAM",native)])),chunk("id3 ",id3)]))
    return owned

def generate_asf(source):
    """Owned ASF objects and mutagen/ffmpeg silence, external only."""
    import uuid,mutagen
    from mutagen.asf import ASF,ASFUnicodeAttribute,ASFByteArrayAttribute,ASFDWordAttribute
    if mutagen.version_string!="1.47.0":raise SystemExit("ASF vectors require mutagen 1.47.0")
    owned=CACHE/"owned";owned.mkdir(exist_ok=True)
    guid=lambda s:uuid.UUID(s).bytes_le
    ids={"header":"75b22630-668e-11cf-a6d9-00aa0062ce6c","file":"8cabdca1-a947-11cf-8ee4-00c00c205365","stream":"b7dc0791-a9b7-11cf-8ee6-00c00c205365","audio":"f8699e40-5b4d-11cf-a8fd-00805f5c442b","content":"75b22633-668e-11cf-a6d9-00aa0062ce6c","ext":"d2d0a440-e307-11d2-97f0-00a0c95ea850","extension":"5fbf03b5-a92e-11cf-8ee3-00c00c205365","metadata":"c5f8cbea-5baf-4877-8467-aa8c44fa4cca","library":"44231c94-9498-49d1-a141-1d134e457054","marker":"f487cd01-a951-11cf-8ee6-00c00c205365","data":"75b22636-668e-11cf-a6d9-00aa0062ce6c","codec":"86d15240-311d-11d0-a3a4-00a0c90348f6","bitrate":"7bf875ce-468d-11d1-8d82-006097c9a2b2","encryption":"2211b3fb-bd23-11d2-b4b7-00a0c955fc6e","extencryption":"298ae614-2622-4c17-b935-dae07ee9289c"}
    g={k:guid(v) for k,v in ids.items()}
    def obj(k,body,size=None):return g.get(k,guid('11111111-2222-3333-4444-555555555555'))+struct.pack('<Q',len(body)+24 if size is None else size)+body
    def u(s):return (s+'\0').encode('utf-16le')
    def file(play=12000000,preroll=200,bitrate=128000):
        b=bytearray(80);struct.pack_into('<Q',b,40,play);struct.pack_into('<Q',b,56,preroll);struct.pack_into('<I',b,76,bitrate);return obj('file',b)
    def stream(tag=0x161,rate=48000,ch=2,bits=16,byteRate=16000,extra=b'',audio=True):
        w=struct.pack('<HHIIHH',tag,ch,rate,byteRate,4,bits)+extra
        b=(g['audio'] if audio else bytes(16))+bytes(24)+struct.pack('<IIHI',len(w),0,1,0)+w
        return obj('stream',b)
    def content(vals):
        vals=[u(v) for v in vals];return obj('content',struct.pack('<5H',*[len(v) for v in vals])+b''.join(vals))
    def descriptor(name,value,kind=0,meta=False):
        name=u(name);value=u(value) if isinstance(value,str) else value
        return (struct.pack('<HHHHI',0,0,len(name),kind,len(value))+name+value if meta else struct.pack('<H',len(name))+name+struct.pack('<HH',kind,len(value))+value)
    def records(items,kind='ext'):
        return obj(kind,struct.pack('<H',len(items))+b''.join(descriptor(*it,meta=kind!='ext') for it in items))
    def extension(children,declared=None):
        body=b''.join(children);return obj('extension',bytes(18)+struct.pack('<I',len(body) if declared is None else declared)+body)
    def picture(image=None,mime='image/png',desc='Owned',kind=3,declared=None):
        image=png() if image is None else image
        return bytes((kind,))+struct.pack('<I',len(image) if declared is None else declared)+u(mime)+u(desc)+image
    def marker(items,count=None,name='',tail=b''):
        name=u(name);body=bytes(16)+struct.pack('<IHH',len(items) if count is None else count,0,len(name))+name
        for at,desc in items:
            raw=u(desc);body+=struct.pack('<QQI I H I',0,at,30+len(raw),0,0,len(raw)//2)+raw
        return obj('marker',body+tail)
    def asf(extra=[],props=None,audio=None,count=None,headerSize=None,data=True,dataSize=None,packets=bytes(128),tail=b''):
        children=[file() if props is None else props,stream() if audio is None else audio,*extra];children=[x for x in children if x]
        body=struct.pack('<I',len(children) if count is None else count)+b'\x01\x02'+b''.join(children)
        h=obj('header',body,headerSize)
        return h+(obj('data',bytes(26)+packets,dataSize) if data else b'')+tail
    def put(name,body,suffix='wma'):(owned/f'asf-{name}.{suffix}').write_bytes(body)
    common=[('WM/AlbumTitle','Album',0),('Author','One',0),('Author','Two',0),('WM/TrackNumber','03/12',0),('WM/PartOfSet','1/2',0),('WM/Picture',picture(),1)]
    put('base',asf());put('structured',asf([content(['Title','Artist','Copyright','Comment','Rating']),records(common)]))
    put('dedup',asf([content(['Same','Artist','','','']),records([('Title','Same',0),('Title','Same',0),('Author','Artist',0),('WM/AlbumTitle','Album',0)])]))
    put('conflicts',asf([content(['One','','','','']),records([('Title','Two',0),('WM/Year','2026',0),('WM/OriginalReleaseYear','1999',0),('WM/OriginalReleaseTime','2000',0)])]))
    aliases=list(__import__('re').findall(r'"([^"]+)":\s*tag\.',(source/'internal/mapping/asf.go').read_text()))
    put('aliases',asf([records([(k,'value',0) for k in aliases])]))
    suppressed=list(__import__('re').findall(r'"([^"]+)":\s*""',(source/'internal/mapping/asf.go').read_text()))
    put('suppressed',asf([records([(k,'not a tag',0) for k in suppressed])]))
    put('fallback',asf([records([(k,'value',0) for k in ['CUSTOM','WM/CUSTOM','Vendor/Album Artist','WM/Bad=Key','WM/~','WM/صورة','WM/',' A/B/C ','WM/PUBLISHER']])]))
    for name,kind,value in [('unicode',0,u('Text')),('bool16',2,struct.pack('<H',3)),('bool32',2,struct.pack('<I',0)),('boolbad',2,b'x'),('dword',3,struct.pack('<I',0xffffffff)),('qword',4,struct.pack('<Q',0xffffffffffffffff)),('word',5,struct.pack('<H',65535)),('bytes',1,b'blob'),('unknown',99,b'unknown'),('shortnum',4,b'short'),('unpaired',0,b'\0\xd8\0\0'),('oddunicode',0,b'A\0B'),('interiornul',0,u('A\0B')),('empty',0,b'')]:
        put(f'value-{name}',asf([records([('WM/CUSTOM',value,kind)])]))
    for kind in ['metadata','library']:
        put(kind,asf([extension([records(common,kind)])]))
        put(f'{kind}-top',asf([records(common,kind)]))
    put('nested-conflicts',asf([records([('Title','One',0)]),extension([records([('Title','Two',0)],'metadata'),records([('WM/AlbumTitle','Album',0)],'library')])]))
    for n in [0,1,10,0xffffffff]:put(f'extension-size-{n}',asf([extension([records(common,'library')],n)]))
    for name,pic in [('short',b'x'),('no-mime',b'\x03'+bytes(4)+b'A\0'),('no-desc',b'\x03'+bytes(4)+u('image/png')+b'A\0'),('empty',picture(b'')),('unknown',picture(b'junk','image/custom')),('wrong-mime',picture(mime='image/jpeg')),('empty-mime',picture(mime='')),('declared-short',picture(declared=10)),('declared-long',picture(declared=0xffffffff)),('type255',picture(kind=255)),('linked',picture(b'https://example.invalid/art','-->'))]:
        put(f'picture-{name}',asf([records([('WM/Picture',pic,1)])]))
    put('pictures-multiple',asf([records([('WM/Picture',picture(),0),(' wm/picture ',picture(kind=4),1)])]))
    put('large-picture',asf([extension([records([('WM/Picture',picture(png()+bytes(1100000)),1)],'library')])]))
    put('large-binary',asf([extension([records([('WM/UnknownBlob',bytes(1100000),1)],'library')])]))
    put('opaque',asf([obj(k,bytes(110000)) for k in ['codec','bitrate','encryption','extencryption','unknown']]))
    put('markers',asf([marker([(7000000,'Second'),(1000000,'Preroll'),(2000000,'Zero'),(5000000,'First'),(5000000,'Equal')])]))
    put('marker-subms',asf([marker([(2004999,'Later'),(2001000,'Earlier')])]))
    put('marker-short',asf([marker([(2000000,'One')],count=3)]))
    put('marker-overflow',asf([marker([(0xffffffffffffffff,'Impossible'),(2000000,'Okay')])]))
    put('marker-cut-description',asf([marker([(3000000,'Cut')])[:-3]]))
    for tag in [0,1,2,3,6,7,10,17,80,85,255,0x160,0x161,0x162,0x163,65534,0x1234]:put(f'codec-{tag}',asf(audio=stream(tag)))
    for depth,cb in [(16,18),(24,18),(32,18),(24,17),(24,50)]:put(f'lossless-{depth}-{cb}',asf(audio=stream(0x163,bits=16,extra=struct.pack('<HH',cb,depth)+bytes(16))))
    for name,props in [('zero',file(0)),('below-preroll',file(1)),('overflow-play',file(0xffffffffffffffff)),('overflow-preroll',file(preroll=0xffffffffffffffff)),('no-preroll',file(preroll=0)),('huge',file(play=92233720368547758,preroll=0))]:put(f'duration-{name}',asf(props=props))
    put('bitrate-fallback',asf(audio=stream(byteRate=0)));put('bitrate-clamp',asf(audio=stream(byteRate=0xffffffff)))
    put('no-stream',asf(audio=b''));put('video',asf(audio=stream(audio=False)));put('duplicate-stream',asf([stream(0x163,rate=96000)]))
    put('no-fileprops',asf(props=b''));put('fileprops-short',asf(props=obj('file',bytes(20))))
    put('stream-short',asf(audio=obj('stream',bytes(20))))
    put('header-only',asf(data=False));put('data-short-size',asf(dataSize=40));put('data-overrun',asf(dataSize=10000));put('data-trailing-index',asf(tail=obj('unknown',bytes(100))))
    for n in [0,1,29,30,31,0xffffffff,0xffffffffffffffff]:put(f'header-size-{n}',asf([records(common)],headerSize=n))
    for n in [0,1,2,3,0xffffffff]:put(f'header-count-{n}',asf([records(common)],count=n))
    for n in [0,1,23,24,10000,0xffffffffffffffff]:put(f'object-size-{n}',asf([obj('ext',bytes(20),n)]))
    for body in [b'x',b'\x03\0',b'\x01\0\xff\xff',b'\x01\0\x02\0T\0\0\0\xff\xff']:
        put(f'bad-descriptor-{body.hex()}',asf([obj('ext',body)]))
    put('noise',asf([records([('WM/ToolName','Lavf owned',0),('WM/EncodedBy','Lavc owned',0)])]))
    seed=asf([records(common)])
    for cut in [1,3,4,10,15,16,23,24,29,30,31,54,100,200,len(seed)-1]:put(f'cut-{cut}',seed[:cut])
    for codec in ['wmav1','wmav2']:
        target=owned/f'asf-ffmpeg-{codec}.wma'
        run(['ffmpeg','-nostdin','-v','error','-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-t','0.2','-c:a',codec,'-metadata','title=Owned — صورة','-y',target])
        tagged=owned/f'asf-mutagen-{codec}.wma';shutil.copyfile(target,tagged);tags=ASF(tagged)
        tags['WM/AlbumTitle']=[ASFUnicodeAttribute('Album')];tags['Author']=[ASFUnicodeAttribute('One'),ASFUnicodeAttribute('Two')];tags['WM/TrackNumber']=[ASFDWordAttribute(3)];tags['WM/Picture']=[ASFByteArrayAttribute(picture())];tags.save()
    put('rate-over-int',asf(audio=stream(rate=0xffffffff)))
    hidden=bytes(16)+obj('data',bytes(26)+bytes(64))+bytes(70)
    real_header=30+len(file())+len(stream())
    put('data-audio-alias',asf(headerSize=real_header+50+16,packets=hidden))
    put('header-in-packets',asf(headerSize=real_header+50+16))
    put('unicode-keys',asf([records([(k,'value',0) for k in ['WM/ ALBUM ARTIST','WM/ß','WM/İSRC','\u0085Title\u0085']])]))
    for name,left,right in [('dotted','İ','i'),('sigma','ΟΣ','Οσ')]:
        put(f'fold-{name}',asf([content([left,'','','','']),records([('Title',right,0),('WM/OriginalReleaseYear',left,0),('WM/OriginalReleaseTime',right,0)])]))
    put('picture-lookalike',asf([records([('WM/Pıcture',picture(),1),('WM/Pİcture',picture(),1)])]))
    return owned

def generate_matroska(source):
    """Owned silence and deterministic EBML/tag/attachment/chapter vectors; external only."""
    owned=CACHE/'owned';owned.mkdir(exist_ok=True)
    def vint(n,width=None):
        width=width or next(w for w in range(1,9) if n<(1<<(7*w))-1)
        return ((1<<(7*width))|n).to_bytes(width,'big')
    def element(id,data=b'',unknown=False):
        return id.to_bytes((id.bit_length()+7)//8,'big')+(b'\xff' if unknown else vint(len(data)))+data
    def uint(id,n,width=None):return element(id,n.to_bytes(width or max(1,(n.bit_length()+7)//8),'big'))
    def text(id,s):return element(id,s if isinstance(s,bytes) else s.encode())
    def number(id,n,width=8):return element(id,struct.pack('>d' if width==8 else '>f',n))
    def simple(name,value=None,sub=b'',binary=None):
        return element(0x67c8,text(0x45a3,name)+(text(0x4487,value) if value is not None else b'')+(element(0x4485,binary) if binary is not None else b'')+sub)
    def group(tags,level=None,target=None,uid=None):
        targets=(uint(0x68ca,level) if level is not None else b'')+(text(0x63ca,target) if target is not None else b'')+(uint(uid,0) if uid else b'')
        return element(0x7373,element(0x63c0,targets)+b''.join(tags))
    def tagblock(*groups):return element(0x1254c367,b''.join(groups))
    def track(codec='A_OPUS',rate=48000,channels=2,bits=16,type=2,private=None,output=None,audio=b''):
        geom=number(0xb5,rate)+uint(0x9f,channels)+uint(0x6264,bits)+(number(0x78b5,output) if output is not None else b'')
        return element(0xae,uint(0xd7,1)+uint(0x83,type)+text(0x86,codec)+element(0xe1,geom)+audio+(element(0x63a2,private) if private is not None else b''))
    def info(title=None,duration=1000,scale=1000000):
        return element(0x1549a966,uint(0x2ad7b1,scale)+number(0x4489,duration)+(text(0x7ba9,title) if title is not None else b''))
    cluster=element(0x1f43b675,uint(0xe7,0)+element(0xa3,b'\x81\0\0\x80'+bytes(200)))
    def file(children=(),dt='matroska',clusters=True,tracks=None,information=None,unknown=False):
        header=element(0x1a45dfa3,text(0x4282,dt))
        body=(info() if information is None else information)+element(0x1654ae6b,track() if tracks is None else tracks)+b''.join(children)+(cluster if clusters else b'')
        return header+element(0x18538067,body,unknown)
    def put(name,body,suffix='mka'):(owned/f'matroska-{name}.{suffix}').write_bytes(body)
    # The mapping source is fetched and hashed in the external cache.
    import re
    names=re.findall(r'"([A-Z_ -]+)"\s*:\s*tag\.',(source/'internal/mapping/matroska.go').read_text().split('// matroskaNames')[0])
    put('aliases',file([tagblock(group([simple(n,'Owned '+n) for n in names]+[simple('COUNTRY','AR'),simple('CUSTOM','value'),simple('PART_NUMBER','04/09'),simple('DISC','0/12')]))]))
    put('empty',file([tagblock(group([simple('TITLE',''),simple('ARTIST',''),simple('ALBUM',None),simple('GENRE','')]))],information=info('')))
    put('duplicates',file([tagblock(group([simple('ARTIST','One'),simple('ARTIST','One'),simple('TITLE','Echo'),simple('TITLE',' echo ')]))],information=info('Echo')))
    put('scopes',file([tagblock(group([simple('TITLE','Track'),simple('ARTIST','One'),simple('GENRE','Rock')],30),group([simple('TITLE','Album'),simple('ARTIST','Two'),simple('GENRE',' rock ')],50),group([simple('TITLE','Edition')],60),group([simple('TITLE','Chapter')],uid=0x63c4))],information=info('Album')))
    put('multiple-tags',file([tagblock(group([simple('TITLE','First')])),tagblock(group([simple('TITLE','Second')]))]))
    for n in ['COLLECTION','EDITION','ISSUE','VOLUME','OPUS','SEASON','SEQUEL','ALBUM','OPERA','CONCERT','MOVIE','EPISODE','PART','SESSION','TRACK','SONG','CHAPTER','SUBTRACK','PART_BIS','MOVEMENT','SCENE','SHOT','UNKNOWN',' album ']:
        put('target-'+n.strip().lower().replace(' ','-'),file([tagblock(group([simple('TITLE',n)],target=n))]))
    for level in [0,10,30,39,40,50,59,60,70,2**64-1]:
        put(f'level-{level}',file([tagblock(group([simple('TITLE','Owned')],level))]))
    for uid in [0x63c5,0x63c9,0x63c4]:put(f'uid-{uid:x}',file([tagblock(group([simple('TITLE','Owned')],50,uid=uid))]))
    put('invalid-keys',file([tagblock(group([simple(n,'value') for n in ['','  ','BAD=KEY','العربية','WM/ß','ß','İSRC','BPS','_STATISTICS_WRITING_APP','DURATION','NUMBER_OF_BYTES','x~']]))]))
    put('unicode-values',file([tagblock(group([simple('TITLE',b'A\xff\xfeB'),simple('ARTIST','العربية'),simple('CUSTOM',b'\xe0\x80\x80'),simple('COMMENT','inside\0nul\0\0')]))],information=info(b'\xff\xfe')))
    put('invalid-name',file([tagblock(group([simple(b'bad\xff\xfe','value')]))]))
    put('nested',file([tagblock(group([simple('ARTIST','Parent',simple('COUNTRY','AR',simple('TITLE','Nested')),binary=bytes(20)),simple('ALBUM',None,simple('TITLE','Child'))]))]))
    nested=simple('TITLE','Leaf')
    for _ in range(48):nested=simple('COUNTRY',None,nested)
    put('nested-deep',file([tagblock(group([nested]))]))
    put('binary-large',file([tagblock(group([simple('CUSTOM',None,binary=bytes(1100000))]))]))
    put('opaque',file([element(0x114d9b74,bytes(11000)),element(0x1c53bb6b,bytes(12000)),element(0xec,bytes(13000)),element(0xbf,b'abcd')]))
    png=(owned/'cover.png').read_bytes()
    def attachment(name='cover.png',mime='image/png',data=png,description='Owned cover'):
        return element(0x61a7,text(0x466e,name)+text(0x4660,mime)+text(0x467e,description)+(element(0x465c,data) if data is not None else b''))
    def attachments(*items):return element(0x1941a469,b''.join(items))
    for name in ['cover.png','small_cover.png','Cover_1.bin','small_cover_2','cover','cover_letter.bin','cover_.bin','booklet.png','font.ttf']:
        put('attachment-'+name.replace('.','-'),file([attachments(attachment(name,'application/octet-stream'))]))
    for mime in ['image/png','IMAGE/JPEG','image/unknown','application/octet-stream','application/pdf','', '-->']:
        put('mime-'+mime.replace('/','-').replace('>','gt'),file([attachments(attachment('cover.png',mime))]))
    for name,data in [('empty',b''),('unknown',b'not an image'),('absent',None),('large',png+bytes(1100000))]:put('picture-'+name,file([attachments(attachment(data=data))]))
    put('picture-wrongmime',file([attachments(attachment(mime='image/jpeg'))]))
    put('picture-description',file([attachments(attachment(description=b'A\xff\xfeB'))]))
    put('attachments-multiple',file([attachments(attachment(),attachment('small_cover.png')),attachments(attachment('booklet.png'),attachment('font.ttf','font/ttf',bytes(10000)))]))
    def display(title='Owned',lang='eng',ietf='en-US'):
        return element(0x80,text(0x85,title)+text(0x437c,lang)+text(0x437d,ietf))
    def atom(start=0,end=None,title='Owned',flags=b'',extra=b'',lang='eng',ietf='en-US'):
        return element(0xb6,uint(0x91,start)+(uint(0x92,end) if end is not None else b'')+display(title,lang,ietf)+flags+extra)
    def edition(atoms,default=False):return element(0x45b9,(uint(0x45db,1) if default else b'')+b''.join(atoms))
    def chapters(*eds):return element(0x1043a770,b''.join(eds))
    put('chapters',file([chapters(edition([atom(200000000,400000000,'Second'),atom(0,200000000,'First'),atom(700000000,None,'Open')]))]))
    put('editions',file([chapters(edition([atom(title='Fallback')]),edition([atom(title='Default')],True),edition([atom(title='Other')],True))]))
    put('chapter-flags',file([chapters(edition([atom(flags=uint(0x98,1)+uint(0x4598,0))]))]))
    put('chapter-languages',file([chapters(edition([atom(lang='und',ietf='UND'),atom(1000000,title=b'bad\xff',lang=b'\xff',ietf='ar-EG')]))]))
    put('chapter-subms',file([chapters(edition([atom(490000,title='Later'),atom(100000,title='Earlier'),atom(100000,title='Stable')]))]))
    put('chapter-overflow',file([chapters(edition([atom(2**64-1,2**64-1),atom(0,2000000000,'Past duration'),atom(10,5,'Invalid end')]))]))
    put('chapter-nested',file([chapters(edition([atom(extra=atom(title='Nested')+display('Second display'))]))]))
    put('chapters-empty',file([chapters()]))
    put('chapters-duplicate',file([chapters(edition([atom(title='First')])),chapters(edition([atom(title='Ignored')]))]))
    for codec in ['A_FLAC','A_OPUS','A_VORBIS','A_AAC','A_AAC/MPEG4/MAIN','A_AAC/MPEG4/LC','A_AAC/MPEG4/LC/SBR','A_AAC/MPEG2/SSR','A_AAC/MPEG4/LTP','A_MPEG/L3','A_MPEG/L2','A_AC3','A_EAC3','A_PCM/INT/LIT','A_ALAC','A_DTS','V_TEST','unknown']:
        put('codec-'+codec.replace('/','-'),file(tracks=track(codec)))
    for name,private in [('lc',bytes.fromhex('1190')),('he',bytes.fromhex('2b920800')),('ps',bytes.fromhex('eb920800')),('short',b'\xff'),('huge',bytes.fromhex('1190')+bytes(1100000))]:
        put('aac-'+name,file(tracks=track('A_AAC',24000,1,private=private)))
    put('aac-output',file(tracks=track('A_AAC/MPEG4/LC/SBR',24000,1,output=44100,private=bytes.fromhex('eb920800'))))
    put('non-aac-private',file(tracks=track('A_FLAC',private=bytes(1100000))))
    put('no-tracks',file(tracks=b''));put('video',file(tracks=track('V_TEST',type=1)));put('mixed',file(tracks=track()+track('V_TEST',type=1)));put('two-audio',file(tracks=track()+track('A_FLAC')))
    put('empty-second-audio',file(tracks=track(audio=element(0xe1))))
    for name,rate in [('nan',float('nan')),('inf',float('inf')),('negative',-1),('zero',0),('huge',2147483647)]:put('rate-'+name,file(tracks=track(rate=rate)))
    for name,duration in [('nan',float('nan')),('inf',float('inf')),('negative',-1),('zero',0),('huge',2**62),('fractional',0.123456)]:put('duration-'+name,file(information=info(duration=duration)))
    for dt in ['webm','WebM','','unknown'] :put('doctype-'+(dt or 'empty'),file(dt=dt),'webm' if dt.lower()=='webm' else 'mka')
    put('clusterless',file(clusters=False));put('unknown-segment',file(unknown=True))
    streamed=element(0x1f43b675,uint(0xe7,0)+element(0xa3,b'\x81\0\0\x80'+bytes(200)),True)
    put('streamed-trailing-tags',file([streamed,tagblock(group([simple('TITLE','Trailing')]))],clusters=False,unknown=True))
    put('intercluster-tags',file([cluster,tagblock(group([simple('ARTIST','Between')])),cluster],clusters=False))
    put('clusters-many',file([cluster]*200,clusters=False))
    put('segment-duplicate',file()+element(0x18538067,tagblock(group([simple('TITLE','Ignored')]))))
    seed=file([tagblock(group([simple('TITLE','Owned')]))])
    for cut in [1,3,4,5,8,12,17,20,25,31,40,60,80,100,len(seed)-1]:put(f'cut-{cut}',seed[:cut])
    put('no-segment',element(0x1a45dfa3,text(0x4282,'matroska')))
    put('invalid-vint',element(0x1a45dfa3)+b'\0garbage')
    for codec,suffix in [('libopus','webm'),('libvorbis','webm'),('flac','mka'),('aac','mka'),('pcm_s16le','mka')]:
        run(['ffmpeg','-nostdin','-v','error','-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-t','0.2','-c:a',codec,'-metadata','title=Owned','-metadata','artist=Owned Artist','-y',owned/f'matroska-ffmpeg-{codec}.{suffix}'])
    put('float32',file(information=element(0x1549a966,number(0x4489,1000.125,4)),tracks=element(0xae,uint(0x83,2)+text(0x86,'A_FLAC')+element(0xe1,number(0xb5,44100,4)+uint(0x9f,2)+uint(0x6264,24)))))
    put('no-info',file(information=b''))
    put('info-duplicate',file([info('Last',500)],information=info('First',1000)))
    put('empty-info-last',file([element(0x1549a966)],information=info('First')))
    put('unknown-info',file(information=element(0x1549a966,number(0x4489,1000)+text(0x7ba9,'Streamed'),True)))
    put('invalid-floats',file(information=element(0x1549a966,element(0x4489,b'bad')),tracks=element(0xae,uint(0x83,2)+text(0x86,'A_FLAC')+element(0xe1,element(0xb5,b'bad')+uint(0x9f,2**64-1)+uint(0x6264,2**32)))))
    put('empty-audio',file(tracks=element(0xae,uint(0x83,2)+text(0x86,'A_OPUS')+element(0xe1))))
    put('attachment-invalid-names',file([attachments(attachment(b'cover\xff\xfe.png',b'image/\xff\xfe',description=b'D\xff\xfe'))]))
    put('long-invalid-key',file([tagblock(group([simple(b'x'*95+b'\xff\xfe'+'العربية'.encode(),'value')]))]))
    put('long-invalid-value',file([tagblock(group([simple('ENCODER',b'Lavf'+b'x'*100+b'\xff\xfe')]))]))
    put('unicode-fold',file([tagblock(group([simple('ARTIST','ΟΣ'),simple('CUSTOM','İ')],30),group([simple('ARTIST','Οσ'),simple('CUSTOM','i')],50))]))
    put('malformed-pairs',file([tagblock(group([simple(n,v) for n in ['PART_NUMBER','DISC'] for v in ['0/12','/4','4/','4/x','4/5/6','-2/09',' 04 / 09 ','']]))]))
    put('duplicate-fields',file([tagblock(group([element(0x67c8,text(0x45a3,'ARTIST')+text(0x45a3,'TITLE')+text(0x4487,'First')+text(0x4487,'Last'))]))]))
    return owned

def generate_dsd(source):
    owned=CACHE/'owned';owned.mkdir(exist_ok=True)
    def ck(name,data):return name.encode()+struct.pack('>Q',len(data))+data+(b'\0' if len(data)%2 else b'')
    def dsf(metadata=None,rate=2822400,channels=2,samples=32768,**fields):
        payload=bytes([0x69])*4096*channels
        fmt=b'fmt '+struct.pack('<QIIIIIIQII',52,fields.get('version',1),fields.get('format',0),fields.get('type',2),channels,rate,fields.get('bits',1),samples,fields.get('block',4096),fields.get('reserved',0))
        end=92+len(payload)
        return b'DSD '+struct.pack('<QQQ',fields.get('header_size',28),fields.get('file_size',end+len(metadata or b'')),fields.get('pointer',end if metadata is not None else 0))+fmt+b'data'+struct.pack('<Q',fields.get('data_size',12+len(payload)))+payload+(metadata or b'')
    def dff(metadata=None,diin=b'',rate=2822400,channels=2,cmpr='DSD ',compression_name=b'\0\0',before=False,extras=b'',audio=bytes([0x69])*8192):
        ids=b'C   ' if channels==1 else b'SLFTSRGT' if channels==2 else b'MLFTMRGTC   LFE LS  RS  '
        prop=b'SND '+ck('FS  ',struct.pack('>I',rate))+ck('CHNL',struct.pack('>H',channels)+ids)+ck('CMPR',cmpr.encode()+compression_name)
        body=b'DSD '+ck('FVER',bytes.fromhex('01050000'))+(ck('DIIN',diin) if diin and before else b'')+ck('PROP',prop)+extras+ck('DSD ',audio)+(ck('DIIN',diin) if diin and not before else b'')+(ck('ID3 ',metadata) if metadata is not None else b'')
        return b'FRM8'+struct.pack('>Q',len(body))+body
    def text(name,value):
        data=value if isinstance(value,bytes) else value.encode()
        return ck(name,struct.pack('>I',len(data))+data)
    def put(name,data,suffix):(owned/f'dsd-{name}.{suffix}').write_bytes(data)
    png=(owned/'cover.png').read_bytes()
    basic=tag([frame('TIT2',b'\x03Owned'),frame('TPE1',b'\x03One\0Two'),frame('TALB',b'\x03Album'),frame('TRCK',b'\x0304/09'),frame('TXXX',b'\x03MusicBrainz Album Id\0owned')])
    picture=tag([frame('TIT2',b'\x03Owned'),frame('APIC',b'\x03image/png\0\x03Cover\0'+png)])
    chapter=frame('CHAP',b'chapter\0'+struct.pack('>IIII',0,5,0xffffffff,0xffffffff)+frame('TIT2',b'\x03Chapter'))
    lyrics=frame('SYLT',b'\x03eng\x02\x01Owned\0Line\0'+struct.pack('>I',1))
    for suffix,make in [('dsf',dsf),('dff',dff)]:
        for name,metadata in [('base',None),('empty',tag([])),('tags',basic),('picture',picture),('chapters-lyrics',tag([chapter,lyrics])),('bad-id3',b'bad'),('bad-version',b'ID3\x05\0\0'+sync(0)),('truncated-tag',basic[:-5]),('large-picture',tag([frame('APIC',b'\x03image/png\0\x03Large\0'+png+bytes(1100000))]))]:put(name,make(metadata),suffix)
        for version in [2,3,4]:put(f'id3-v{version}',make(tag([frame('TT2' if version==2 else 'TIT2',b'\0Owned',version)],version)),suffix)
        for rate in [0,44100*64,44100*128,44100*256,48000*64,4294967295]:put(f'rate-{rate}',make(rate=rate),suffix)
        for channel in ([1,2,3,4,5,6] if suffix=='dsf' else [1,2,6]):put(f'channels-{channel}',make(channels=channel),suffix)
        seed=make(basic)
        for cut in [0,1,3,4,12,20,27,28,31,52,79,80,91,92,100,len(seed)-1]:put(f'cut-{cut}',seed[:cut],suffix)
    for field,values in [('version',[0,2]),('format',[1]),('type',[0,8]),('block',[0,512]),('reserved',[1]),('header_size',[0,27]),('bits',[0,8,255]),('samples',[0,3,32771]),('file_size',[0,92]),('data_size',[0,12,100,100000,18446744073709551615]),('pointer',[1,28,80,92,100,100000])]:
        for value in values:put(f'{field}-{value}',dsf(basic,**{field:value}),'dsf')
    for name,diin in [('tags',text('DIAR','Owned Artist')+text('DITI','Owned Title')),('empty',text('DIAR','')+text('DITI','')),('duplicates',text('DIAR','One')+text('DIAR','Two')+text('DITI','Title')),('unicode',text('DIAR','العربية')+text('DITI','Title\0')),('unknown',ck('EMID',bytes(10000))+text('DITI','Owned')),('malformed',ck('DITI',struct.pack('>I',100)+b'short'))]:
        put('diin-'+name,dff(diin=diin),'dff')
    put('diin-before',dff(diin=text('DIAR','Artist')+text('DITI','Title'),before=True),'dff')
    put('diin-conflict',dff(basic,diin=text('DIAR','Other Artist')+text('DITI','Other Title')),'dff')
    put('diin-echo',dff(basic,diin=text('DIAR','One')+text('DITI','Owned')),'dff')
    put('opaque',dff(extras=ck('JUNK',bytes(1100000))),'dff')
    put('duplicate-id3',dff(basic)+ck('ID3 ',picture),'dff')
    put('id3-before-audio',dff(extras=ck('ID3 ',picture)),'dff')
    put('dst-refusal',dff(cmpr='DST '),'dff')
    put('dst-name-refusal',dff(compression_name=b'DST Encoded'),'dff')
    put('zero-audio',dff(audio=b''),'dff')
    import io,mutagen
    from mutagen.id3 import ID3,TIT2,TPE1,APIC
    if mutagen.version_string!='1.47.0':raise ValueError('DSD vectors require mutagen 1.47.0')
    generated=ID3();generated.add(TIT2(encoding=3,text=['Owned Mutagen']));generated.add(TPE1(encoding=3,text=['One','Two']));generated.add(APIC(encoding=3,mime='image/png',type=3,desc='Owned',data=png))
    buffer=io.BytesIO();generated.save(buffer,v2_version=4,padding=lambda _:0)
    for suffix,make in [('dsf',dsf),('dff',dff)]:put('mutagen',make(buffer.getvalue()),suffix)
    return owned

def oracle(source,binary,owned,step=1):
    helper = CACHE/"helper"
    helper.mkdir(exist_ok=True)
    (helper/"main.go").write_text(HELPER)
    (helper/"go.mod").write_text(f"module sistrum.local/waxlabel-oracle\n\ngo 1.26\nrequire github.com/colespringer/waxlabel v0.0.0\nreplace github.com/colespringer/waxlabel => {source}\n")
    run([os.environ.get("GO","go"),"build","-trimpath","-o",CACHE/"supplement","."],cwd=helper)
    files = sorted(p for p in (source/"testdata").iterdir() if p.suffix.lower() in (".mp3",".wav",".aiff",".aifc") and p.name != "sample-rf64.wav")
    files += sorted(p for p in owned.iterdir() if p.suffix in (".mp3",".wav",".aiff"))
    if step == 2:
        files = sorted(p for folder in (source/"testdata",owned) for p in folder.iterdir() if p.suffix.lower() in (".flac",".ogg",".opus",".oga"))
    if step == 3:
        files = sorted(p for folder in (source/"testdata",owned) for p in folder.iterdir() if p.suffix.lower() in (".m4a",".mp4",".m4b",".mov",".m4r",".alac"))
    if step == 4:
        files = sorted(p for p in (source/"testdata").iterdir() if p.suffix.lower() in (".ape",".wv",".mpc",".mpp",".mp+"))
        files += sorted(p for p in owned.iterdir() if p.name.startswith("ape-") and p.suffix.lower() in (".ape",".wv",".mpc",".mpp",".mp+",".mp3"))
    if step == 5:
        files = sorted(p for p in (source/"testdata").iterdir() if p.suffix.lower() in (".wav",".aiff",".aifc",".aif"))
        files += sorted(p for p in owned.iterdir() if p.name.startswith("iff-") and p.suffix.lower() in (".wav",".aiff",".aifc",".aif"))
    if step == 6:
        files = sorted(p for p in (source/"testdata").iterdir() if p.suffix.lower() in (".wma",".asf"))
        files += sorted(p for p in owned.iterdir() if p.name.startswith("asf-") and p.suffix.lower() in (".wma",".asf"))
    if step == 7:
        files = sorted(p for p in (source/"testdata").iterdir() if p.suffix.lower() in (".mka",".mkv",".webm",".mk3d",".mks"))
        files += sorted(p for p in owned.iterdir() if p.name.startswith("matroska-") and p.suffix.lower() in (".mka",".mkv",".webm",".mk3d",".mks"))
    if step == 8: files=[]
    rows = []
    for path in files:
        ref = subprocess.run([str(binary),"dump","--native","--json",str(path)],capture_output=True,text=True)
        rel = path.relative_to(CACHE).as_posix()
        if ref.returncode:
            if ref.returncode not in (3,4):
                raise RuntimeError(f"Unexpected oracle failure {ref.returncode}: {ref.stderr}")
            rows.append({"path":rel,"sha256":sha(path),"errorClass":"UNSUPPORTED_FORMAT" if ref.returncode == 3 else "INVALID_DATA"})
            continue
        data = json.loads(ref.stdout)[0]
        data.pop("file")
        supplement = subprocess.check_output([str(CACHE/"supplement"),str(path)],text=True)
        rows.append({"path":rel,"sha256":sha(path),"oracle":data,"supplement":json.loads(supplement)})
    if step == 8:
        import importlib.util, sys
        spec=importlib.util.spec_from_file_location("waxlabel_dsd",REPO/"scripts/waxlabel-dsd-oracle.py")
        adapter=importlib.util.module_from_spec(spec);spec.loader.exec_module(adapter)
        rows=adapter.oracle_rows(sys.modules[__name__],binary)
    DOCS.mkdir(exist_ok=True)
    reference={"attribution":HEADER,"pin":PIN,"files":rows}
    if step == 8: reference["dsdCrateSha256"]=adapter.PINS
    (DOCS/f"oracle-step{step}.json").write_text(json.dumps(reference,ensure_ascii=False,indent=2)+"\n")
    # Hash every fetched upstream source/test/testdata file, and every owned input.
    inputs = sorted(p for p in source.rglob("*") if p.is_file() and ".git" not in p.parts)
    inputs += sorted(p for p in owned.rglob("*") if p.is_file())
    if (CACHE/"dsd-reference").exists(): inputs += sorted(p for p in (CACHE/"dsd-reference").rglob("*") if p.is_file())
    with MANIFEST.open("w") as out:
        out.write(f"# {HEADER}\n# WaxLabel commit {PIN}; generated by scripts/waxlabel-oracle.py\npath\tsha256\n")
        for path in inputs:
            out.write(f"{path.relative_to(CACHE).as_posix()}\t{sha(path)}\n")
    print(f"Generated {len(rows)} step-{step} oracle records; hashed {len(inputs)} fetched/generated files")


def verify():
    count = 0
    expected = set()
    for line in MANIFEST.read_text().splitlines():
        if line.startswith("#") or line == "path\tsha256":
            continue
        rel, digest = line.split("\t")
        path = (CACHE/rel).resolve()
        if CACHE not in path.parents or not path.is_file() or sha(path) != digest:
            raise SystemExit(f"Missing/changed oracle input: {rel}; run the pinned fetch/generate harness")
        expected.add(rel)
        count += 1
    for folder in (CACHE/"src",CACHE/"owned",CACHE/"dsd-reference"):
        actual = {p.relative_to(CACHE).as_posix() for p in folder.rglob("*") if p.is_file() and ".git" not in p.parts}
        if actual-expected:
            raise SystemExit(f"Unmanifested oracle inputs: {sorted(actual-expected)}")
    for fixture in DOCS.glob("oracle-step*.json"):
        for row in json.loads(fixture.read_text())["files"]:
            if row["path"] not in expected or sha(CACHE/row["path"]) != row["sha256"]:
                raise SystemExit("Fixture/corpus manifest disagreement")
    print(f"Verified {count} pinned upstream and owned files; no audio in repository")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch-generate",action="store_true")
    parser.add_argument("--verify",action="store_true")
    parser.add_argument("--step",type=int,choices=(1,2,3,4,5,6,7,8),default=1)
    args = parser.parse_args()
    safe_cache()
    if args.fetch_generate:
        source,binary = fetch()
        oracle(source,binary,{1:generate,2:generate_vorbis,3:generate_mp4,4:generate_ape,5:generate_iff,6:generate_asf,7:generate_matroska,8:generate_dsd}[args.step](source),args.step)
        verify()
    elif args.verify:
        verify()
    else:
        parser.error("choose --fetch-generate or --verify")
