#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Desktop-only pinned source build. All downloads, libraries and binaries are external."""
import hashlib, os, pathlib, shutil, subprocess, tarfile, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE = pathlib.Path(os.environ.get('SISTRUM_ANALYSIS_ORACLE_DIR', str(pathlib.Path.home()/'.cache/sistrum-analysis-oracle'))).resolve()
PINS = {
 'airmedy': ('https://api.github.com/repos/misa198/airmedy/tarball/7d3a08d0eaa2c88d3518f19665eaffc4762dd6a1', '5b89ef1c11d60163f8f372fdb53a0753cf4986b6881d46655083867621c5f467'),
 'ffmpeg-8.1.2': ('https://ffmpeg.org/releases/ffmpeg-8.1.2.tar.gz','32faba5ef67340d54724941eae1425580791195312a4fd13bf6f820a2818bf22'),
 'aubio-0.4.9': ('https://files.pythonhosted.org/packages/cd/80/302d89240603e5347c7f8026c8b02c59f8dfaec66c91a743d82de7c86006/aubio-0.4.9.tar.gz','df1244f6c4cf5bea382c8c2d35aa43bc31f4cf631fe325ae3992c219546a4202'),
 'fftw-3.3.10': ('https://www.fftw.org/fftw-3.3.10.tar.gz','56c932549852cddcfafdab3820b0200c7742675be92179e59e6215b340e26467'),
}
def run(args, cwd=None, env=None): subprocess.run([str(a) for a in args], cwd=cwd, env=env, check=True)
def source(name):
 url, sha = PINS[name]; archive = CACHE/(name+'.tar.gz'); dest=CACHE/('src-'+name)
 if not archive.exists(): urllib.request.urlretrieve(url, archive)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=sha: raise RuntimeError('Source hash mismatch: '+name)
 if not dest.exists():
  dest.mkdir()
  with tarfile.open(archive) as tar:
   prefix=tar.getmembers()[0].name.split('/')[0]+'/'
   for entry in tar:
    if not entry.name.startswith(prefix): continue
    entry.name=entry.name[len(prefix):]
    if entry.name: tar.extract(entry,dest,filter='data')
 return dest
if __name__=='__main__':
 CACHE.mkdir(parents=True,exist_ok=True)
 sources={n:source(n) for n in PINS}; install=CACHE/'install'; jobs=str(min(os.cpu_count() or 1, 12))
 fftw=sources['fftw-3.3.10']
 if not (install/'lib/libfftw3f.a').exists():
  run(['./configure','--prefix='+str(install),'--disable-shared','--enable-static','--with-pic','--enable-single','--disable-fortran','--disable-doc'],fftw)
  run(['make','-j'+jobs],fftw); run(['make','install'],fftw)
 aubio=sources['aubio-0.4.9']
 if not (install/'lib/libaubio.a').exists():
  shim=CACHE/'pyshim'; shim.mkdir(exist_ok=True)
  (shim/'imp.py').write_text('import types, importlib.util as u\ndef new_module(name): return types.ModuleType(name)\ndef load_source(name,path):\n s=u.spec_from_file_location(name,path); m=u.module_from_spec(s); s.loader.exec_module(m); return m\n')
  for p in (aubio/'waflib').rglob('*.py'): p.write_text(p.read_text().replace("'rU'", "'r'"))
  env=dict(os.environ,PYTHONPATH=str(shim),CFLAGS='-O2 -fPIC -I'+str(install/'include'),LDFLAGS='-L'+str(install/'lib'),PKG_CONFIG_PATH=str(install/'lib/pkgconfig'))
  flags=['--enable-fftw3f','--disable-intelipp','--disable-accelerate','--disable-sndfile','--disable-samplerate','--disable-jack','--disable-avcodec','--disable-blas','--disable-docs','--disable-tests','--disable-examples','--notests','--prefix='+str(install)]
  run(['python3','waf','configure','build']+flags,aubio,env)
  shutil.copy2(aubio/'build/src/libaubio.a',install/'lib/libaubio.a')
  shutil.copytree(aubio/'src',install/'include/aubio',dirs_exist_ok=True)
 ffmpeg=sources['ffmpeg-8.1.2']
 if not (install/'bin/ffmpeg').exists():
  run(['./configure','--prefix='+str(install),'--disable-everything','--disable-doc','--disable-debug','--disable-network','--disable-autodetect','--enable-static','--disable-shared','--enable-avcodec','--enable-avformat','--enable-avutil','--enable-swresample','--enable-avfilter','--enable-ffmpeg','--disable-ffplay','--disable-ffprobe','--enable-decoders','--enable-demuxers','--enable-parsers','--enable-encoder=pcm_f32le','--enable-muxer=wav','--enable-protocol=file','--enable-filter=ebur128,aspectralstats,astats,aresample,aformat,anull,abuffer,abuffersink'],ffmpeg)
  run(['make','-j'+jobs],ffmpeg); run(['make','install'],ffmpeg)
 harness=CACHE/'harness'; harness.mkdir(exist_ok=True)
 for name in ['ffmpeg_analyzer.h','ffmpeg_shared_mu.h','ffmpeg_shared_mu.c']:
  shutil.copy2(sources['airmedy']/'internal/infra/audio'/name,harness/name)
 shutil.copy2(ROOT/'scripts/analysis-oracle/main.go',harness/'main.go')
 (harness/'go.mod').write_text('module sistrum-analysis-oracle\n\ngo 1.23\n')
 env=dict(os.environ,CGO_ENABLED='1',CGO_CFLAGS='-I'+str(install/'include'),CGO_LDFLAGS='-L'+str(install/'lib')+' -lavfilter -lavformat -lavcodec -lswresample -lswscale -lavutil -laubio -lfftw3f -lm -lpthread')
 run(['go','build','-o',CACHE/'analyze','.'],harness,env)
 print('Oracle: '+str(CACHE/'analyze'))
