"""Tracked task identities and derived history; no task executor or source fingerprints."""
from __future__ import annotations
from contextlib import contextmanager
import json
import os
from pathlib import Path, PurePosixPath
import re
import tempfile
import time
import uuid
import subprocess

REGISTRY = '.specify/task-registry.json'
HISTORY = 'TASK_HISTORY.md'
ROW = re.compile(r'^- \[([ xX])\] ((?:M\d+-)?(?:TOR|T)\d+) (.+)$', re.M)
CANDIDATE = re.compile(r'^- \[[ xX]\].*$', re.M)


def rows(text):
    result=[]
    for line in CANDIDATE.findall(text):
        found=ROW.fullmatch(line)
        if not found:raise ValueError(f'Malformed task checkbox/ID: {line}')
        result.append(found.groups())
    return result


def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result: raise ValueError(f'Duplicate JSON key: {key}')
        result[key]=value
    return result


def safe(root,relative):
    p=PurePosixPath(relative)
    if not relative or p.is_absolute() or '..' in p.parts or '\\' in relative or p.as_posix()!=relative:
        raise ValueError(f'Unsafe repository path: {relative}')
    target=root/relative
    if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'Symlink/outside-root path: {relative}')
    return target


def atomic(path,text):
    path.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(mode='w',encoding='utf-8',dir=path.parent,delete=False) as out:
        temporary=Path(out.name)
        try:out.write(text);out.flush();os.fsync(out.fileno())
        except BaseException:temporary.unlink(missing_ok=True);raise
    try:os.replace(temporary,path)
    finally:temporary.unlink(missing_ok=True)


@contextmanager
def lock(root,timeout=10):
    work=safe(root,'.work');work.mkdir(exist_ok=True)
    if work.is_symlink():raise ValueError('Managed .work must not be a symlink')
    path=work/'task-registry.lock';deadline=time.monotonic()+timeout
    while True:
        try:path.mkdir();break
        except FileExistsError:
            if time.monotonic()>=deadline:raise ValueError('Registry locked; inspect active processes before removing a stale .work/task-registry.lock')
            time.sleep(.025)
    try:yield
    finally:path.rmdir()


def title(value):
    if not isinstance(value,str) or not value.strip() or any(ord(c)<32 for c in value):raise ValueError('Title must be one nonempty line')
    return value.strip()


def empty():
    return {'version':1,'next_mission':1,'next_tor':1,'next_completion':1,'missions':{},'tasks':{}}


def history(state):
    rows=sorted(((t['completion'],identifier,t['title']) for identifier,t in state['tasks'].items() if t['status']=='complete'))
    return '# Task history\n\n'+''.join(f'- {identifier} — {name}\n' for _,identifier,name in rows)


