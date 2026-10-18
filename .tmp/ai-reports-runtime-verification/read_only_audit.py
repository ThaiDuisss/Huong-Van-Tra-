"""Runtime preflight: SELECT/SHOW only; no migration, upload or cleanup."""
import concurrent.futures
import datetime
import json
import os
from pathlib import Path
import re
import socket
import sys
import urllib.parse
import zlib

ROOT = Path(__file__).resolve().parents[2]
REC = ROOT / 'services/Recommendation-Service'
sys.path.insert(0, str(REC))
import httpx
import pymysql
from app.core.settings import Settings


def query(cursor, sql, args=None):
    if not sql.lstrip().upper().startswith(('SELECT ', 'SHOW ')):
        raise ValueError('Only SELECT/SHOW statements permitted')
    cursor.execute(sql, args)
    return cursor.fetchall()


def source_checksums(directory):
    # Flyway ChecksumCalculator: signed CRC32 of UTF-8 lines, excluding line
    # endings and a leading BOM. Confirmed against cached Flyway bytecode.
    result = []
    for path in sorted(directory.glob('V*.sql')):
        lines = path.read_text(encoding='utf-8-sig').splitlines()
        crc = zlib.crc32(''.join(lines).encode('utf-8'))
        result.append({'script': path.name, 'checksum': crc if crc < 2**31 else crc - 2**32})
    return result


def inspect_schema(cursor, schema):
    result = {'schema': schema}
    result['charsetCollation'] = query(cursor, 'SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=%s', (schema,))
    result['tables'] = query(cursor, 'SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME', (schema,))
    tables = {row['TABLE_NAME'] for row in result['tables']}
    quoted_schema = '`' + schema.replace('`', '``') + '`'
    result['showTables'] = query(cursor, f'SHOW TABLES FROM {quoted_schema}')
    if 'flyway_schema_history' in tables:
        result['history'] = query(cursor, f'SELECT installed_rank, version, description, script, success, checksum FROM {quoted_schema}.flyway_schema_history ORDER BY installed_rank')
    else:
        result['history'] = None
    for table in ('ai_reports', 'ai_report_imports', 'file_references', 'files', 'ingredients'):
        if table not in tables:
            continue
        result[table] = {'columns': query(cursor, 'SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION', (schema, table)),
                         'indexes': query(cursor, 'SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY INDEX_NAME, SEQ_IN_INDEX', (schema, table))}
        if table == 'file_references':
            target = f'{quoted_schema}.file_references'
            predicate = "ref_type='AI_REPORT' AND usage_type='ORIGINAL' AND is_deleted=FALSE"
            for col in ('file_id', 'ref_id'):
                result[table]['duplicate_' + col] = query(cursor, f'SELECT COUNT(*) AS duplicate_groups FROM (SELECT {col} FROM {target} WHERE {predicate} GROUP BY {col} HAVING COUNT(*)>1) AS duplicate_keys')
            result[table]['null_generated_inputs'] = query(cursor, f'SELECT COUNT(*) AS active_originals, COALESCE(SUM(file_id IS NULL),0) AS null_file_ids, COALESCE(SUM(ref_id IS NULL),0) AS null_report_ids FROM {target} WHERE {predicate}')
            result[table]['ddl'] = query(cursor, f'SHOW CREATE TABLE {target}')
    return result


