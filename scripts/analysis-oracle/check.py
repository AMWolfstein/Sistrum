#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify JSON field coverage, manifest consistency and repeatability of the oracle."""
import json, subprocess
from build import ROOT,CACHE
from corpus import verify
verify()
rows=json.loads((ROOT/'docs/analysis/corpus-manifest.json').read_text())
fixtures=json.loads((ROOT/'docs/analysis/oracle.json').read_text())
fields={'file','loudness_lufs','loudness_range','true_peak','rms','crest','spectral_centroid','spectral_rolloff','spectral_flatness','spectral_flux','zcr','tempo','onset_variance'}
assert len({r['file'] for r in fixtures})==len(fixtures)
assert {r['file'] for r in fixtures}=={r['file'] for r in rows if 'file' in r and 'analysis_error' not in r}
for row in fixtures:
 assert set(row)==fields,row
 if row['file'].startswith('owned/generated/'):
  p=subprocess.run([str(CACHE/'analyze'),str(CACHE/row['file'])],capture_output=True,text=True,check=True)
  actual=json.loads(p.stdout);actual['file']=row['file'];assert actual==row,row['file']
print(f'{len(fixtures)} complete feature rows; generated oracle repeatability passed')
