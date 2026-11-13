"""Local workflow dependency preparation and read-only capability reporting."""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import shutil
import shlex
import subprocess
import sys
import tempfile
import registry


def interpreter(root,name):
    folder=registry.safe(root,'.work/'+name)
    return folder/('Scripts/python.exe' if os.name=='nt' else 'bin/python')


def run(argv,root,timeout=20):
    done=subprocess.run(argv,cwd=root,capture_output=True,text=True,errors='replace',stdin=subprocess.DEVNULL,timeout=timeout)
    if done.returncode:raise ValueError(f'{Path(str(argv[0])).name} exited {done.returncode}: {(done.stderr+done.stdout)[-700:]}')
    return done.stdout.strip() or done.stderr.strip()


def prerequisites(root):
    if sys.version_info<(3,10):raise ValueError('Python 3.10+ required (3.11+ for optional Serena)')
    top=Path(run(['git','rev-parse','--show-toplevel'],root)).resolve()
    if top!=root.resolve():raise ValueError('Run with the repository root, not a nested directory')
    done=subprocess.run(['git','check-ignore','-q','.work/workflow-probe'],cwd=root)
    if done.returncode:raise ValueError('.work/ must be Git-ignored before provisioning')
    if subprocess.check_output(['git','ls-files','-z','--','.work'],cwd=root):raise ValueError('Tracked .work files are not allowed')
    for path in ['.work','.work/code-tools-python','.work/serena-python','workflow/codegraph','workflow/codegraph/node_modules']:
        registry.safe(root,path)
    return 'Git root, Python and ignored/confined managed paths verified'


def toolchains():
    """Pin readers shared with the codegraph tools, without leaving that folder on sys.path."""
    sys.path.insert(0,str(Path(__file__).with_name('codegraph')))
    try:
        import toolchains as pins
        return pins
    finally:sys.path.pop(0)


def verify_venv(root,python):
    expected=python.parent.parent.resolve()
    actual=Path(json.loads(run([str(python),'-c','import json,sys; print(json.dumps(sys.prefix))'],root))).resolve()
    if actual!=expected:raise ValueError(f'Refusing non-managed Python prefix at {python}; repair the local venv before setup')


def doctor(root,full=False,serena=False):
    checks=[];optional=[]
    def check(name,probe,required=True):
        try:entry={'name':name,'status':'PASS','detail':probe()}
        except (ValueError,OSError,KeyError,TypeError,ImportError,subprocess.SubprocessError) as error:entry={'name':name,'status':'UNAVAILABLE','detail':str(error)}
        (checks if required else optional).append(entry);return entry
    pre=check('prerequisites',lambda:prerequisites(root))
    if pre['status']!='PASS':return {'status':'INCOMPLETE','checks':checks,'optional':optional}
    check('node',lambda:run(['node','--version'],root))
    check('npm',lambda:run(['npm','--version'],root))
    def graph():
        info=json.loads(run([sys.executable,str(root/'workflow/codegraph/codegraph_adapter.py'),'version'],root))
        if not info.get('available') or info.get('version')!='1.6.2':raise ValueError('Pinned CodeGraph 1.6.2 unavailable; run setup')
        return info
    check('codegraph',graph)
    python=interpreter(root,'code-tools-python')
    def parsers():
        selected=shlex.split(os.environ['CODEGRAPH_SHELL_PYTHON']) if os.environ.get('CODEGRAPH_SHELL_PYTHON') else [str(python)]
        if not selected:raise ValueError('Empty parser interpreter override')
        if not os.environ.get('CODEGRAPH_SHELL_PYTHON') and not python.is_file():raise ValueError('Managed parser environment missing; run setup')
        if not os.environ.get('CODEGRAPH_SHELL_PYTHON'):verify_venv(root,python)
        info=json.loads(run([*selected,str(root/'workflow/codegraph/shell_adapter.py'),'version'],root))
        if not info.get('available'):raise ValueError('Pinned Bash parser versions missing; run setup')
        # Real parse capability, not just a package-version declaration.
        with tempfile.TemporaryDirectory(prefix='workflow-doctor-') as tmp:
            source=Path(tmp)/'probe.sh';source.write_text('probe() { :; }\nprobe\n')
            request=Path(tmp)/'request.json';request.write_text(json.dumps([{'file':'probe.sh','path':str(source)}]))
            data=json.loads(run([*selected,str(root/'workflow/codegraph/shell_adapter.py'),'extract','--request',str(request)],root))
            if not data or data[0]['errors'] or not data[0]['functions']:raise ValueError('Real Bash parse failed')
        run([*selected,'-c',"import sys; exec('import tomllib' if sys.version_info >= (3,11) else 'import tomli as tomllib'); assert tomllib.loads('[probe]\\nok=true')['probe']['ok']"],root)
        return {'python':selected,'packages':info['packages'],'real_parse':'PASS'}
    check('parsers',parsers)
    def semantic():
        executable=os.environ.get('SERENA_PYTHON') or str(interpreter(root,'serena-python'))
        info=json.loads(run([executable,str(root/'workflow/codegraph/serena_adapter.py'),'version','--project',str(root/'.work/codegraph/index/source')],root))
        if not info.get('available'):raise ValueError('Pinned optional Serena dependencies unavailable')
        return info
    check('serena',semantic,required=serena)
    check('dart',lambda:run(['dart','--version'],root),required=full)
    def rust():
        channel=toolchains().rust_channel(root)
        if not channel:raise ValueError('rust_backend/rust-toolchain.toml pins no Rust channel')
        installed=run(['rustup','toolchain','list'],root)
        if not any(line.split() and line.split()[0].startswith(channel) for line in installed.splitlines()):raise ValueError(f'Rust {channel} not installed; doctor never installs toolchains')
        return run(['cargo',f'+{channel}','clippy','--version'],root)
    check('rust-clippy',rust,required=full)
    def java():
        version=toolchains().jdk_version(root)
        if not version:raise ValueError('android/app/build.gradle.kts pins no JavaVersion')
        output=run(['java','-version'],root)
        if not re.search(rf'version "{version}(?:[."]|$)|openjdk {version}\b',output):raise ValueError(f'Activate the repository-required JDK {version}')
        return f'JDK {version} available'
    check('java',java,required=full)
    def android():
        sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if not sdk or not Path(sdk).is_dir():raise ValueError('Android SDK not configured')
        wrapper=(root/'android/gradlew').is_file()
        if not wrapper and not shutil.which('gradle'):raise ValueError('No android/gradlew and no gradle executable; setup does not build APKs to create one')
        return {'sdk':sdk,'gradle':'wrapper' if wrapper else 'path','note':'Build compatibility/variant coverage not verified by doctor'}
    check('android',android,required=full)
    def cpp():
        sys.path.insert(0,str(root/'workflow/codegraph'))
        try:
            import native_audit
            executable=native_audit.clang_tidy()
            if not executable:raise ValueError('Activate clang-tidy/NDK or AUDIT_CLANG_TIDY')
            version=run([executable,'--version'],root)
            config=json.loads((root/'workflow/codegraph/audit-config.json').read_text(encoding='utf-8'))['tools']['cpp']
            database=native_audit.compilation_database(root,config)
            if database is None or not database.is_file():raise ValueError('C++ compilation database absent; configure actual CMake target')
            return {'version':version,'database':str(database),'note':'Compiler/header/ABI coverage is checked by audit, not doctor'}
        finally:sys.path.pop(0)
    check('clang-tidy/database',cpp,required=full)
    check('ruff',lambda:run(['ruff','--version'],root),required=full)
    check('shellcheck',lambda:run(['shellcheck','--version'],root),required=full)
    return {'status':'PASS' if all(item['status']=='PASS' for item in checks) else 'INCOMPLETE','checks':checks,'optional':optional,
            'scope':'Workflow core; --full additionally requires app tool availability, --with-serena requires optional semantic packages. Availability is not a successful app build or full audit.',
            'hint':'Run setup for local analysis packages; activate platform toolchains separately. No toolchains/downloads/builds are launched by doctor.'}


