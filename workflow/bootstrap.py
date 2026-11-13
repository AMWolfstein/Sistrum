"""Create missing project support/history from portable defaults; never import task data."""
from __future__ import annotations
import json
import os
from pathlib import Path
import subprocess
import registry

ENTRY = '\n\n<!-- workflow-bootstrap -->\n## Shared workflow instructions\n\nBefore modifying this repository, read and follow [workflow/README.md](workflow/README.md)\nand its linked policy/skills. This applies to every agent, including small changes.\n'


def create(root,relative,data,mode=0o644):
    """Exclusive creation preserves files that appear between checking and opening."""
    target=registry.safe(root,relative);target.parent.mkdir(parents=True,exist_ok=True)
    try:
        with target.open('xb') as out:out.write(data);out.flush();os.fsync(out.fileno())
    except FileExistsError:
        if not target.is_file():raise ValueError(f'Expected regular file, found another object: {relative}')
        return False
    target.chmod(mode);return True


def allowed(relative):
    parts=Path(relative).parts
    if relative=='.github/workflows/workflow-integrity.yml':return True
    if parts[:2] in [('.agents','skills'),('.agents','reviewers')]:return Path(relative).suffix=='.md'
    if parts[:1]!=('.specify',):return False
    if len(parts)==2:return parts[1] in {'.gitignore','project-policy.md','init-options.json','integration.json','upstream.json','LICENSE.spec-kit'}
    return parts[1] in {'scripts','templates','memory','integrations','workflows'}


def project_data(root):
    history=registry.safe(root,registry.HISTORY);statefile=registry.safe(root,registry.REGISTRY)
    existed=history.exists();created=False
    if existed and not history.is_file():raise ValueError('History path is not a regular file')
    try:
        if statefile.exists():
            state=registry.load(root);registry.record_rows(root,state)
            if not existed:created=create(root,registry.HISTORY,registry.history(state).encode('utf-8'))
            if history.read_text(encoding='utf-8')!=registry.history(state):
                return {'status':'INCOMPLETE','history':'preserved' if not created else 'created','reason':'Existing history differs from local registry; not overwritten. Reconcile explicitly.'}
            return {'status':'PASS','history':'created' if created else 'preserved','registry':'preserved'}
        records=any((root/'specs').glob('*/tasks.md')) or any((root/'.specify/quick-tasks').glob('TOR*.md'))
        blank=registry.history(registry.empty())
        if not existed:created=create(root,registry.HISTORY,blank.encode('utf-8'))
        if records or history.read_text(encoding='utf-8')!=blank:
            return {'status':'INCOMPLETE','history':'created' if created else 'preserved','reason':'Existing project records/history need explicit init --migrate/reconciliation; setup does not reset or import them'}
        initialized=create(root,registry.REGISTRY,(json.dumps(registry.empty(),indent=2)+'\n').encode('utf-8'))
        return {'status':'PASS','history':'created' if created else 'preserved','registry':'created' if initialized else 'preserved'}
    except (ValueError,OSError,KeyError,TypeError) as error:
        return {'status':'INCOMPLETE','history':'preserved' if existed else 'not-created','reason':str(error)}


def prepare(root,assets=None):
    """Install defaults only when missing; preserve custom project configuration."""
    top=subprocess.check_output(['git','rev-parse','--show-toplevel'],cwd=root,text=True).strip()
    if Path(top).resolve()!=root.resolve():raise ValueError('Setup target must be the Git repository root')
    if subprocess.check_output(['git','ls-files','-z','--','.work'],cwd=root):raise ValueError('Refusing setup with tracked .work files')
    assets=assets if assets is not None else Path(__file__).with_name('assets')
    if not assets.is_dir():raise ValueError('Portable workflow assets are missing; copy the complete workflow/ directory')
    installed=[];preserved=[]
    with registry.lock(root):
        ignored=subprocess.run(['git','check-ignore','-q','.work/bootstrap-probe'],cwd=root)
        if ignored.returncode not in (0,1):raise ValueError('Cannot inspect Git ignore rules')
        if ignored.returncode:
            ignore=registry.safe(root,'.gitignore')
            prefix=ignore.read_bytes() if ignore.exists() else b''
            with ignore.open('ab') as out:out.write((b'\n' if prefix and not prefix.endswith(b'\n') else b'')+b'\n# Local workflow caches\n.work/\n')
        for source in sorted(assets.rglob('*')):
            if source.is_symlink():raise ValueError(f'Symlink in portable assets: {source}')
            if not source.is_file():continue
            relative=source.relative_to(assets).as_posix()
            if not allowed(relative):continue
            if create(root,relative,source.read_bytes(),source.stat().st_mode & 0o777):installed.append(relative)
            else:preserved.append(relative)
        agents=registry.safe(root,'AGENTS.md')
        if not agents.exists():create(root,'AGENTS.md',('# Project instructions\n'+ENTRY).encode('utf-8'))
        else:
            text=agents.read_text(encoding='utf-8')
            if 'workflow/README.md' not in text and '<!-- workflow-bootstrap -->' not in text:
                with agents.open('ab') as out:out.write(ENTRY.encode('utf-8'))
        project=project_data(root)
    return {'status':project['status'],'installed_support_files':installed,'preserved_support_files':preserved,'project':project,
            'note':'Existing files are never replaced; review preserved custom instructions for compatibility. Project task data is not copied from assets.'}