def check_data(state):
    if not isinstance(state,dict) or state.get('version')!=1:raise ValueError('Unsupported task registry version')
    if not isinstance(state.get('missions'),dict) or not isinstance(state.get('tasks'),dict):raise ValueError('Invalid registry collections')
    missions=state['missions'];tasks=state['tasks'];tor=set();orders=set();max_mission=0;max_t={m:0 for m in missions}
    for identifier,mission in missions.items():
        number=int(identifier[1:])
        if number<1 or identifier!=f'M{number:03d}':raise ValueError(f'Invalid mission ID: {identifier}')
        if not mission.get('nonce'):raise ValueError(f'Missing mission reservation: {identifier}')
        title(mission['title']);max_mission=max(max_mission,number)
        p=PurePosixPath(mission['path'])
        if len(p.parts)!=2 or p.parts[0]!='specs' or '..' in p.parts:raise ValueError('Mission path must be specs/<feature>')
    if len({m['path'] for m in missions.values()})!=len(missions):raise ValueError('Mission paths are duplicated')
    finals=set()
    for identifier,task in tasks.items():
        match=re.fullmatch(r'(?:(M\d+)-)?(TOR|T)(\d+)',identifier)
        if not match:raise ValueError(f'Invalid task ID: {identifier}')
        mission,kind,raw=match.groups();number=int(raw)
        expected=(mission+'-' if mission else '')+f'{kind}{number:03d}'
        if number<1 or identifier!=expected or (kind=='T' and not mission):raise ValueError(f'Noncanonical task ID: {identifier}')
        if task['mission']!=mission or task['kind']!=kind or (mission and mission not in missions):raise ValueError(f'Wrong mission/kind: {identifier}')
        title(task['title'])
        if not task.get('nonce'):raise ValueError(f'Missing task reservation: {identifier}')
        if kind=='TOR':
            if number in tor:raise ValueError(f'TOR number reused across missions/standalone: {number}')
            tor.add(number)
        else:max_t[mission]=max(max_t[mission],number)
        expected_record=f"{missions[mission]['path']}/tasks.md" if mission else f'.specify/quick-tasks/{identifier}.md'
        if task['record']!=expected_record:raise ValueError(f'Unexpected record path: {identifier}')
        if task.get('final'):
            if not mission or kind!='T' or mission in finals:raise ValueError('Invalid/duplicate final task')
            finals.add(mission)
        if task['status'] not in ('reserved','complete'):raise ValueError('Unknown task status')
        if task['status']=='complete':
            n=task['completion']
            if type(n)!=int or n<1 or n in orders:raise ValueError('Duplicate/invalid completion order')
            orders.add(n)
            evidence=task.get('evidence',{})
            if not evidence.get('imported') and not (evidence.get('reviewer') and evidence.get('result')=='PASS' and evidence.get('round') in (1,2,3) and evidence.get('checks')):raise ValueError(f'Missing completion evidence: {identifier}')
        elif task.get('completion') is not None:raise ValueError('Unfinished task has completion order')
    for key,maximum in [('next_mission',max_mission),('next_tor',max(tor,default=0)),('next_completion',max(orders,default=0))]:
        if type(state[key])!=int or state[key]<=maximum:raise ValueError(f'Counter would reuse an ID/order: {key}')
    for identifier,task in tasks.items():
        if task.get('final') and task['status']=='complete' and any(t['mission']==task['mission'] and t['status']!='complete' for t in tasks.values()):raise ValueError('Completed mission has unfinished tasks')
    for m,maximum in max_t.items():
        if type(missions[m]['next_task'])!=int or missions[m]['next_task']<=maximum:raise ValueError(f'Task counter would reuse an ID: {m}')


def load(root):
    state=json.loads(safe(root,REGISTRY).read_text(encoding='utf-8'),object_pairs_hook=unique)
    check_data(state);return state


def save(root,state):
    check_data(state)
    atomic(safe(root,REGISTRY),json.dumps(state,indent=2,ensure_ascii=False,sort_keys=True)+'\n')
    atomic(safe(root,HISTORY),history(state))


def record_complete(text):
    statuses=re.findall(r'^- Status: (IN_PROGRESS|COMPLETE)\s*$',text,re.M)
    if len(statuses)!=1:raise ValueError('Standalone record requires one explicit Status line')
    return statuses[0]=='COMPLETE'


def record_rows(root,state):
    seen={}
    for relative in sorted({t['record'] for t in state['tasks'].values()}):
        text=safe(root,relative).read_text(encoding='utf-8')
        if relative.endswith('/tasks.md'):
            for check,identifier,name in rows(text):
                if identifier in seen:raise ValueError(f'Duplicate task checkbox: {identifier}')
                final=name.startswith('[FINAL] ');name=name.removeprefix('[FINAL] ')
                seen[identifier]=(relative,check.lower()=='x',name,final)
        else:
            found=re.match(r'^# (TOR\d+) — (.+)\n',text)
            if not found:raise ValueError(f'Invalid standalone task header: {relative}')
            if found[1] in seen:raise ValueError('Duplicate task record')
            seen[found[1]]=(relative,record_complete(text),found[2],False)
    for identifier,task in state['tasks'].items():
        expected=(task['record'],task['status']=='complete',task['title'],bool(task.get('final')))
        if seen.get(identifier)!=expected:raise ValueError(f'Record/registry mismatch: {identifier}')
    if set(seen)!=set(state['tasks']):raise ValueError(f'Unregistered task rows: {sorted(set(seen)-set(state["tasks"]))}')
    # Detect standalone/feature files created outside the allocator too.
    paths={p.relative_to(root).as_posix() for p in (root/'.specify/quick-tasks').glob('TOR*.md')}
    for file in (root/'specs').glob('*/tasks.md'):
        if rows(file.read_text(encoding='utf-8')):paths.add(file.relative_to(root).as_posix())
    if paths!={t['record'] for t in state['tasks'].values()}:raise ValueError('Task files exist outside registered record paths')


