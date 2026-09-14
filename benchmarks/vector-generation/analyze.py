#!/usr/bin/env python3
"""Summarize measured study outputs; never synthesizes missing build runs."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import statistics
import zipfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('work',type=Path)
a=p.parse_args();w=a.work
inventory=json.loads((w/'inventory.json').read_text())
runs=[json.loads(line) for line in (w/'builds.jsonl').read_text().splitlines()]
def duration(s):
    seconds=0
    for n,unit in re.findall(r'([\d.]+)(h|m|s)',s): seconds+=float(n)*{'h':3600,'m':60,'s':1}[unit]
    return seconds
for r in runs:
    log=w/'logs'/f"{r['mode']}-{r['label']}.log"
    m=re.search(r'profiling report at: file://([^\s]+)',log.read_text())
    if not m:continue
    profile=Path(r.get('profile',m.group(1)))
    if not profile.exists():continue
    text=profile.read_text().split('<h2>Task Execution</h2>')[-1]
    tasks=[]
    for tr in re.findall(r'<tr>(.*?)</tr>',text,re.S):
        cols=[html.unescape(re.sub('<[^>]*>','',v)).strip() for v in re.findall(r'<td[^>]*>(.*?)</td>',tr,re.S)]
        if len(cols)==3 and cols[0].startswith(':'):
            tasks.append({'path':cols[0],'seconds':duration(cols[1]),'status':cols[2]})
    r['profile_tasks']=tasks
summary={}
for mode in ['static','generated','hybrid']:
    summary[mode]={}
    for kind in ['bootstrap','clean','noop','edit','edit-restore','cache-seed','cache-restore','shrinker','prepare-only','idea-import','contract-tests','configuration-seed','configuration-reuse']:
        rs=[r for r in runs if r['mode']==mode and (r['label']==kind or re.fullmatch(re.escape(kind)+r'-\d+',r['label'])) and r['exit_code']==0]
        if rs:
            summary[mode][kind]={'n':len(rs),'median_seconds':statistics.median(r['seconds'] for r in rs),'seconds':[r['seconds'] for r in rs],
             'prepare_task_seconds':[sum(t['seconds'] for t in r.get('profile_tasks',[]) if t['path'].endswith(':prepareVectorSources')) for r in rs],
             'compile_task_seconds':[sum(t['seconds'] for t in r.get('profile_tasks',[])
                                        if ':material-vectors-' in t['path'] and t['path'].endswith((':compileKotlinJvm', ':compileReleaseKotlinAndroid'))) for r in rs]}

def normalized_entries(path):
    with zipfile.ZipFile(path) as z:
        return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
artifacts={}
for mode in ['static','generated','hybrid']:
    directory=w/'artifacts'/mode/'final'
    if not directory.exists():directory=w/'artifacts'/mode/'bootstrap'
    artifacts[mode]={}
    for f in sorted(directory.glob('*')):
        if f.suffix in ['.jar','.aar','.apk']:
            artifacts[mode][f.name]={'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'entries':normalized_entries(f)}
    for f in (w/'artifacts'/mode/'with-apk').glob('*.apk'):
        artifacts[mode][f.name]={'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'entries':normalized_entries(f)}
comparisons=[]
for mode in ['generated','hybrid']:
    for name,b in artifacts[mode].items():
        old=artifacts['static'].get(name)
        if old:
            changed=[n for n in old['entries'].keys()|b['entries'].keys() if old['entries'].get(n)!=b['entries'].get(n)]
            comparisons.append({'mode':mode,'artifact':name,'baseline_bytes':old['bytes'],'bytes':b['bytes'],'whole_file_equal':old['sha256']==b['sha256'],'changed_entries':changed})
environment=w/'environment.json'
result={'inventory':inventory,'environment':json.loads(environment.read_text()) if environment.exists() else {},'build_summary':summary,'runs':runs,'artifact_comparisons':comparisons}
(w/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
for r in runs:
    if r['exit_code']:
        print('FAILED',r['mode'],r['label'],'exit',r['exit_code'])
for mode,rs in summary.items():
    print(mode)
    for name,s in rs.items():print(' ',name,s['n'],round(s['median_seconds'],3),'prepare',s['prepare_task_seconds'])
for c in comparisons:
    print(c['mode'],c['artifact'],c['baseline_bytes'],c['bytes'],'equal',c['whole_file_equal'],'entrychanges',len(c['changed_entries']))
