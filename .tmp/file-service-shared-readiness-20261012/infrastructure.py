"""Read-only infrastructure probes. No shared backup, restore, DDL or storage write."""
import hashlib
import hmac
import json
import socket
import sys
from datetime import datetime, timezone
from urllib.parse import urlsplit, quote
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import xml.etree.ElementTree as ET
import preflight as p


def network(host, port):
    try:
        addresses = sorted({x[4][0] for x in socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)})
        with socket.create_connection((host, port), timeout=8):
            pass
        return {'host': host, 'port': port, 'resolvedAddresses': addresses, 'tcp': 'PASS'}
    except Exception as e:
        return {'host': host, 'port': port, 'tcp': 'FAIL', 'errorType': type(e).__name__}


def signed_get(endpoint, path='/', policy=False):
    # AWS Signature V4; signing material remains in this process only.
    now = datetime.now(timezone.utc)
    amzdate, day = now.strftime('%Y%m%dT%H%M%SZ'), now.strftime('%Y%m%d')
    payload = hashlib.sha256(b'').hexdigest()
    signed = 'host;x-amz-content-sha256;x-amz-date'
    headers = f'host:{endpoint.netloc}\nx-amz-content-sha256:{payload}\nx-amz-date:{amzdate}\n'
    uri, query = quote(path, safe='/~'), 'policy=' if policy else ''
    canonical = '\n'.join(['GET', uri, query, headers, signed, payload])
    scope = f'{day}/us-east-1/s3/aws4_request'
    signing_input = '\n'.join(['AWS4-HMAC-SHA256', amzdate, scope, hashlib.sha256(canonical.encode()).hexdigest()])
    key = ('AWS4' + p.VALUES['MINIO_SECRET_KEY']).encode()
    for value in (day, 'us-east-1', 's3', 'aws4_request'):
        key = hmac.new(key, value.encode(), hashlib.sha256).digest()
    signature = hmac.new(key, signing_input.encode(), hashlib.sha256).hexdigest()
    authorization = f"AWS4-HMAC-SHA256 Credential={p.VALUES['MINIO_ACCESS_KEY']}/{scope}, SignedHeaders={signed}, Signature={signature}"
    url = endpoint.scheme + '://' + endpoint.netloc + uri + ('?' + query if query else '')
    req = Request(url, headers={'x-amz-date': amzdate, 'x-amz-content-sha256': payload, 'Authorization': authorization})
    try:
        with urlopen(req, timeout=12) as response:
            return response.status, response.read(), None
    except HTTPError as e:
        body = e.read()
        try:
            code = ET.fromstring(body).findtext('Code')
        except ET.ParseError:
            code = 'HTTPError'
        return e.code, body, code


