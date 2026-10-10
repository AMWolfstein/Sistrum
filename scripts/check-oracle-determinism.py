#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every test input in two independently rebuilt oracle directories."""
import hashlib,json,pathlib,sys

def inventory(root):
    result={}
    for group in ('corpus','alac-packets','owned'):
        for p in sorted((root/group).rglob('*')):
            if p.is_file():
                with p.open('rb') as f:result[str(p.relative_to(root))]=hashlib.file_digest(f,'sha256').hexdigest()
    return result
if __name__=='__main__':
    a,b=map(pathlib.Path,sys.argv[1:3]);left,right=inventory(a),inventory(b)
    if not left or left!=right:
        bad=sorted(k for k in left.keys()|right.keys() if left.get(k)!=right.get(k))
        raise SystemExit('Non-deterministic oracle inputs: '+', '.join(bad))
    proof=hashlib.sha256(json.dumps(left,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    print(f'Determinism passed: {len(left)} files, every SHA-256 identical; inventory SHA-256 {proof}')
