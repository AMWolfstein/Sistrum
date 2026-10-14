#!/usr/bin/env python3
"""Stage the pinned T012 audio beside the existing flat corpus; never access a device."""
import argparse
import csv
import hashlib
import os
from pathlib import Path
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--out', type=Path, required=True, help='existing corpus staging directory, outside the repository')
parser.add_argument('--oracle', type=Path, default=Path(os.environ.get('SISTRUM_ORACLE_DIR', Path.home() / '.cache/sistrum-waxflow-oracle')))
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
out = args.out.resolve()
if out == repo or repo in out.parents:
    parser.error('--out must be outside the repository')
manifest = repo / 'androidApp/src/androidTest/assets/kotlin-formats.tsv'
rows = list(csv.DictReader(manifest.open(), delimiter='\t'))
# Verify everything before writing anything. Missing or changed inputs fail; no fixture is skipped.
for row in rows:
    source = args.oracle / row['root'] / row['source']
    if hashlib.sha256(source.read_bytes()).hexdigest() != row['file_sha256']:
        raise SystemExit(f'corpus hash mismatch: {source}')
out.mkdir(parents=True, exist_ok=True)
for row in rows:
    source = args.oracle / row['root'] / row['source']
    suffix = source.suffix
    # m4b contains ALAC; .m4a is the scanner/registry container label for this test.
    if suffix == '.m4b':
        suffix = '.m4a'
    shutil.copyfile(source, out / f"t012-{row['id']}{suffix}")
print(f'Staged {len(rows)} pinned files in {out}; existing corpus files retained. No device accessed.')
