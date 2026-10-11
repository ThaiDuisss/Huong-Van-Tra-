"""Offline comparison of sanitized shared metadata with isolated verification."""
import difflib
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
live = json.loads((HERE / 'live-preflight.json').read_text(encoding='utf-8'))
test = json.loads((ROOT / '.tmp/file-service-isolated/summary.json').read_text(encoding='utf-8'))

def normalized(sql, strip_pending=False):
    lines=sql.splitlines()
    if strip_pending:
        lines=[line for line in lines if not any(token in line for token in
            ('`ai_report_ref_id`','`ai_report_file_id`','`uq_ai_report_original_report`',
             '`uq_ai_report_original_file`','CONSTRAINT `fk_file_references_file`'))]
    text='\n'.join(lines)
    text=re.sub(r',\s*\)', '\n)', text)
    return '\n'.join(line.strip() for line in text.splitlines())

results={}
for table in ('files','file_references','business_sequences'):
    shared=live['showCreate'][table][0]['Create Table']
    tested=test['fullFreshTables'][table][0][1]
    a=normalized(shared)
    b=normalized(tested,strip_pending=(table=='file_references'))
    results[table]={'matchesAfterExcludingPendingEffects':a==b,
        'definitionDiff':list(difflib.unified_diff(b.splitlines(),a.splitlines(),fromfile='isolated',tofile='shared',lineterm=''))}
assert results['files']['matchesAfterExcludingPendingEffects']
assert results['file_references']['matchesAfterExcludingPendingEffects']
cols={(c['TABLE_NAME'],c['COLUMN_NAME']):c for c in live['columns']}
assert all(cols[key]['COLUMN_TYPE']=='binary(16)' and cols[key]['IS_NULLABLE']=='NO'
    and cols[key]['GENERATION_EXPRESSION']=='' for key in (('files','id'),('file_references','file_id'),('file_references','ref_id')))
assert all(cols[('file_references',key)]['COLUMN_TYPE']=='varchar(50)'
    and cols[('file_references',key)]['COLLATION_NAME']=='utf8mb4_0900_ai_ci' for key in ('ref_type','usage_type'))
assert cols[('file_references','is_deleted')]['COLUMN_TYPE']=='tinyint(1)' and cols[('file_references','is_deleted')]['IS_NULLABLE']=='NO'
assert not any(live[k] for k in ('partitionedTables','foreignKeys','columnCollisions','indexCollisions','constraintNameCollisions','helperCollision'))
assert live['orphans']==live['duplicateFiles']==live['duplicateReports']==0
assert live['fingerprintsUnchanged'] and live['envBytesUnchanged'] and live['sourceBytesUnchanged']
results['targetedMigrationPreconditions']='PASS'
results['businessSequencesCollationDifference']='Shared utf8mb4_unicode_ci vs isolated utf8mb4_0900_ai_ci; V6 inherits database defaults. Neither pending migration changes this table.'
(HERE/'schema-comparison.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))
