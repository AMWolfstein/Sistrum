#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Reject audio in the git index, including staged additions and uppercase names."""
import pathlib,subprocess,sys
EXTENSIONS='wv wvc dsf dff ape mpc wma asf aif aiff aifc wav flac m4a caf mp3 ogg opus m4b'.split()
def tracked():
    paths=subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
    return [p for p in paths if pathlib.PurePath(p).suffix.lower().lstrip('.') in EXTENSIONS]
if __name__=='__main__':
    files=tracked()
    if files:
        print('Git must not track audio. Generate/fetch it outside git with: bash scripts/waxflow-oracle.sh --fetch-generate\n'+'\n'.join(files),file=sys.stderr);sys.exit(1)
    print('No tracked audio files')
