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
    DOCS.mkdir(exist_ok=True)
    (DOCS/f"oracle-step{step}.json").write_text(json.dumps({"attribution":HEADER,"pin":PIN,"files":rows},ensure_ascii=False,indent=2)+"\n")
    # Hash every fetched upstream source/test/testdata file, and every owned input.
    inputs = sorted(p for p in source.rglob("*") if p.is_file() and ".git" not in p.parts)
    inputs += sorted(p for p in owned.rglob("*") if p.is_file())
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
    for folder in (CACHE/"src",CACHE/"owned"):
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
    parser.add_argument("--step",type=int,choices=(1,2),default=1)
    args = parser.parse_args()
    safe_cache()
    if args.fetch_generate:
        source,binary = fetch()
        oracle(source,binary,generate(source) if args.step==1 else generate_vorbis(source),args.step)
        verify()
    elif args.verify:
        verify()
    else:
        parser.error("choose --fetch-generate or --verify")