def minio():
    ep = urlsplit(p.VALUES['MINIO_ENDPOINT'])
    assert ep.scheme in ('http', 'https') and ep.hostname and not ep.username and not ep.password
    assert ep.path in ('', '/') and not ep.query and not ep.fragment, 'Unsupported endpoint shape; do not guess'
    result = {'source': 'services/File-Service/file.env -> application.yml', 'endpoint': ep.scheme + '://' + ep.netloc,
              'network': network(ep.hostname, ep.port or (443 if ep.scheme == 'https' else 80))}
    assert result['network']['tcp'] == 'PASS', 'MinIO TCP unreachable'
    with urlopen(result['endpoint'] + '/minio/health/live', timeout=10) as response:
        result['healthHttpStatus'] = response.status
    status, body, code = signed_get(ep)
    result['authentication'] = {'httpStatus': status, 'errorCode': code}
    assert status == 200, 'Authenticated MinIO bucket listing failed'
    tree = ET.fromstring(body)
    names = {node.text for node in tree.findall('.//{*}Bucket/{*}Name')}
    buckets = {}
    for bucket in ('hvt-file-public', 'hvt-file-private'):
        entry = {'exists': bucket in names}
        assert entry['exists'], 'Required bucket missing: ' + bucket
        status, body, code = signed_get(ep, '/' + bucket, policy=True)
        entry.update({'policyHttpStatus': status, 'errorCode': code})
        if status == 200:
            policy = json.loads(body)
            statements = policy.get('Statement', [])
            if isinstance(statements, dict):
                statements = [statements]
            anonymous = []
            for stmt in statements:
                principal = stmt.get('Principal', {})
                aws = principal.get('AWS') if isinstance(principal, dict) else principal
                allows_all = aws == '*' or isinstance(aws, list) and '*' in aws
                if stmt.get('Effect') == 'Allow' and allows_all:
                    actions = stmt.get('Action', [])
                    anonymous.append({'actions': [actions] if isinstance(actions, str) else actions,
                                      'conditional': bool(stmt.get('Condition'))})
            entry['policySha256'] = hashlib.sha256(body).hexdigest()
            entry['anonymousAllowStatements'] = anonymous
        elif status == 404 and code in ('NoSuchBucketPolicy', 'NoSuchPolicy'):
            entry['anonymousAllowStatements'] = []
            entry['policySha256'] = None
        else:
            raise RuntimeError('Bucket policy cannot be verified: ' + bucket + ' HTTP ' + str(status))
        buckets[bucket] = entry
    result['buckets'] = buckets
    return result


def run():
    original_hash, parsed = p.load()
    result = {'auditedAtUtc': datetime.now(timezone.utc).isoformat(), 'mysqlNetwork': network(parsed.hostname, parsed.port or 3306)}
    assert result['mysqlNetwork']['tcp'] == 'PASS'
    # MySQL success must be established before any MinIO probe.
    with p.connect(parsed) as con:
        identity = p.query(con, 'SELECT DATABASE() AS db, VERSION() AS version')[0]
        assert identity['db'] == 'file_db' and identity['version'].startswith('8.4.')
        result['backupServerVariables'] = p.optional(con, "SHOW VARIABLES WHERE Variable_name IN ('gtid_mode','log_bin','binlog_expire_logs_seconds','event_scheduler','datadir')")
        result['objects'] = {
            'tables': p.query(con, "SELECT TABLE_NAME,TABLE_TYPE,ENGINE,DATA_LENGTH,INDEX_LENGTH FROM information_schema.TABLES WHERE TABLE_SCHEMA='file_db' ORDER BY TABLE_NAME"),
            'routines': p.query(con, "SELECT ROUTINE_TYPE,COUNT(*) AS count FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='file_db' GROUP BY ROUTINE_TYPE"),
            'events': p.query(con, "SELECT STATUS,COUNT(*) AS count FROM information_schema.EVENTS WHERE EVENT_SCHEMA='file_db' GROUP BY STATUS"),
            'triggers': p.query(con, "SELECT COUNT(*) AS count FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='file_db'"),
            'binlogs': p.optional(con, 'SHOW BINARY LOGS'),
            'invalidBooleanFlags': p.query(con, 'SELECT COUNT(*) AS count FROM file_references WHERE is_deleted NOT IN (0,1) OR is_deleted IS NULL'),
            'inboundForeignKeys': p.query(con, "SELECT TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA='file_db' AND REFERENCED_TABLE_NAME IN ('files','file_references')")}
    result['minio'] = minio()
    result['deploymentNetwork'] = {'configuredLocalEureka': network('127.0.0.1', 8761),
                                   'configuredDefaultKafka': network('100.93.252.63', 19092),
                                   'localFileServicePort': network('127.0.0.1', 8101)}
    result['envUnchanged'] = hashlib.sha256(p.ENV_FILE.read_bytes()).hexdigest() == original_hash
    p.save('infrastructure', result)
    p.emit(result)


if __name__ == '__main__':
    try:
        run()
    except Exception as exc:
        p.emit({'failure': p.redact(str(exc)), 'type': type(exc).__name__})
        sys.exit(1)
