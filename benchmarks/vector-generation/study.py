#!/usr/bin/env python3
"""Isolated, reproducible source-distribution/build study. No production migration."""
import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
STYLES = ['outlined', 'rounded', 'sharp', 'themed']
MODES = ['static', 'generated', 'hybrid']
MODULES = [f':modules:material-vectors-{s}' for s in STYLES]
GENERATED = '*.generated.kt'

def module(root, style):
    return root / f'symbols/material-vectors-{style}'

def sha(data): return hashlib.sha256(data).hexdigest()

def tar_bytes(files):
    b = io.BytesIO()
    with tarfile.open(fileobj=b, mode='w', format=tarfile.PAX_FORMAT) as t:
        for name, data in sorted(files.items()):
            i = tarfile.TarInfo(name); i.size = len(data); i.mode = 0o755 if name == 'gradlew' else 0o644
            t.addfile(i, io.BytesIO(data))
    return b.getvalue()

def zip_bytes(files):
    buffer=io.BytesIO()
    with zipfile.ZipFile(buffer,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,data,compresslevel=9)
    return buffer.getvalue()

def source_files(root):
    return {str(p.relative_to(root)): p.read_bytes() for p in root.rglob('*')
            if p.is_file() and not any(s in {'build', '.gradle', '.kotlin', '__pycache__'} for s in p.relative_to(root).parts)}

