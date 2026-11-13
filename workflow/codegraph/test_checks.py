"""Heuristic test-quality and coverage evidence; never executes or deletes tests."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import native_bindings

def balanced_end(text: str, start: int, opening: str, closing: str, language: str = 'dart') -> int | None:
    """Index after the bracket that closes the one at `start`, ignoring strings, characters and comments."""
    depth, index, size = 0, start, len(text)
    quotes = '\'"' if language in ('dart', 'kotlin') else '"'
    while index < size:
        char = text[index]
        if text.startswith('//', index):
            index = text.find('\n', index)
            index = size if index == -1 else index
            continue
        if text.startswith('/*', index):
            index = text.find('*/', index + 2)
            index = size if index == -1 else index + 2
            continue
        if char == "'" and language == 'rust':  # a character literal, otherwise a lifetime
            if text[index + 1:index + 2] == '\\':
                index = text.find("'", index + 2) + 1 or size
            elif text[index + 2:index + 3] == "'":
                index += 3
            else:
                index += 1
            continue
        if char == "'" and language == 'kotlin':
            index = text.find("'", index + (3 if text[index + 1:index + 2] == '\\' else 2)) + 1 or size
            continue
        if char in quotes:
            raw = language == 'dart' and text[index - 1:index] == 'r' or language == 'rust' and text[index - 1:index] in ('r', '#')
            triple = text.startswith(char * 3, index) and language != 'rust'
            index += 3 if triple else 1
            while index < size:
                if not raw and text[index] == '\\':
                    index += 2
                    continue
                if text.startswith(char * 3, index) if triple else text[index] == char:
                    index += 3 if triple else 1
                    break
                index += 1
            continue
        if char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return index + 1
        index += 1
    return None

STARTS = {
    'dart': re.compile(r'^[ \t]*(?:test|testWidgets)\s*\(', re.M),
    'rust': re.compile(r'#\[(?:tokio::)?test(?:\([^\]]*\))?\]\s*(?:#\[[^\]]*\]\s*)*(?:async\s+)?fn\s+(?P<name>\w+)\s*\(', re.M),
    'kotlin': re.compile(r'@Test\b[^\n]*\n(?:[ \t]*@[^\n]*\n)*[ \t]*(?:(?:public|private|internal|suspend)\s+)*fun\s+(?P<name>`[^`\n]+`|\w+)\s*\(',re.M),
    'java': re.compile(r'@Test\b[^\n]*\n(?:[ \t]*@[^\n]*\n)*[ \t]*(?:(?:public|protected|private|final|static)\s+)*void\s+(?P<name>\w+)\s*\(',re.M),
}
ASSERT = re.compile(r'\b(?:expect\w*|assert\w*|verify\w*|check\w*|ensure\w*|fail|panic|unreachable)\s*!?\s*\(')


def lead(path,line,code,message,category):
    return {'path':path,'line':line,'code':code,'message':message,'categories':[category],
            'confidence':'REVIEW_REQUIRED','evidence':'heuristic' if category=='tests' else 'coverage-or-reference-hint'}


def blocks(path,text,language):
    pattern=STARTS.get(language)
    if not pattern:return [],['Unsupported test syntax/language']
    # Search Rust annotations in the original, but reject comments/string occurrences.
    code=native_bindings.masked(text)
    result=[];errors=[]
    for found in pattern.finditer(text if language!='dart' else code):
        if language!='dart' and code[found.start():found.start()+1]==' ':continue
        number=text.count('\n',0,found.start())+1
        if language=='dart':
            name=re.match(r'\s*(?:r)?([\'\"])([^\n]*?)\1',text[found.end():])
            if not name:
                errors.append(f'line {number}: computed/multiline test name');continue
            start=found.end()+name.end()
            end=balanced_end(text,code.index('(',found.start()),'(',')',language)
            title=name.group(2)
        else:
            start=code.find('{',found.end())
            end=balanced_end(text,start,'{','}',language) if start>=0 else None
            title=found['name'].strip('`')
        if end is None:
            errors.append(f'line {number}: unparsed test body');continue
        body=text[start:end]
        result.append({'path':path,'line':number,'name':title,'body':body,'head':text[found.start():start],
                       'digest':hashlib.sha256(body.strip().encode()).hexdigest()})
    if not result and not errors:errors.append('No supported literal test declarations recognized; may be a helper or custom/parameterized suite')
    return result,errors


def inspect_tests(root,files,languages,is_test):
    findings=[];first={};parsed=0;limits={}
    for language,paths in files.items():
        for path in paths:
            text=(root/path).read_text(errors='replace')
            if not is_test(path) and not (language=='rust' and '#[' in text):continue
            items,errors=blocks(path,text,language)
            # Ordinary Rust sources with no tests are not unsupported test files.
            if errors and (is_test(path) or items or re.search(r'#\[(?:tokio::)?test',native_bindings.masked(text))):limits[path]=errors
            for block in items:
                parsed+=1;code=native_bindings.masked(block['body'])
                expected=re.search(r'\bexpected\s*=|\bshould_panic\b',block['head'])
                if not ASSERT.search(code) and not expected:
                    findings.append(lead(path,block['line'],'assertion_free',f'Test {block["name"]!r} has no recognized assertion; check helper assertions, expected exceptions and failure behavior','tests'))
                same=first.setdefault((language,block['digest']),block)
                if same is not block and len(block['body'].strip())>40:
                    findings.append(lead(path,block['line'],'same_body',f'Test {block["name"]!r} matches body at {same["path"]}:{same["line"]}; setup, parameters, fixtures and protected regression intent may differ','tests'))
    return findings,{'status':'PARTIAL' if limits else 'RAN','tests_read':parsed,'syntax_limits':limits,
                     'reason':'Heuristic body inspection only; helpers, parameterization and assertions require review'}


def direct_gaps(root,source_files,all_tests):
    corpus='\n'.join((root/p).read_text(errors='replace') for p in all_tests)
    # Remove comments while retaining import/string evidence, including filenames.
    corpus=native_bindings.TOKENS.sub(lambda m: ' ' if m.group().startswith(('//','/*')) else m.group(),corpus)
    findings=[]
    for language,paths in source_files.items():
        for path in paths:
            own=(root/path).read_text(errors='replace')
            if language=='rust' and re.search(r'#\[\s*(?:cfg\s*\(\s*test\s*\)|(?:tokio::)?test)',native_bindings.masked(own)):continue
            name=Path(path).name if language=='dart' else Path(path).stem
            if not re.search(r'(?<![\w])'+re.escape(name)+r'(?![\w])',corpus):
                findings.append(lead(path,1,'no_direct_test','No direct filename/name evidence in discovered tests; investigate indirect coverage before adding a test','gaps'))
    return findings,{'status':'RAN','test_files_read':len(all_tests),'reason':'Direct-name heuristic, not a call graph or proof of missing behavioral coverage'}


def inspect_coverage(root,source_files,settings):
    findings=[];statuses={}
    for language,paths in source_files.items():
        config=settings.get(language)
        if not config:
            statuses[language]={'status':'UNAVAILABLE','reason':'No LCOV input configured'};continue
        report=root/config['lcov']
        if not report.is_file():
            statuses[language]={'status':'UNAVAILABLE','reason':f'Missing {config["lcov"]}', 'produce':config['produce']};continue
        if report.is_symlink() or not report.resolve().is_relative_to(root):
            statuses[language]={'status':'INCOMPLETE','reason':'LCOV path escapes checkout or is a symlink'};continue
        lines={};current=None
        try:
            for raw in report.read_text().splitlines():
                if raw.startswith('SF:'):
                    file=Path(raw[3:]);base=root/config.get('cwd','.')
                    try:current=(file if file.is_absolute() else base/file).resolve().relative_to(root).as_posix()
                    except ValueError:current=None
                elif raw=='end_of_record':current=None
                elif raw.startswith('DA:') and current:
                    n,hits=map(int,raw[3:].split(',')[:2])
                    if n<1 or hits<0:raise ValueError('Invalid LCOV line/hit count')
                    bucket=lines.setdefault(current,{})
                    bucket[n]=bucket.get(n,0)+hits
            if not lines:raise ValueError('No usable DA records in LCOV')
        except (OSError,ValueError) as error:
            statuses[language]={'status':'INCOMPLETE','reason':str(error)};continue
        newer=[p for p in paths if (root/p).stat().st_mtime>report.stat().st_mtime]
        statuses[language]={'status':'PARTIAL' if newer else 'RAN','report':config['lcov'],
                            'mtime':report.stat().st_mtime,'sources_newer_than_report':newer,
                            'reason':'Existing run evidence only; source revision/test selection not verified'}
        for path in paths:
            if path in newer:continue
            if path not in lines:
                findings.append(lead(path,1,'not_in_coverage','Not present in supplied LCOV; verify instrumentation exclusions and the original run scope','gaps'));continue
            missed=[n for n,hits in sorted(lines[path].items()) if hits==0]
            if missed:findings.append(lead(path,missed[0],'uncovered_lines',f'{len(missed)} recorded lines had zero hits in the supplied run; inspect assertions and behavior','gaps'))
    return findings,statuses
