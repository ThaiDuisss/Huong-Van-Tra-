"""Read-only return/Connect audit. Never starts services or changes Connect state.

Config output is explicitly allowlisted; tokens/credentials and raw HTTP errors
are never retained. Docker/DB access must be audited separately on their host.
"""
import argparse
import concurrent.futures
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--connect-url', default='http://100.99.2.30:8083')
    parser.add_argument('--api-url', default='http://localhost:8080/api')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    os.environ.update(SPRING_FLYWAY_ENABLED='false', SPRING_SQL_INIT_MODE='never',
                      SPRING_JPA_HIBERNATE_DDL_AUTO='none', STORAGE_INITIALIZATION_ENABLED='false')
    token_file = root / '.local/runtime-audit-token.txt'
    token = token_file.read_text(encoding='utf-8-sig').strip() if token_file.exists() else ''
    token = re.sub(r'^Bearer\s+', '', token, flags=re.I)

    def get(url, authenticated=False):
        headers = {'Accept': 'application/json', 'X-Client-Type': 'POS_WEB'}
        if authenticated and token:
            headers['Authorization'] = 'Bearer ' + token
        evidence = {'method': 'GET', 'url': url}
        try:
            opener = urllib.request.build_opener(NoRedirect)
            with opener.open(urllib.request.Request(url, headers=headers), timeout=8) as response:
                value = json.loads(response.read(4 * 1024 * 1024))
                evidence['http_status'] = response.status
                return evidence, value
        except urllib.error.HTTPError as error:
            evidence['http_status'] = error.code
        except Exception as error:
            evidence['error_type'] = type(error).__name__
        return evidence, None

    result = {'observed_at': datetime.now(timezone.utc).isoformat(),
              'mutations_executed': False, 'services_started': [], 'connectors': []}
    result['connect_api'], value = get(args.connect_url.rstrip('/') + '/')
    if isinstance(value, dict):
        result['connect_api']['runtime'] = {k: value[k] for k in ('version', 'commit', 'kafka_cluster_id') if k in value}
    evidence, names = get(args.connect_url.rstrip('/') + '/connectors')
    result['connector_list'] = evidence
    result['connector_list']['names'] = names if isinstance(names, list) else []
    allowed = {'connector.class', 'tasks.max', 'database.hostname', 'database.port', 'database.server.id',
               'database.include.list', 'table.include.list', 'topic.prefix', 'snapshot.mode',
               'schema.history.internal.kafka.bootstrap.servers', 'schema.history.internal.kafka.topic',
               'transforms', 'transforms.outbox.route.by.field', 'transforms.outbox.route.topic.replacement'}

    def connector(name):
        base = args.connect_url.rstrip('/') + '/connectors/' + name
        status_evidence, status = get(base + '/status')
        config_evidence, config = get(base + '/config')
        offsets_evidence, offsets = get(base + '/offsets')
        row = {'name': name, 'status_request': status_evidence, 'config_request': config_evidence}
        row['offsets_request'] = offsets_evidence
        if isinstance(offsets, dict):
            row['source_offsets'] = [
                {'partition': {k: v for k, v in item.get('partition', {}).items() if k == 'server'},
                 'offset': {k: v for k, v in item.get('offset', {}).items()
                            if k in {'file', 'pos', 'row', 'ts_sec', 'snapshot', 'snapshot_completed', 'gtids'}}}
                for item in offsets.get('offsets', [])]
        if isinstance(status, dict):
            # Stack traces may include connection strings; retain only error class/host clues.
            row['connector'] = {k: status.get('connector', {}).get(k) for k in ('state', 'worker_id')}
            row['tasks'] = []
            for task in status.get('tasks', []):
                trace = task.get('trace', '')
                row['tasks'].append({k: task.get(k) for k in ('id', 'state', 'worker_id')} | {
                    'unknown_host_mysql': bool(re.search(r'UnknownHostException:\s*mysql\b', trace)),
                    'exception_classes': sorted(set(re.findall(r'(?:[a-zA-Z_]+\.)+[A-Za-z]*(?:Exception|Error)', trace)))})
            trace = status.get('connector', {}).get('trace', '')
            row['connector']['unknown_host_mysql'] = bool(re.search(r'UnknownHostException:\s*mysql\b', trace))
            row['connector']['exception_classes'] = sorted(set(re.findall(r'(?:[a-zA-Z_]+\.)+[A-Za-z]*(?:Exception|Error)', trace)))
        if isinstance(config, dict):
            row['config'] = {k: v for k, v in config.items() if k in allowed}
        return row

    targets = sorted(set(result['connector_list']['names']) | {'order-outbox-connector', 'inventory-outbox-connector'})
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        result['connectors'] = list(pool.map(connector, targets))
    return_id = 'a189202e-5a97-429d-9685-32c35bf80baf'
    command_id = '8aea3781-0886-4fe0-a57a-2ec5187f30ba'
    endpoints = {
        'command': f'/orders/returns/{return_id}/inventory-dispositions/{command_id}',
        'return': f'/orders/returns/{return_id}',
        'inspections': '/inventory/customer-return-inspections?returnCode=RET20261104194138642&page=0&size=100',
        'qc_summary': f'/inventory/customer-returns/{return_id}/qc-summary',
    }
    fields = {'id', 'commandId', 'returnId', 'returnCode', 'orderId', 'orderCode', 'status', 'createdAt',
              'processedAt', 'failedAt', 'failureCode', 'inspectionId', 'physicalDone', 'requiredQuantity',
              'processedQuantity', 'remainingQuantity', 'inventoryProcessingStatus', 'isPendingDisposition',
              'hasFailedDisposition', 'qcStatus', 'remainingQcQuantity', 'postedQcQuantity', 'items', 'lines',
              'quantity', 'result', 'returnItemId', 'postedQuantity', 'inspectionStatus', 'totalElements', 'content'}
    def sanitize(value):
        if isinstance(value, dict):
            return {k: sanitize(v) for k, v in value.items() if k in fields}
        if isinstance(value, list):
            return [sanitize(v) for v in value]
        return value
    result['business'] = {}
    for name, path in endpoints.items():
        evidence, value = get(args.api_url.rstrip('/') + path, True)
        if isinstance(value, dict):
            evidence['data'] = sanitize(value.get('data', value))
        result['business'][name] = evidence
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
    observed_stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    args.output.with_name(args.output.stem + '-' + observed_stamp + args.output.suffix).write_text(
        json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=True))


if __name__ == '__main__':
    main()
