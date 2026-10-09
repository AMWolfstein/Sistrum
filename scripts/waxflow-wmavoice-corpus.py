#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Derived from MIT WaxFlow codec/wmavoice/testdata/corpus, fork
# github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe.
# Copyright (c) 2026 Cole Springer, MIT License (see codecs/THIRD-PARTY-NOTICES).
"""Reproduce header-only Voice refusals; expected results come only from Go.
Usage: waxflow-wmavoice-corpus.py EXTERNAL_PINNED_CLONE EXTERNAL_CORPUS
"""
import pathlib,struct,sys
src,corpus=map(pathlib.Path,sys.argv[1:]);corpus=corpus.resolve()
if corpus.is_relative_to(pathlib.Path(__file__).resolve().parent.parent):
    raise SystemExit('Corpus must remain outside the repository')
original=(src/'codec/wmavoice/testdata/corpus/voice-16000-12k.wma').read_bytes()
wfx=original.index(bytes.fromhex('9107dcb7b7a9cf118ee600c00c205365'))+24+54
flags=struct.unpack_from('<I',original,wfx+36)[0]
for name,offset,fmt,value in [('channels-2.wma',2,'<H',2),('rate-24000.wma',4,'<I',24000),('delta-pitch-7000.wma',4,'<I',7000),('denoise-12.wma',36,'<I',(flags&~60)|(12<<2))]:
    data=bytearray(original);struct.pack_into(fmt,data,wfx+offset,value)
    dst=corpus/'waxflow-wmavoice-tests/refused'/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
