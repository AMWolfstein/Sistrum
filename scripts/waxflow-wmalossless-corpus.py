#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Derived from MIT WaxFlow codec/wmalossless/testdata/corpus, fork
# github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe.
# Copyright (c) 2026 Cole Springer, MIT License (see codecs/THIRD-PARTY-NOTICES).
"""Header-only named-refusal vectors. PCM/refusal expectations must come from Go.
Usage: waxflow-wmalossless-corpus.py EXTERNAL_PINNED_CLONE EXTERNAL_CORPUS
"""
import pathlib,struct,sys
src,corpus=map(pathlib.Path,sys.argv[1:]);corpus=corpus.resolve()
if corpus.is_relative_to(pathlib.Path(__file__).resolve().parent.parent):
    raise SystemExit('Corpus must remain outside the repository')
original=(src/'codec/wmalossless/testdata/corpus/ll-44100-2ch-16.wma').read_bytes()
sp=original.index(bytes.fromhex('9107dcb7b7a9cf118ee600c00c205365'))+24
for name,offset,value in [('depth-32.wma',72,32),('channels-9.wma',56,9),('subframe-depth-6.wma',86,48)]:
    data=bytearray(original);struct.pack_into('<H',data,sp+offset,value)
    dst=corpus/'waxflow-wmalossless-tests/refused'/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
