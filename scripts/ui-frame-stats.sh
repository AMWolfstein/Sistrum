#!/usr/bin/env bash
# .qa only. Requires an unlocked device and a loaded, paused track in the .qa mini-player.
# Usage: bash scripts/ui-frame-stats.sh --track-title 'Exact loaded title' [--output-dir DIR]
# Record the same device, library, theme and Reduce Transparency setting for later comparisons.
set -euo pipefail
exec python3 - "$@" <<'PY'
import argparse
import datetime
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.amwolfstein.sistrum.dev.qa'
p = argparse.ArgumentParser(description='SC-011: Home scroll, Library scroll and player-open frame shares (.qa only)')
p.add_argument('--package', default=PACKAGE)
p.add_argument('--home-axis', choices=('horizontal', 'vertical'), default='horizontal', help='Shipped Home uses horizontal track shelves; choose vertical if a later layout changes')
p.add_argument('--track-title', required=True, help='Exact title already loaded in the .qa mini-player (no playback is started)')
p.add_argument('--output-dir', type=Path, default=Path('/tmp') / ('sistrum-ui-frames-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S')))
args = p.parse_args()
if args.package != PACKAGE:
    p.error('REFUSED: only ' + PACKAGE + ' is allowed')
if not args.track_title.strip():
    p.error('--track-title must not be empty')
adb = shutil.which('adb')
if not adb:
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        properties = Path('local.properties')
        if properties.exists():
            sdk = next((line.split('=', 1)[1] for line in properties.read_text().splitlines() if line.startswith('sdk.dir=')), None)
    adb = str(Path(sdk) / 'platform-tools/adb') if sdk else None
if not adb or not Path(adb).is_file():
    sys.exit('BLOCKED: adb not found')

def shell(*command):
    return subprocess.check_output([adb, 'shell', *command], text=True, stderr=subprocess.STDOUT, timeout=30)

def foreground():
    windows = shell('dumpsys', 'window')
    focus = re.search(r'mCurrentFocus=Window\{[^\n]*\s' + re.escape(PACKAGE) + r'/[^\n]*\}', windows)
    if not focus:
        raise RuntimeError('REFUSED: unlocked foreground window must belong to ' + PACKAGE)

def hierarchy():
    foreground()
    path = '/sdcard/sistrum-qa-frame-window.xml'
    dump = shell('uiautomator', 'dump', path)
    if 'dumped to' not in dump:
        raise RuntimeError('UI hierarchy dump failed: ' + dump)
    root = ET.fromstring(shell('cat', path))
    shell('rm', path)
    return root

def bounds(node):
    values = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not values:
        raise RuntimeError('Invalid UI bounds: ' + str(node.attrib))
    return tuple(map(int, values.groups()))

def tap(node):
    if node.get('package') != PACKAGE:
        raise RuntimeError('REFUSED: target node belongs to another package')
    x1, y1, x2, y2 = bounds(node)
    if x2 <= x1 or y2 <= y1:
        raise RuntimeError('Target has empty bounds')
    foreground()
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def destination(labels):
    nodes = [n for n in hierarchy().iter('node') if n.get('package') == PACKAGE and
             (n.get('content-desc') in labels or n.get('text') in labels)]
    if len(nodes) != 1:
        raise RuntimeError('Expected one expanded navigation target ' + str(labels) + '; found ' + str(len(nodes)))
    tap(nodes[0])
    time.sleep(1)

def scroll(horizontal=False):
    root = hierarchy()
    nodes = [n for n in root.iter('node') if n.get('package') == PACKAGE and n.get('scrollable') == 'true']
    if not nodes:
        raise RuntimeError('No scrollable content: populate the .qa library before recording SC-011')
    node = max(nodes, key=lambda n: (bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
    x1,y1,x2,y2 = bounds(node)
    if horizontal:
        start, end = (x1+3*(x2-x1)//4, (y1+y2)//2), (x1+(x2-x1)//4, (y1+y2)//2)
    else:
        start, end = ((x1+x2)//2, y1+3*(y2-y1)//4), ((x1+x2)//2, y1+(y2-y1)//4)
    for first, last in [(start,end)]*3 + [(end,start)]*3:
        foreground()
        shell('input', 'swipe', *map(str, (*first, *last)), '400')
        time.sleep(0.3)

def measure(name, action):
    foreground()
    shell('dumpsys', 'gfxinfo', PACKAGE, 'reset')
    action()
    time.sleep(1)
    stats = shell('dumpsys', 'gfxinfo', PACKAGE, 'framestats')
    (args.output_dir / (name + '-framestats.txt')).write_text(stats)
    total = re.search(r'^\s*Total frames rendered:\s*(\d+)', stats, re.M)
    janky = re.search(r'^\s*Janky frames:\s*(\d+)', stats, re.M)
    if not total or not janky or int(total[1]) == 0:
        raise RuntimeError(name + ': missing/nonzero frame counters required; see raw framestats')
    total, janky = int(total[1]), int(janky[1])
    result = f'{name}\t{total}\t{janky}\t{100*janky/total:.3f}%'
    print(result, flush=True)
    with (args.output_dir / 'summary.tsv').open('a') as output:
        output.write(result + '\n')

def open_player():
    nodes = [n for n in hierarchy().iter('node') if n.get('package') == PACKAGE and
             args.track_title in (n.get('text', ''), *n.get('content-desc', '').split('\n'))]
    if len(nodes) != 1:
        raise RuntimeError('Expected unique loaded mini-player title; found ' + str(len(nodes)))
    tap(nodes[0])
    time.sleep(1)
    # Full player exposes a seek control; a tap that did nothing must never count as player-open frames.
    if not any(n.get('package') == PACKAGE and n.get('class') == 'android.widget.SeekBar'
               for n in hierarchy().iter('node')):
        raise RuntimeError('Player open not confirmed (no seek control)')

try:
    args.output_dir.mkdir(parents=True, exist_ok=False)
    (args.output_dir / 'summary.tsv').write_text('scenario\ttotal_frames\tjanky_frames\tjanky_share\n')
    foreground()
    (args.output_dir / 'device.txt').write_text(shell('getprop', 'ro.build.fingerprint') + shell('wm', 'size') + shell('wm', 'density'))
    destination(('Home', 'الرئيسية'))
    measure('home-scroll', lambda: scroll(horizontal=args.home_axis == 'horizontal'))
    # Reverse scrolling expands the shipped navigation chrome before choosing Library.
    destination(('Library', 'المكتبة'))
    measure('library-scroll', scroll)
    measure('player-open', open_player)
    print('logs: ' + str(args.output_dir))
except (RuntimeError, subprocess.SubprocessError, ET.ParseError, OSError) as error:
    sys.exit('BLOCKED: ' + str(error) + '\nlogs: ' + str(args.output_dir))
PY