def database_audit(env_file):
    settings = Settings(_env_file=REC / env_file)
    result = {'profile': env_file, 'host': settings.mysql_host, 'port': settings.mysql_port, 'configuredSchema': settings.mysql_database}
    try:
        with pymysql.connect(host=settings.mysql_host, port=settings.mysql_port, user=settings.mysql_user,
                password=settings.mysql_password.get_secret_value(), database=settings.mysql_database,
                connect_timeout=5, read_timeout=8, write_timeout=5,
                cursorclass=pymysql.cursors.DictCursor, autocommit=False) as connection:
            with connection.cursor() as cursor:
                cursor.execute('SET TRANSACTION READ ONLY')
                cursor.execute('START TRANSACTION')
                result['database'] = query(cursor, 'SELECT DATABASE() AS current_schema, VERSION() AS server_version')
                result['runtimeTarget'] = inspect_schema(cursor, settings.mysql_database)
                # Metadata discovery under this existing connection's grants only.
                result['fileSchemaCandidates'] = query(cursor, "SELECT TABLE_SCHEMA FROM information_schema.TABLES WHERE TABLE_NAME='file_references' AND TABLE_SCHEMA NOT IN ('mysql','sys','information_schema','performance_schema')")
                result['fileCandidateAudits'] = [inspect_schema(cursor, row['TABLE_SCHEMA']) for row in result['fileSchemaCandidates']]
                result['otherRecommendationSchemaVisible'] = query(cursor, "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('recommend_app','recommendation_db')")
            connection.rollback()
        result['status'] = 'reachable'
    except pymysql.MySQLError as error:
        # Keep exact diagnostic but remove any credentials/URL userinfo.
        message = str(error)
        for secret in (settings.mysql_password.get_secret_value(), settings.mysql_user):
            if secret:
                message = message.replace(secret, '[redacted]')
        result.update(status='unavailable', errorType=type(error).__name__, detail=message)
    return result


def tcp_probe(host, port):
    try:
        with socket.create_connection((host, port), timeout=4):
            return {'host': host, 'port': port, 'status': 'reachable'}
    except OSError as error:
        return {'host': host, 'port': port, 'status': 'unreachable', 'detail': str(error)}


def http_probe(name, url):
    result = {'component': name, 'url': url}
    try:
        with httpx.Client(timeout=5, follow_redirects=False, trust_env=False) as client:
            response = client.get(url)
            result.update(status='reachable', httpStatus=response.status_code)
            if response.status_code == 200 and '/actuator/health' in url:
                try:
                    result['health'] = response.json().get('status')
                except (ValueError, AttributeError):
                    pass
    except httpx.HTTPError as error:
        result.update(status='unreachable', detail=str(error))
    return result


def main():
    candidates = []
    for env_path in (ROOT / 'services').glob('*/*env*'):
        if not env_path.is_file():
            continue
        for line in env_path.read_text(encoding='utf-8-sig').splitlines():
            match = re.match(r'\s*(MINIO_ENDPOINT|MINIO_PUBLIC_ENDPOINT)\s*=\s*(.*?)\s*$', line)
            if match:
                endpoint = match[2].strip('"\'')
                parsed = urllib.parse.urlsplit(endpoint)
                if parsed.scheme in ('http','https') and parsed.hostname and not parsed.username and not parsed.password:
                    candidates.append({'source': str(env_path.relative_to(ROOT)), 'key': match[1], 'endpoint': endpoint})
    probes = [('Gateway', 'http://localhost:8080/actuator/health'),
              ('Recommendation', 'http://localhost:8090/health'),
              ('File-Service', 'http://localhost:8101/actuator/health'),
              ('Eureka', 'http://localhost:8761/actuator/health'),
              ('Keycloak', 'https://auth.hvtra.id.vn/realms/hvt/.well-known/openid-configuration')]
    for endpoint in sorted({row['endpoint'] for row in candidates}):
        probes.append(('MinIO candidate (not verified File-Service override)', endpoint.rstrip('/') + '/minio/health/live'))
    # Probe source/default storage origin, without guessing a remote endpoint.
    probes.append(('MinIO Recommendation default', 'http://localhost:9000/minio/health/live'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        http_results = list(pool.map(lambda item: http_probe(*item), probes))
    result = {'timestampUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'recommendationSourceChecksums': source_checksums(REC / 'flyway/sql'),
              'fileSourceChecksums': source_checksums(ROOT / 'services/File-Service/src/main/resources/migration'),
              'recommendationProfiles': [database_audit(name) for name in ('recommend.env', '.env')],
              'fileRuntimeEnvironment': {key: ('present' if os.getenv(key) else 'missing') for key in ('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','MINIO_ENDPOINT','MINIO_ACCESS_KEY','MINIO_SECRET_KEY')},
              'minioCandidates': candidates, 'http': http_results,
              'redisTcp': tcp_probe('100.93.252.63', 6379)}
    destination = Path(__file__).with_name('read-only-audit.json')
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2, default=str), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))


if __name__ == '__main__':
    main()