def _validate(root,base=None):
    state=load(root);record_rows(root,state)
    if safe(root,HISTORY).read_text(encoding='utf-8')!=history(state):raise ValueError('TASK_HISTORY.md is not synchronized; run history')
    if base:
        if not re.fullmatch(r'[0-9a-fA-F]{7,40}',base):raise ValueError('Base must be a Git commit SHA')
        subprocess.run(['git','rev-parse','--verify',base+'^{commit}'],cwd=root,check=True,capture_output=True)
        present=subprocess.run(['git','ls-tree','-z',base,'--',REGISTRY],cwd=root,capture_output=True,check=True).stdout
        if present:
            old=subprocess.run(['git','show',f'{base}:{REGISTRY}'],cwd=root,capture_output=True,text=True,check=True)
            previous=json.loads(old.stdout,object_pairs_hook=unique);check_data(previous)
            for group,keys in [('missions',('nonce','path','title')),('tasks',('nonce','mission','kind','record','title','final'))]:
                for identifier,value in previous[group].items():
                    current=state[group].get(identifier)
                    if not current or any(current.get(k)!=value.get(k) for k in keys):raise ValueError(f'Reservation removed/reassigned since base: {identifier}')
                    if group=='tasks' and value['status']=='complete' and (current['status']!='complete' or current['completion']!=value['completion']):raise ValueError('Completed history was rewritten')
                    if group=='missions' and current['next_task']<value['next_task']:raise ValueError('Mission counter decreased')
            for identifier in set(state['missions'])-set(previous['missions']):
                if int(identifier[1:])<previous['next_mission']:raise ValueError('New mission reuses an earlier counter range')
            for identifier in set(state['tasks'])-set(previous['tasks']):
                task=state['tasks'][identifier];number=int(re.search(r'(\d+)$',identifier)[1])
                if task['kind']=='TOR' and number<previous['next_tor']:raise ValueError('New TOR reuses an earlier counter range')
                if task['kind']=='T' and task['mission'] in previous['missions'] and number<previous['missions'][task['mission']]['next_task']:raise ValueError('New task reuses an earlier counter range')
            if any(state[k]<previous[k] for k in ('next_mission','next_tor','next_completion')):raise ValueError('Global counter decreased')
    return {'status':'PASS','missions':len(state['missions']),'reserved':sum(t['status']=='reserved' for t in state['tasks'].values()),'completed':sum(t['status']=='complete' for t in state['tasks'].values())}


def validate(root,base=None):
    with lock(root):return _validate(root,base)


def new_mission(root,name,path):
    name=title(name);safe(root,path)
    if len(PurePosixPath(path).parts)!=2 or not path.startswith('specs/'):raise ValueError('Mission path must be specs/<feature>')
    with lock(root):
        state=load(root)
        if any(m['path']==path for m in state['missions'].values()):raise ValueError('Mission path already reserved')
        identifier=f'M{state["next_mission"]:03d}';state['next_mission']+=1
        state['missions'][identifier]={'title':name,'path':path,'next_task':1,'nonce':uuid.uuid4().hex}
        save(root,state);return identifier


def reserve(root,name,mission=None,tor=False,final=False):
    name=title(name)
    if name.startswith('[FINAL]'):raise ValueError('Use --final with a plain task title')
    with lock(root):
        state=load(root)
        if mission and mission not in state['missions']:raise ValueError('Unknown mission')
        if not mission:tor=True
        if final and (tor or not mission):raise ValueError('Final must be a regular mission task')
        existing=[t for t in state['tasks'].values() if t['mission']==mission and t.get('final')] if mission else []
        if existing and (final or existing[0]['status']=='complete'):raise ValueError('Mission final task already reserved/completed')
        if tor:number=state['next_tor'];state['next_tor']+=1;kind='TOR'
        else:number=state['missions'][mission]['next_task'];state['missions'][mission]['next_task']+=1;kind='T'
        identifier=(mission+'-' if mission else '')+f'{kind}{number:03d}'
        record=state['missions'][mission]['path']+'/tasks.md' if mission else f'.specify/quick-tasks/{identifier}.md'
        target=safe(root,record)
        if not mission and target.exists():raise ValueError('Standalone record exists; refusing overwrite')
        entry={'title':name,'mission':mission,'kind':kind,'record':record,'status':'reserved','nonce':uuid.uuid4().hex,'final':final,'completion':None}
        state['tasks'][identifier]=entry
        row=f'- [ ] {identifier} '+('[FINAL] ' if final else '')+name+'\n'
        if mission:
            content=target.read_text(encoding='utf-8') if target.exists() else f'# {mission} tasks\n\n'
            if existing:
                marker=next(i for i,t in state['tasks'].items() if t is not entry and t['mission']==mission and t.get('final'))
                position=content.index(f'- [ ] {marker} ');content=content[:position]+row+content[position:]
            else:content=content.rstrip()+'\n\n'+row
        else:content=f'# {identifier} — {name}\n\n- Status: IN_PROGRESS\n\n## Acceptance and checks\n\nFill the concrete request, baseline, paths, acceptance, map result and validation.\n\n## Review\n\nPending independent review (maximum three results).\n'
        save(root,state)  # Persist the reservation before ancillary record writes; never reuse on a crash.
        atomic(target,content);return {'id':identifier,'record':record}