def setup(root,serena=False,offline=False):
    import bootstrap
    prepared=bootstrap.prepare(root)
    prerequisites(root)
    if serena and sys.version_info<(3,11):raise ValueError('Serena requires Python 3.11+')
    before=doctor(root,serena=serena)
    if offline:return {**before,'status':'PASS' if before['status']=='PASS' and prepared['status']=='PASS' else 'INCOMPLETE','bootstrap':prepared,'reused':before['status']=='PASS','offline':True,'note':'Offline mode prepares local support/history but does not download dependencies'}
    log=registry.safe(root,'.work/workflow-setup.log');log.parent.mkdir(exist_ok=True)
    changed=False
    with registry.lock(root,timeout=1),log.open('w') as output:
        def install(argv,timeout=300):
            done=subprocess.run(argv,cwd=root,stdout=output,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,timeout=timeout,env={**os.environ,'npm_config_cache':str(registry.safe(root,'.work/npm-cache')),'PIP_CACHE_DIR':str(registry.safe(root,'.work/pip-cache'))})
            if done.returncode:raise ValueError(f'Provisioning failed ({Path(str(argv[0])).name}); inspect {log}')
        if next(item for item in before['checks'] if item['name']=='codegraph')['status']!='PASS':
            if not shutil.which('npm'):raise ValueError('Node/npm must be installed before setup')
            install(['npm','ci','--prefix','workflow/codegraph','--ignore-scripts','--no-audit','--no-fund']);changed=True
        for name,requirements,needed in [('code-tools-python','requirements.txt',True),('serena-python','requirements-serena.txt',serena)]:
            if not needed:continue
            python=interpreter(root,name)
            capability='parsers' if name=='code-tools-python' else 'serena'
            ready=next((item['status']=='PASS' for item in before['checks']+before['optional'] if item['name']==capability),False)
            if ready:continue
            if not python.is_file():install([sys.executable,'-m','venv',str(python.parent.parent)])
            verify_venv(root,python)
            install([str(python),'-m','pip','install','--disable-pip-version-check','-r',str(root/'workflow/codegraph'/requirements)]);changed=True
    after=doctor(root,serena=serena)
    return {**after,'status':'PASS' if after['status']=='PASS' and prepared['status']=='PASS' else 'INCOMPLETE','bootstrap':prepared,'reused':not changed,'log':str(log)}


def status(root):
    top=Path(run(['git','rev-parse','--show-toplevel'],root)).resolve()
    if top!=root:raise ValueError('Use the repository root')
    result={'branch':run(['git','branch','--show-current'],root),'commit':run(['git','rev-parse','HEAD'],root),
            'working_tree_changes':run(['git','status','--short'],root).splitlines()}
    with registry.lock(root):
        try:
            checked=registry._validate(root);state=registry.load(root)
            result.update(status='PASS',registry=checked,next_mission=f'M{state["next_mission"]:03d}',next_tor=f'TOR{state["next_tor"]:03d}',
                          pending=[{'id':identifier,'title':task['title']} for identifier,task in state['tasks'].items() if task['status']=='reserved'],history=registry.HISTORY)
        except (ValueError,OSError,KeyError,TypeError) as error:result.update(status='INCOMPLETE',error=str(error))
    return result
