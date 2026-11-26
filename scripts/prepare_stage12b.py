#!/usr/bin/env python3
"""Build reproducible, synthetic Stage 12-B fixtures. Never runs or approves a device test."""
import argparse
import hashlib
import io
import json
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

CASES = [
    ('ko', ('docx',)), ('ko', ('txt', 'csv', 'xlsx')),
    ('en', ('pdf',)), ('en', ('txt', 'docx', 'csv')),
    ('ja', ('xlsx',)), ('ja', ('txt', 'csv', 'docx')),
    ('zh-Hans', ('txt',)), ('zh-Hans', ('docx', 'csv', 'xlsx')),
    ('zh-Hant', ('csv',)), ('zh-Hant', ('txt', 'docx', 'xlsx')),
]
GOALS = {
    'ko': 'F01부터 F10까지 각 항목의 값을 정확히 나열하고, 가능한 경우 구간 출처 ID를 대괄호로 표기하세요. 없는 정보는 추측하지 마세요. NOT-A-FACT 항목은 제외하세요.',
    'en': 'List the exact values of F01 through F10, with bracketed excerpt IDs when possible. Do not invent missing values. Exclude NOT-A-FACT.',
    'ja': 'F01からF10までの値を正確に列挙し、可能なら角括弧付きの区間IDを示してください。不明な値を推測せず、NOT-A-FACTを除外してください。',
    'zh-Hans': '准确列出F01至F10的值，尽可能标注方括号来源片段ID。不得猜测缺失值，排除NOT-A-FACT。',
    'zh-Hant': '準確列出F01至F10的值，盡可能標註方括號來源片段ID。不得猜測缺失值，排除NOT-A-FACT。',
}
OFFICE = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
PACKAGE = 'http://schemas.openxmlformats.org/package/2006/relationships'
WORD = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
SHEET = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
CT = 'http://schemas.openxmlformats.org/package/2006/content-types'


