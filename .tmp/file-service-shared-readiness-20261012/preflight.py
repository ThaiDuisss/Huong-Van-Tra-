"""Read-only File-Service shared preflight. Only SELECT/SHOW and session safety SQL."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import urlsplit, parse_qs
import zlib

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / '.tmp/file-service-isolated/pythonlib'))
import pymysql

ENV_FILE = ROOT / 'services/File-Service/file.env'
SOURCE = Path('E:/HVTra-integration/20261012-002258/worktree/services/File-Service/src/main/resources/migration')
EXPECTED = {'1':1563888655,'2':1876724228,'3':-1382391432,'4':1408072548,'5':-93814498,'6':-478525518}
PENDING = {'20261010221221':('V20261010221221__unique_ai_report_original_binding.sql',-996233198),
           '20261011203800':('V20261011203800__restore_file_references_foreign_key.sql',1229623600)}
VALUES = {}

def emit(value):
    print(json.dumps(value, default=str), flush=True)

def redact(text):
    for key, value in VALUES.items():
        if key in ('SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','MINIO_ACCESS_KEY','MINIO_SECRET_KEY'):
            text = text.replace(value, '[REDACTED]')
    return text

def save(name, value):
    content = json.dumps(value, indent=2, default=str)
    (HERE / (name + '.json')).write_text(redact(content), encoding='utf-8')

def load():
    before = hashlib.sha256(ENV_FILE.read_bytes()).hexdigest()
    for line in ENV_FILE.read_text(encoding='utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('export '):
            line = line[7:]
        key, value = line.split('=',1)
        value = value.strip()
        if len(value)>=2 and value[0]==value[-1] and value[0] in ('"',"'"):
            value=value[1:-1]
        VALUES[key.strip()]=value
    required=('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
              'MINIO_ENDPOINT','MINIO_ACCESS_KEY','MINIO_SECRET_KEY')
    assert all(VALUES.get(k) for k in required), 'Required environment value missing'
    os.environ.update({k:VALUES[k] for k in required})
    assert VALUES['SPRING_DATASOURCE_URL'].startswith('jdbc:mysql://')
    parsed = urlsplit(VALUES['SPRING_DATASOURCE_URL'][5:])
    assert parsed.hostname=='100.93.252.63' and (parsed.port or 3306)==3306
    assert parsed.path=='/file_db' and not parsed.username and not parsed.password
    return before, parsed

def connect(parsed):
    return pymysql.connect(host=parsed.hostname,port=parsed.port or 3306,database='file_db',
        user=VALUES['SPRING_DATASOURCE_USERNAME'],password=VALUES['SPRING_DATASOURCE_PASSWORD'],
        charset='utf8mb4',autocommit=True,connect_timeout=8,read_timeout=12,write_timeout=12,
        init_command='SET SESSION TRANSACTION READ ONLY',cursorclass=pymysql.cursors.DictCursor)

def query(con, sql, args=None):
    assert re.match(r'^\s*(SELECT|SHOW)\b',sql,re.I), 'Non-read-only statement rejected'
    with con.cursor() as cur:
        cur.execute(sql,args)
        return cur.fetchall()

def optional(con, sql, args=None):
    try:
        return {'status':'READABLE','rows':query(con,sql,args)}
    except pymysql.MySQLError as exc:
        return {'status':'UNVERIFIED','mysqlErrorCode':exc.args[0]}

def fingerprint(con):
    result={}
    for table in ('files','file_references','business_sequences','flyway_schema_history'):
        rows=query(con,f'SELECT * FROM `{table}` ORDER BY 1')
        encoded=json.dumps(rows,sort_keys=True,default=lambda x:x.hex() if isinstance(x,bytes) else str(x)).encode()
        result[table]={'count':len(rows),'sha256':hashlib.sha256(encoded).hexdigest()}
    return result

def source_integrity():
    result={}
    for p in SOURCE.glob('V*.sql'):
        raw=p.read_bytes()
        crc=zlib.crc32(''.join(raw.decode('utf-8-sig').splitlines()).encode())
        if crc>=2**31: crc-=2**32
        result[p.name]={'checksum':crc,'sha256':hashlib.sha256(raw).hexdigest()}
    return result

def monitoring(con):
    return {
        'innodbTransactions':optional(con,"""SELECT COUNT(*) AS active_transactions,
          COALESCE(SUM(trx_rows_modified>0),0) AS transactions_with_modified_rows,
          COALESCE(MAX(TIMESTAMPDIFF(SECOND,trx_started,NOW())),0) AS oldest_seconds
          FROM information_schema.INNODB_TRX WHERE trx_mysql_thread_id<>CONNECTION_ID()"""),
        'visibleFileDbSessions':optional(con,"""SELECT COMMAND,COUNT(*) AS session_count,MAX(TIME) AS oldest_seconds
          FROM information_schema.PROCESSLIST WHERE DB='file_db' AND ID<>CONNECTION_ID()
          GROUP BY COMMAND"""),
        'fileDbMetadataLocks':optional(con,"""SELECT l.OBJECT_NAME,l.LOCK_TYPE,l.LOCK_STATUS,COUNT(*) AS locks_count
          FROM performance_schema.metadata_locks l JOIN performance_schema.threads t ON t.THREAD_ID=l.OWNER_THREAD_ID
          WHERE l.OBJECT_SCHEMA='file_db' AND t.PROCESSLIST_ID<>CONNECTION_ID()
          GROUP BY l.OBJECT_NAME,l.LOCK_TYPE,l.LOCK_STATUS"""),
        'fileDbWriteCounters':optional(con,"""SELECT OBJECT_NAME,COUNT_WRITE,COUNT_INSERT,COUNT_UPDATE,COUNT_DELETE
          FROM performance_schema.table_io_waits_summary_by_table WHERE OBJECT_SCHEMA='file_db'
          AND OBJECT_NAME IN ('files','file_references','business_sequences','flyway_schema_history')"""),
        'instrumentation':optional(con,"""SELECT NAME,ENABLED,TIMED FROM performance_schema.setup_instruments
          WHERE NAME IN ('wait/lock/metadata/sql/mdl','wait/io/table/sql/handler')""")}

def privileges(con):
    raw=query(con,'SHOW GRANTS')
    grants=[]
    for row in raw:
        text=next(iter(row.values()))
        m=re.match(r'^GRANT (.+?) ON (.+?) TO ',text,re.I)
        if m:
            grants.append({'privileges':[s.strip() for s in m[1].split(',')],
                           'scope':m[2], 'grantOption':text.endswith('WITH GRANT OPTION')})
        else:
            grants.append({'roleOrOtherGrant':True})
    role=query(con,'SELECT CURRENT_ROLE() AS active_role')[0]['active_role']
    global_names=set()
    schema_names=set()
    table_names={}
    for g in grants:
        if 'scope' not in g: continue
        names=set(g['privileges'])
        if g['scope']=='*.*': global_names.update(names)
        elif g['scope'].replace('`','')=='file_db.*': schema_names.update(names)
        elif g['scope'].replace('`','').startswith('file_db.'):
            table_names[g['scope'].replace('`','').split('.',1)[1]]=names
    need=['SELECT','ALTER','REFERENCES','CREATE ROUTINE','EXECUTE','ALTER ROUTINE','CREATE','INSERT','INDEX']
    direct=global_names|schema_names
    declared={n:('GRANTED' if n in direct or 'ALL PRIVILEGES' in direct else 'NOT_CONFIRMED') for n in need}
    return {'grantsWithoutPrincipal':grants,'activeRoles':role!='NONE','requiredDirectSchemaPrivileges':declared,
            'tableSpecificPrivileges':{k:sorted(v) for k,v in table_names.items()},
            'processPrivilegeDeclared':'PROCESS' in global_names or 'ALL PRIVILEGES' in global_names,
            'writePrivilegesExecutionTested':False}

def flyway(command, pending=False):
    assert command in ('info','validate'), 'Only read-only Flyway commands permitted'
    env={k:v for k,v in os.environ.items() if not k.upper().startswith('FLYWAY_')}
    env.update({'FLYWAY_URL':VALUES['SPRING_DATASOURCE_URL'],'FLYWAY_USER':VALUES['SPRING_DATASOURCE_USERNAME'],
        'FLYWAY_PASSWORD':VALUES['SPRING_DATASOURCE_PASSWORD']})
    (HERE/'empty.conf').write_text('# Explicitly empty; env supplies legitimate File-Service target.\n',encoding='utf-8')
    args=['D:/Malpe/flyway-13.7.0/flyway.cmd','-configFiles='+str(HERE/'empty.conf'),
      '-locations=filesystem:'+SOURCE.as_posix(),'-schemas=file_db','-defaultSchema=file_db',
      '-createSchemas=false','-cleanDisabled=true','-baselineOnMigrate=false','-outOfOrder=false',
      '-validateOnMigrate=true','-connectRetries=0','-outputType=json',
      '-ignoreMigrationPatterns='+('*:pending' if pending else ''),
      '-initSql=SET SESSION TRANSACTION READ ONLY', command]
    p=subprocess.run(args,cwd=HERE,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=60)
    output=redact(p.stdout+p.stderr)
    at=output.find('{')
    assert at>=0, 'No Flyway JSON response: '+output
    result=json.JSONDecoder().raw_decode(output[at:])[0]
    name='flyway-'+command+('-ignore-pending' if pending else '-strict')
    save(name,{'processExitCode':p.returncode,**result})
    return result

def run():
    env_hash,parsed=load()
    initial_sources=source_integrity()
    verified=json.loads((Path('E:/HVTra-integration/20261012-002258/mysql-verification/result.json')).read_text(encoding='utf-8'))['sourceIntegrity']
    assert initial_sources==verified,'Migration bytes differ from isolated verification'
    result={'auditedAtUtc':datetime.now(timezone.utc).isoformat(),'target':{'host':parsed.hostname,'port':parsed.port or 3306,'database':'file_db'},
            'sourceIntegrity':initial_sources,'isolatedSourceBytesMatch':True}
    with connect(parsed) as con:
        result['identity']=query(con,"SELECT DATABASE() AS database_name,VERSION() AS mysql_version,@@hostname AS server_hostname,@@SESSION.transaction_read_only AS audit_session_read_only,@@SESSION.foreign_key_checks AS foreign_key_checks,@@SESSION.sql_mode AS sql_mode,@@SESSION.lock_wait_timeout AS lock_wait_timeout,@@SESSION.innodb_lock_wait_timeout AS innodb_lock_wait_timeout,@@GLOBAL.read_only AS server_read_only,@@GLOBAL.super_read_only AS server_super_read_only")[0]
        assert result['identity']['database_name']=='file_db' and result['identity']['audit_session_read_only']==1
        result['privileges']=privileges(con)
        # Short consistent, explicitly read-only snapshot. End it before monitoring.
        with con.cursor() as cur: cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        try:
            result['beforeRows']=fingerprint(con)
            result['history']=query(con,'SELECT version,description,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
            assert len(result['history'])==6 and all(row['version'] in EXPECTED and row['success']==1 and row['checksum']==EXPECTED[row['version']] for row in result['history'])
            assert len({row['version'] for row in result['history']})==6
            for row in result['history']:
                assert initial_sources[row['script']]['checksum']==row['checksum']
            result['tables']=query(con,"SELECT TABLE_NAME,TABLE_TYPE,ENGINE,TABLE_COLLATION,ROW_FORMAT,TABLE_ROWS,DATA_LENGTH,INDEX_LENGTH,DATA_FREE FROM information_schema.TABLES WHERE TABLE_SCHEMA='file_db' ORDER BY TABLE_NAME")
            result['columns']=query(con,"SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='file_db' AND TABLE_NAME IN ('files','file_references','business_sequences') ORDER BY TABLE_NAME,ORDINAL_POSITION")
            result['indexes']=query(con,"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,IS_VISIBLE,INDEX_TYPE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='file_db' AND TABLE_NAME IN ('files','file_references','business_sequences') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX")
            result['foreignKeys']=query(con,"""SELECT k.TABLE_NAME,k.CONSTRAINT_NAME,k.COLUMN_NAME,k.ORDINAL_POSITION,k.REFERENCED_TABLE_SCHEMA,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE
              FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
              ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.TABLE_NAME=r.TABLE_NAME AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME
              WHERE k.CONSTRAINT_SCHEMA='file_db' ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION""")
            result['partitionedTables']=query(con,"SELECT TABLE_NAME,COUNT(*) AS partitions FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA='file_db' AND TABLE_NAME IN ('files','file_references') AND PARTITION_NAME IS NOT NULL GROUP BY TABLE_NAME")
            result['showCreate']={t:query(con,f'SHOW CREATE TABLE `{t}`') for t in ('files','file_references','business_sequences','flyway_schema_history')}
            result['aiCounts']=query(con,"""SELECT COUNT(*) AS total_references,
              COALESCE(SUM(ref_type='AI_REPORT' AND usage_type='ORIGINAL' AND is_deleted=FALSE),0) AS active_ai_original,
              COALESCE(SUM(ref_type='AI_REPORT' AND usage_type='ORIGINAL' AND is_deleted=TRUE),0) AS deleted_ai_original,
              COALESCE(SUM(ref_type IS NULL OR usage_type IS NULL OR is_deleted IS NULL OR ref_id IS NULL OR file_id IS NULL),0) AS null_inputs,
              COALESCE(SUM(ref_id IS NOT NULL AND OCTET_LENGTH(ref_id)<>16 OR file_id IS NOT NULL AND OCTET_LENGTH(file_id)<>16),0) AS invalid_binary_lengths
              FROM file_references""")[0]
            result['duplicateFiles']=query(con,"SELECT COUNT(*) AS duplicate_groups FROM (SELECT file_id FROM file_references WHERE ref_type='AI_REPORT' AND usage_type='ORIGINAL' AND is_deleted=FALSE GROUP BY file_id HAVING COUNT(*)>1) d")[0]['duplicate_groups']
            result['duplicateReports']=query(con,"SELECT COUNT(*) AS duplicate_groups FROM (SELECT ref_id FROM file_references WHERE ref_type='AI_REPORT' AND usage_type='ORIGINAL' AND is_deleted=FALSE GROUP BY ref_id HAVING COUNT(*)>1) d")[0]['duplicate_groups']
            result['orphans']=query(con,'SELECT COUNT(*) AS orphan_count FROM file_references r LEFT JOIN files f ON f.id=r.file_id WHERE r.file_id IS NULL OR f.id IS NULL')[0]['orphan_count']
            result['columnCollisions']=query(con,"SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='file_db' AND TABLE_NAME='file_references' AND COLUMN_NAME IN ('ai_report_ref_id','ai_report_file_id')")
            result['indexCollisions']=query(con,"SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='file_db' AND TABLE_NAME='file_references' AND INDEX_NAME IN ('uq_ai_report_original_report','uq_ai_report_original_file')")
            result['constraintNameCollisions']=query(con,"SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='file_db' AND CONSTRAINT_NAME IN ('fk_file_references_file','uq_ai_report_original_report','uq_ai_report_original_file')")
            result['helperCollision']=query(con,"SELECT ROUTINE_NAME,ROUTINE_TYPE FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='file_db' AND ROUTINE_NAME='hvtra_restore_file_reference_fk'")
        finally:
            con.rollback()
        result['monitoringBefore']=monitoring(con)
        result['backupRelatedServerInfo']=optional(con,"SHOW VARIABLES WHERE Variable_name IN ('log_bin','binlog_expire_logs_seconds','automatic_sp_privileges','partial_revokes','performance_schema')")
        save('live-preflight',result)
        emit({'identity':result['identity'],'history':'V1-V6 checksum PASS','counts':{k:v['count'] for k,v in result['beforeRows'].items()},
              'duplicatesFile':result['duplicateFiles'],'duplicatesReport':result['duplicateReports'],'orphans':result['orphans'],
              'columnCollisions':len(result['columnCollisions']),'indexCollisions':len(result['indexCollisions']),
              'constraintCollisions':len(result['constraintNameCollisions']),'helperCollision':len(result['helperCollision']),
              'privileges':result['privileges'],'monitoring':result['monitoringBefore']})
    info=flyway('info')
    assert 'error' not in info
    pending={m['version']:m for m in info['migrations'] if m['state']=='Pending'}
    assert set(pending)==set(PENDING)
    assert len(info['migrations'])==8 and all(m['state'] in ('Success','Pending') for m in info['migrations'])
    for version,(script,checksum) in PENDING.items():
        assert initial_sources[script]['checksum']==checksum and pending[version]['filepath'].replace('\\','/').endswith('/'+script)
    validation=flyway('validate',pending=True)
    assert validation.get('validationSuccessful') and not validation.get('invalidMigrations')
    strict=flyway('validate')
    assert not strict.get('validationSuccessful')
    invalid=strict.get('invalidMigrations',[])
    assert len(invalid)==2 and {m['version'] for m in invalid}==set(PENDING)
    assert all(m['errorDetails']['errorCode']=='RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED' for m in invalid)
    time.sleep(3)
    with connect(parsed) as con:
        result['afterRows']=fingerprint(con)
        result['monitoringAfter']=monitoring(con)
        result['fingerprintsUnchanged']=result['afterRows']==result['beforeRows']
    result['flywayPending']=[{'version':v,'script':PENDING[v][0],'checksum':PENDING[v][1]} for v in sorted(PENDING)]
    result['pendingOnlyValidation']='PASS'
    result['strictValidation']='EXPECTED_FAIL_ONLY_TWO_PENDING'
    result['envBytesUnchanged']=hashlib.sha256(ENV_FILE.read_bytes()).hexdigest()==env_hash
    result['sourceBytesUnchanged']=source_integrity()==initial_sources
    save('live-preflight',result)
    emit({'pending':result['flywayPending'],'validateIgnorePending':'PASS','strictValidate':'EXPECTED_FAIL_ONLY_TWO_PENDING',
          'fingerprintsUnchanged':result['fingerprintsUnchanged'],'envBytesUnchanged':result['envBytesUnchanged'],
          'sourceBytesUnchanged':result['sourceBytesUnchanged'],'monitoringAfter':result['monitoringAfter']})

if __name__=='__main__':
    try:
        run()
    except Exception as exc:
        emit({'failure':redact(str(exc)),'type':type(exc).__name__})
        sys.exit(1)