def prepare(work, ref):
    work.mkdir(parents=True, exist_ok=True)
    head = subprocess.check_output(['git', 'rev-parse', '--verify', f'{ref}^{{commit}}'], cwd=ROOT, text=True).strip()
    archive = subprocess.check_output(['git', 'archive', head], cwd=ROOT)
    result = {'head': head, 'archives': {}, 'generated_reference': {}}
    for mode in MODES:
        dest = work / mode
        if dest.exists(): raise SystemExit(f'Refusing to overwrite {dest}')
        dest.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as t: t.extractall(dest, filter='data')
        (dest / 'gradlew').chmod(0o755)
        if mode == 'generated':
            (dest / 'vector-study').mkdir()
            shutil.copy2(Path(__file__).with_name('prepare_sources.py'), dest / 'vector-study/prepare_sources.py')
        for style in STYLES:
            m = module(dest, style)
            files = list((m / 'src/commonMain/kotlin').rglob(GENERATED))
            if mode == 'static':
                for f in files: result['generated_reference'][str(f.relative_to(dest))] = sha(f.read_bytes())
            if mode == 'generated':
                for f in files: f.unlink()
                font_input = '' if style == 'themed' else f'inputs.file(rootProject.file("fonts/material/{style}/composeResources/font/material_symbols_{style}_variable.ttf"))'
                addition = f'''
// Isolated measurement variant: generate the exact original Kotlin sources.
val prepareVectorSources by tasks.registering(Exec::class) {{
    inputs.files(rootProject.file("tools/generate_material_vectors.py"), rootProject.file("vector-study/prepare_sources.py"), rootProject.file("fonts/material/MaterialSymbols.codepoints"))
    {font_input}
    inputs.property("fonttoolsVersion", "4.60.2")
    inputs.property("style", "{style}")
    outputs.dir(layout.buildDirectory.dir("generated/vectorStudy/kotlin"))
    outputs.cacheIf {{ true }}
    commandLine(providers.gradleProperty("vectorStudyPython").get(), rootProject.file("vector-study/prepare_sources.py"), "--root", rootProject.projectDir, "--style", "{style}", "--output", layout.buildDirectory.dir("generated/vectorStudy/kotlin").get().asFile)
}}
kotlin.sourceSets.named("commonMain") {{ kotlin.srcDir(prepareVectorSources) }}
'''
            elif mode == 'hybrid' and style != 'themed':
                bodies = {}
                for f in files:
                    if not re.fullmatch(r'[A-Za-z]+Icons\d+\.generated\.kt', f.name): continue
                    text = f.read_text()
                    getter = re.compile(r'^val Icons\.[^\n]+\n    get\(\) =[^\n]+\n', re.M)
                    getters = getter.findall(text)
                    assert getters and 'private object ' in text, f
                    header = text.split('\nval Icons.', 1)[0]
                    facade_header = '\n'.join(line for line in header.splitlines() if not line.startswith('import ') or line in ['import androidx.compose.ui.graphics.vector.ImageVector', 'import io.github.hlcaptain.symbols.material.Icons'])
                    f.write_text(facade_header + '\n\n' + '\n'.join(getters))
                    implementation = getter.sub('', text).replace('private object ', 'internal object ')
                    relative = f.relative_to(m / 'src/commonMain/kotlin')
                    body_name = str(relative).replace('Icons', 'Bodies')
                    bodies[body_name] = implementation.encode()
                (m / 'vector-geometry.tar.gz').write_bytes(gzip.compress(tar_bytes(bodies), compresslevel=9, mtime=0))
                addition = '''
// Isolated middle ground: static API, pre-generated compressed implementation.
val prepareVectorSources by tasks.registering(Sync::class) {
    from(tarTree(resources.gzip(layout.projectDirectory.file("vector-geometry.tar.gz"))))
    into(layout.buildDirectory.dir("generated/vectorStudy/kotlin"))
    outputs.cacheIf { true }
}
kotlin.sourceSets.named("commonMain") { kotlin.srcDir(prepareVectorSources) }
'''
            else:
                addition = '\nval prepareVectorSources by tasks.registering\n'
            addition += '\ntasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach { dependsOn(prepareVectorSources) }\n'
            with (m / 'build.gradle.kts').open('a') as f: f.write(addition)
        files = source_files(dest)
        packed = gzip.compress(tar_bytes(files), compresslevel=9, mtime=0)
        (work / f'{mode}-source.tar.gz').write_bytes(packed)
        vectors = {k: v for k, v in files.items() if any(k.startswith(f'symbols/material-vectors-{s}/') for s in STYLES)}
        vector_zip=zip_bytes(vectors)
        (work / f'{mode}-vectors.zip').write_bytes(vector_zip)
        kt = [v for k, v in vectors.items() if k.endswith('.kt')]
        result['archives'][mode] = {'snapshot_targz_bytes': len(packed), 'vectors_zip_bytes':len(vector_zip),
           'tracked_vector_kt_lines':sum(len(v.splitlines()) for v in kt), 'tracked_vector_kt_bytes':sum(map(len,kt)),
           'tracked_vector_kt_files':len(kt), 'vector_tree_bytes':sum(map(len,vectors.values())),
           'public_named_getters':sum(len(re.findall(rb'^val Icons\.', v, re.M)) for v in kt),
           'source_archive_sha256':sha(packed)}
    input_names=['fonts/material/MaterialSymbols.codepoints','tools/generate_material_vectors.py']+[
        f'fonts/material/{s}/composeResources/font/material_symbols_{s}_variable.ttf' for s in STYLES if s!='themed']
    inputs_zip=zip_bytes({name:(work/'static'/name).read_bytes() for name in input_names})
    (work/'vector-generation-inputs.zip').write_bytes(inputs_zip)
    result['vector_generation_inputs_zip_bytes']=len(inputs_zip)
    (work / 'inventory.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result['archives'],indent=2))

def run_build(work, mode, label, tasks, python, cache=False, configuration_cache=False):
    dest=work/mode; logs=work/'logs';logs.mkdir(exist_ok=True)
    cmd=['./gradlew',*tasks,'--max-workers=1','--console=plain',
         '--configuration-cache' if configuration_cache else '--no-configuration-cache',
         '--build-cache' if cache else '--no-build-cache','-Dorg.gradle.jvmargs=-Xmx8192M -Dfile.encoding=UTF-8',
         '-Pkotlin.compiler.execution.strategy=in-process',f'-PvectorStudyPython={python}', '--profile']
    env=dict(os.environ)
    if not env.get('JAVA_HOME'):
        raise SystemExit('Set JAVA_HOME to a JDK 21 installation.')
    start=time.monotonic()
    with (logs/f'{mode}-{label}.log').open('w') as log:
        p=subprocess.run(cmd,cwd=dest,env=env,stdout=log,stderr=subprocess.STDOUT)
    row={'mode':mode,'label':label,'seconds':time.monotonic()-start,'exit_code':p.returncode,'tasks':tasks,'cache':cache,'configuration_cache':configuration_cache,'command':cmd}
    report=re.search(r'profiling report at: file://([^\s]+)',(logs/f'{mode}-{label}.log').read_text())
    if report and Path(report.group(1)).exists():
        # Gradle profile names have second precision; sub-second builds can overwrite them.
        saved=logs/f'{mode}-{label}.html'
        shutil.copy2(report.group(1),saved)
        row['profile']=str(saved.resolve())
    with (work/'builds.jsonl').open('a') as f:f.write(json.dumps(row)+'\n')
    print(json.dumps(row),flush=True)
    if p.returncode:raise SystemExit(f'Build failed: {logs}/{mode}-{label}.log')

def capture_artifacts(work,mode,label):
    out=work/'artifacts'/mode/label;out.mkdir(parents=True,exist_ok=True)
    for style in STYLES:
        m=module(work/mode,style)
        for folder,ext in [('build/libs','*.jar'),('build/outputs/aar','*.aar')]:
            for p in (m/folder).glob(ext):shutil.copy2(p,out/p.name)
    for p in (work/mode/'benchmarks/shrinkable-vectors/build/outputs/apk').rglob('*.apk'):
        shutil.copy2(p,out/p.name)

def measure(work,python,repeats):
    tasks=[f'{m}:jvmJar' for m in MODULES]+[f'{m}:bundleReleaseAar' for m in MODULES]+[f'{m}:jvmSourcesJar' for m in MODULES]
    # Bootstrap dependencies/configuration outside the timed target-clean series.
    for mode in MODES:
        run_build(work,mode,'bootstrap',tasks,python)
        capture_artifacts(work,mode,'bootstrap')
    for iteration in range(repeats):
        for mode in MODES[iteration%3:]+MODES[:iteration%3]:
            for s in STYLES:shutil.rmtree(module(work/mode,s)/'build',ignore_errors=True)
            run_build(work,mode,f'clean-{iteration}',tasks,python)
            run_build(work,mode,f'noop-{iteration}',tasks,python)
            file=module(work/mode,'themed')/'src/commonMain/kotlin/io/github/hlcaptain/symbols/material/vectors/themed/MaterialSymbolThemedVector.kt'
            original=file.read_bytes();file.write_bytes(original.replace(b'asOutlinedImageVector(autoMirror)', b'asOutlinedImageVector(!autoMirror)'))
            try:run_build(work,mode,f'edit-{iteration}',tasks,python)
            finally:file.write_bytes(original)
            run_build(work,mode,f'edit-restore-{iteration}',tasks,python)
    for mode in MODES:
        for s in STYLES:shutil.rmtree(module(work/mode,s)/'build',ignore_errors=True)
        run_build(work,mode,'cache-seed',tasks,python,cache=True)
        for s in STYLES:shutil.rmtree(module(work/mode,s)/'build',ignore_errors=True)
        run_build(work,mode,'cache-restore',tasks,python,cache=True)
        capture_artifacts(work,mode,'final')
        run_build(work,mode,'shrinker',[':benchmarks:shrinkable-vectors:assembleUnshrunk',':benchmarks:shrinkable-vectors:assembleShrunk'],python)
        capture_artifacts(work,mode,'with-apk')
        with (work/'logs'/f'{mode}-shrinker-verification.log').open('w') as f:
            subprocess.run(['python3','benchmarks/shrinkable-vectors/verify.py'],cwd=work/mode,stdout=f,stderr=subprocess.STDOUT,check=True)

def verify_sources(work):
    reference=json.loads((work/'inventory.json').read_text())['generated_reference']
    generated_files=[f for s in STYLES for f in (module(work/'generated',s)/'build/generated/vectorStudy/kotlin').rglob('*.kt')]
    assert len(generated_files) == len(reference), 'Unexpected generated file count'
    for name, expected in reference.items():
        style=next(s for s in STYLES if name.startswith(f'symbols/material-vectors-{s}/'))
        relative=name.split('/src/commonMain/kotlin/',1)[1]
        generated=module(work/'generated',style)/'build/generated/vectorStudy/kotlin'/relative
        assert sha(generated.read_bytes()) == expected, name
    for style in STYLES:
        pattern=r'^val Icons\.[^\n]+'
        declarations=[]
        for mode in MODES:
            m=module(work/mode,style)
            roots=[m/'src/commonMain/kotlin',m/'build/generated/vectorStudy/kotlin']
            declarations.append(sorted(d for root in roots for f in root.rglob('*.kt')
                                       for d in re.findall(pattern,f.read_text(),re.M)))
        assert declarations[0] == declarations[1] == declarations[2], style
    print(f'Exact generated sources verified: {len(reference)} files; named declarations match all modes.')

def validate(work, python, repeats):
    for mode in MODES:
        run_build(work,mode,'contract-tests',[f'{m}:jvmTest' for m in MODULES],python)
    verify_sources(work)
    for mode in ['generated','hybrid']:
        for style in STYLES:
            shutil.rmtree(module(work/mode,style)/'build/generated/vectorStudy',ignore_errors=True)
        try:
            run_build(work,mode,'idea-import',[f'{m}:prepareKotlinIdeaImport' for m in MODULES],python)
        except SystemExit as error:
            # A missing IDE hook is a measured limitation; still test explicit preparation.
            print(f'IDE import probe failed: {error}',flush=True)
        for iteration in range(repeats):
            for style in STYLES:
                shutil.rmtree(module(work/mode,style)/'build/generated/vectorStudy',ignore_errors=True)
            run_build(work,mode,f'prepare-only-{iteration}',[f'{m}:prepareVectorSources' for m in MODULES],python)
    for mode in MODES:
        tasks=[f'{m}:{t}' for t in ['jvmJar','bundleReleaseAar','jvmSourcesJar'] for m in MODULES]
        run_build(work,mode,'configuration-seed',tasks,python,configuration_cache=True)
        for iteration in range(repeats):
            run_build(work,mode,f'configuration-reuse-{iteration}',tasks,python,configuration_cache=True)
    verify_sources(work)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['prepare','measure','build','verify','validate'])
    p.add_argument('--work',type=Path,required=True)
    p.add_argument('--ref',default='02ff132ae044343352318306448edc5c17b6792d',help='Baseline commit for prepare; never changes the working checkout.')
    p.add_argument('--python',default=sys.executable)
    p.add_argument('--repeats',type=int,default=3)
    p.add_argument('--mode',choices=MODES,default='generated')
    p.add_argument('--label',default='probe')
    p.add_argument('--task',action='append')
    p.add_argument('--configuration-cache',action='store_true')
    a=p.parse_args()
    if a.action=='prepare':prepare(a.work,a.ref)
    elif a.action=='measure':measure(a.work,a.python,a.repeats)
    elif a.action=='verify':verify_sources(a.work)
    elif a.action=='validate':validate(a.work,a.python,a.repeats)
    else:run_build(a.work,a.mode,a.label,a.task or [f'{m}:prepareVectorSources' for m in MODULES],a.python,configuration_cache=a.configuration_cache)
if __name__=='__main__':main()