def json_bytes(data):
    return (json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode('utf-8')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def zip_bytes(parts):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_STORED) as z:
        for name, data in sorted(parts.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.create_system = 0
            info.external_attr = 0x20
            z.writestr(info, data.encode('utf-8') if isinstance(data, str) else data)
    return out.getvalue()


def relationships(entries):
    return f'<Relationships xmlns="{PACKAGE}">' + ''.join(
        f'<Relationship Id="rId{i}" Type="{OFFICE}/{kind}" Target="{target}"/>'
        for i, kind, target in entries) + '</Relationships>'


def content_types(entries):
    return f'<Types xmlns="{CT}"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>' + ''.join(
        f'<Override PartName="/{part}" ContentType="application/vnd.openxmlformats-officedocument.{kind}+xml"/>'
        for part, kind in entries) + '</Types>'


def docx(rows):
    body = ''.join(f'<w:p><w:r><w:t>{escape(k + ": " + v)}</w:t></w:r></w:p>' for k, v in rows)
    return zip_bytes({
        '[Content_Types].xml': content_types([('word/document.xml', 'wordprocessingml.document.main')]),
        '_rels/.rels': relationships([(1, 'officeDocument', 'word/document.xml')]),
        'word/document.xml': f'<w:document xmlns:w="{WORD}"><w:body>{body}<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>',
    })


def xlsx(rows):
    cells = ''.join(f'<row r="{i}"><c r="A{i}" t="inlineStr"><is><t>{escape(k)}</t></is></c><c r="B{i}" t="inlineStr"><is><t>{escape(v)}</t></is></c></row>' for i, (k, v) in enumerate(rows, 1))
    return zip_bytes({
        '[Content_Types].xml': content_types([('xl/workbook.xml', 'spreadsheetml.sheet.main'), ('xl/worksheets/sheet1.xml', 'spreadsheetml.worksheet')]),
        '_rels/.rels': relationships([(1, 'officeDocument', 'xl/workbook.xml')]),
        'xl/workbook.xml': f'<workbook xmlns="{SHEET}" xmlns:r="{OFFICE}"><sheets><sheet name="Facts" sheetId="1" r:id="rId1"/></sheets></workbook>',
        'xl/_rels/workbook.xml.rels': relationships([(1, 'worksheet', 'worksheets/sheet1.xml')]),
        'xl/worksheets/sheet1.xml': f'<worksheet xmlns="{SHEET}"><cols><col min="1" max="1" width="20" customWidth="1"/><col min="2" max="2" width="30" customWidth="1"/></cols><sheetData>{cells}</sheetData></worksheet>',
    })


def pdf(rows):
    lines = [k + ': ' + v for k, v in rows]
    if any(not s.isascii() or any(c in s for c in '()\\') for s in lines):
        raise ValueError('PDF fixture supports safe ASCII only')
    stream = ('BT /F1 12 Tf 54 740 Td 20 TL\n' + '\n'.join(f'({s}) Tj T*' for s in lines) + '\nET\n').encode('ascii')
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>', b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>', b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', b'<< /Length ' + str(len(stream)).encode('ascii') + b' >>\nstream\n' + stream + b'endstream']
    out = bytearray(b'%PDF-1.4\n'); offsets = [0]
    for i, obj in enumerate(objects, 1):
        offsets.append(len(out)); out.extend(f'{i} 0 obj\n'.encode('ascii') + obj + b'\nendobj\n')
    xref = len(out)
    out.extend(f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets[1:]: out.extend(f'{offset:010d} 00000 n \n'.encode('ascii'))
    out.extend(f'trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode('ascii'))
    return bytes(out)


def file_bytes(ext, rows):
    if ext == 'txt': return ''.join(f'{k}: {v}\n' for k, v in rows).encode('utf-8')
    if ext == 'csv': return ''.join(f'{k},{v}\n' for k, v in rows).encode('utf-8')
    return {'docx': docx, 'xlsx': xlsx, 'pdf': pdf}[ext](rows)


def build(candidate):
    if len(candidate) != 40 or any(c not in '0123456789abcdef' for c in candidate): raise ValueError('Expected exact 40-character candidate SHA')
    parts = {}; cases = []
    for n, (locale, formats) in enumerate(CASES, 1):
        case = f'T{n:02d}'
        values = [f'CASE-{case}', '2031-04-15', f'ALPHA-{n:02d}', f'BETA-{n:02d}', '137', '284', '421', '7.5%', 'REVIEW-PENDING', 'SOURCE-SYNTHETIC']
        rows = [(f'F{i:02d}', value) for i, value in enumerate(values, 1)]
        groups = [rows] if len(formats) == 1 else [rows[:4], rows[4:7]+[('NOT-A-FACT','CASE-OTHER')], rows[7:]]
        files = []
        for j, (ext, group) in enumerate(zip(formats, groups), 1):
            name = f'inputs/{case}/D{j}.{ext}'; data = file_bytes(ext, group); parts[name] = data
            files.append({'path':name,'sha256':sha(data),'bytes':len(data)})
        cases.append({'id':case,'locale':locale,'goal':GOALS[locale],'files':files,'expected':dict(rows)})
    parts['fault/F5.unsupported'] = b'Synthetic unsupported file; no personal data.\n'
    manifest = {'schema':1,'candidate_commit':candidate,'cases':cases,'unsupported':{'path':'fault/F5.unsupported','sha256':sha(parts['fault/F5.unsupported'])}}
    parts['manifest.json'] = json_bytes(manifest)
    tasks = {c['id']:{'status':'NOT_RUN','facts':{k:'NOT_RUN' for k in c['expected']},'synthesis_completed':None,'unsupported_claim':None,'false_whole_file_claim':None,'unflagged_unknown_id':None,'report':None,'map_requests':None,'total_requests':None,'elapsed_ms':None,'network_evidence':None} for c in cases}
    parts['evidence-template.json'] = json_bytes({'schema':1,'candidate_commit':candidate,'manifest_sha256':sha(parts['manifest.json']),'apk_sha256':None,'device':None,'os':None,'model_tag_quantization':None,'tasks':tasks,'faults':{f'F{i}':{'status':'NOT_RUN','evidence':None} for i in range(1,6)},'locale_smoke':{l:{'status':'NOT_RUN','evidence':None} for l in GOALS},'status':'BLOCKED'})
    parts['GOALS.txt'] = ('\n\n'.join(c['id']+' / '+c['locale']+'\n'+c['goal'] for c in cases)+'\n').encode('utf-8')
    parts['README.txt'] = ('Stage 12-B synthetic test pack / NOT_RUN\n\nCandidate: '+candidate+'\n1. Use inputs/T01 through T10 in order, select the locale and files, and paste the matching goal from GOALS.txt.\n2. Review extracted text. Keep the exact model and APK fixed; PDF requires Android 15+.\n3. Compare report with manifest.json expected values; do not attach manifest.json or evidence-template.json to the app.\n4. Save redacted reports, fault tests and independent timing/network evidence in a private folder. Complete the evidence template manually.\n5. Do not mark missing tests PASS. No release or progression is authorized by this pack.\n').encode('utf-8')
    return parts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    data = zip_bytes(build(args.candidate))
    with args.out.open('xb') as f: f.write(data)
    print('CREATED', args.out, 'SHA256', sha(data))


if __name__ == '__main__': main()