def complete(root,identifier,reviewer,round_number,checks):
    reviewer=title(reviewer);checks=title(checks)
    if type(round_number)!=int or round_number not in (1,2,3):raise ValueError('Review round must be 1..3')
    with lock(root):
        state=load(root);record_rows(root,state)
        task=state['tasks'][identifier]
        if task['status']!='reserved':raise ValueError('Task already completed')
        if task.get('final') and any(t['status']!='complete' for i,t in state['tasks'].items() if t['mission']==task['mission'] and i!=identifier):raise ValueError('Final task has unfinished dependencies')
        target=safe(root,task['record']);content=target.read_text(encoding='utf-8')
        if task['mission']:content=content.replace(f'- [ ] {identifier} ',f'- [x] {identifier} ',1)
        else:content=content.replace('- Status: IN_PROGRESS','- Status: COMPLETE',1)
        task.update(status='complete',completion=state['next_completion'],evidence={'reviewer':reviewer,'round':round_number,'checks':checks,'result':'PASS'})
        state['next_completion']+=1
        save(root,state);atomic(target,content)
        return {'id':identifier,'status':'COMPLETE'}


def initialize(root,migrate=False):
    with lock(root):
        if safe(root,REGISTRY).exists():raise ValueError('Registry already exists; refusing reset')
        state=empty();rewrites={}
        def imported(identifier,name,mission,record,done,final=False):
            state['tasks'][identifier]={'title':title(name),'mission':mission,'kind':'TOR' if '-TOR' in identifier or identifier.startswith('TOR') else 'T','record':record,'status':'complete' if done else 'reserved','nonce':uuid.uuid4().hex,'final':final,'completion':state['next_completion'] if done else None,'evidence':{'imported':True,'source':record} if done else {}}
            if done:state['next_completion']+=1
        if migrate:
            for file in sorted((root/'specs').glob('*/tasks.md')):
                content=file.read_text(encoding='utf-8');items=rows(content)
                if not items:continue
                m=f'M{state["next_mission"]:03d}';state['next_mission']+=1
                record=file.relative_to(root).as_posix();name=content.splitlines()[0].lstrip('# ').removeprefix('Tasks: ')
                state['missions'][m]={'title':title(name),'path':file.parent.relative_to(root).as_posix(),'next_task':1,'nonce':uuid.uuid4().hex}
                for check,old,name in items:
                    match=re.fullmatch(r'(?:(M\d+)-)?(TOR|T)(\d+)',old);kind,number=match[2],int(match[3])
                    identifier=f'{m}-{kind}{number:03d}';final=name.startswith('[FINAL] ')
                    if identifier in state['tasks']:raise ValueError('Duplicate imported task')
                    imported(identifier,name.removeprefix('[FINAL] '),m,record,check.lower()=='x',final)
                    key='next_tor' if kind=='TOR' else None
                    if key:state[key]=max(state[key],number+1)
                    else:state['missions'][m]['next_task']=max(state['missions'][m]['next_task'],number+1)
                    content=re.sub(r'(?<![\w-])'+re.escape(old)+r'(?!\d)',identifier,content)
                rewrites[file]=content
            for file in sorted((root/'.specify/quick-tasks').glob('TOR*.md')):
                text=file.read_text(encoding='utf-8');match=re.match(r'^# (TOR\d+) — (.+)\n',text)
                if not match:raise ValueError('Invalid standalone task during migration')
                identifier,name=match.groups()
                if identifier in state['tasks']:raise ValueError('Duplicate imported TOR')
                imported(identifier,name,None,file.relative_to(root).as_posix(),record_complete(text))
                state['next_tor']=max(state['next_tor'],int(identifier[3:])+1)
        check_data(state)
        existing_history=safe(root,HISTORY)
        if existing_history.exists() and existing_history.read_text(encoding='utf-8')!=history(state):raise ValueError('Existing history differs from imported records; preserve and reconcile it explicitly before init')
        for file in rewrites:safe(root,file.relative_to(root).as_posix())
        save(root,state)
        for file,text in rewrites.items():atomic(file,text)
        return _validate(root)
