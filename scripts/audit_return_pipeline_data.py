"""Read-only MySQL and Kafka evidence. Requires pymysql and confluent-kafka.

Credentials are read in memory from service env files. Only selected business
fields are retained. No service startup, migrations, Kafka group joins, offset
commits, topic creation or production. Kafka scans report their exact bounds.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import time
import urllib.parse
import uuid


ROOT = Path(__file__).resolve().parents[1]
RETURN_ID = 'a189202e-5a97-429d-9685-32c35bf80baf'
COMMAND_ID = '8aea3781-0886-4fe0-a57a-2ec5187f30ba'


def env_file(path):
    result = {}
    if path.exists():
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            match = re.match(r'^\s*(?:export\s+)?([A-Za-z_][\w]*)\s*=\s*(.*)\s*$', line)
            if match:
                result[match[1]] = match[2].strip().strip('\"\'')
    return result


def db_audit(service, candidates=()):
    import pymysql
    directory = ROOT / 'services' / (service + '-Service')
    env = env_file(directory / '.env')
    # IDE service-specific files are audited as an additional configuration source.
    specific = env_file(directory / (service.lower() + '.env'))
    env.update(specific)
    target = urllib.parse.urlsplit(env['SPRING_DATASOURCE_URL'].removeprefix('jdbc:'))
    row = {'service': service, 'host': target.hostname, 'port': target.port or 3306,
           'database': target.path.strip('/'), 'source': 'service-specific env overriding .env (not asserted as JVM runtime config)'}
    connection = pymysql.connect(host=target.hostname, port=target.port or 3306,
                                 user=env['SPRING_DATASOURCE_USERNAME'], password=env['SPRING_DATASOURCE_PASSWORD'],
                                 database=target.path.strip('/'), connect_timeout=6, read_timeout=8,
                                 cursorclass=pymysql.cursors.DictCursor, autocommit=False)
    try:
        with connection.cursor() as cursor:
            cursor.execute('SET SESSION TRANSACTION READ ONLY')
            connection.begin()
            def select(name, sql, params=()):
                try:
                    cursor.execute(sql, params)
                    row[name] = cursor.fetchall()
                except Exception as error:
                    row[name] = {'error_type': type(error).__name__,
                                 'error_code': error.args[0] if error.args and isinstance(error.args[0], int) else None}
            select('server', 'SELECT @@hostname AS hostname, @@version AS version, @@log_bin AS log_bin, '
                   '@@binlog_format AS binlog_format, @@binlog_row_image AS binlog_row_image')
            select('available_binlogs', 'SHOW BINARY LOGS')
            select('migration_history', 'SELECT version, script, checksum, success FROM flyway_schema_history '
                   'ORDER BY installed_rank DESC LIMIT 5')
            select('tables', 'SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() '
                   'AND (table_name LIKE %s OR table_name LIKE %s OR table_name LIKE %s)',
                   ('%outbox%', '%inbox%', '%return%'))
            if service == 'Order':
                select('commands', 'SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(command_id) AS command_id, '
                       'BIN_TO_UUID(return_id) AS return_id, status, created_at, processed_at, failed_at, failure_code, '
                       'recovery_attempt_count, not_found_count, BIN_TO_UUID(inspection_id) AS inspection_id '
                       'FROM order_return_inventory_commands WHERE return_id=UUID_TO_BIN(%s) AND is_deleted=0 '
                       'ORDER BY created_at', (RETURN_ID,))
                select('command_lines', 'SELECT BIN_TO_UUID(l.return_item_id) AS return_item_id, l.quantity, l.result, '
                       'BIN_TO_UUID(c.command_id) AS command_id FROM order_return_inventory_command_lines l '
                       'JOIN order_return_inventory_commands c ON c.id=l.inventory_command_id WHERE c.return_id=UUID_TO_BIN(%s) '
                       'AND l.is_deleted=0', (RETURN_ID,))
                select('unresolved_counts', 'SELECT status, count(*) AS commands FROM order_return_inventory_commands '
                       'WHERE is_deleted=0 GROUP BY status')
                select('failed_command_replay_candidates', 'SELECT BIN_TO_UUID(c.command_id) AS command_id, '
                       'BIN_TO_UUID(c.return_id) AS return_id, c.failure_code, c.created_at, '
                       'count(o.event_id) AS outbox_records FROM order_return_inventory_commands c '
                       'LEFT JOIN outbox_events o ON o.aggregate_id=BIN_TO_UUID(c.command_id) '
                       "AND o.event_type='RETURN_INVENTORY_DISPOSITION_REQUESTED' "
                       "WHERE c.status='FAILED' AND c.is_deleted=0 GROUP BY c.id ORDER BY c.created_at")
            else:
                select('inspections', 'SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(command_id) AS command_id, status, '
                       'created_at, posted_at FROM customer_return_inspections WHERE return_id=UUID_TO_BIN(%s) '
                       'AND is_deleted=0', (RETURN_ID,))
            select('matching_outbox', "SELECT event_id, topic, event_type, occurred_at, aggregate_id, "
                   "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.commandId')) AS command_id "
                   'FROM outbox_events WHERE payload LIKE %s OR aggregate_id=%s ORDER BY occurred_at',
                   ('%' + COMMAND_ID + '%', COMMAND_ID))
            select('outbox_return_topic_counts', 'SELECT topic, count(*) AS records, min(occurred_at) AS oldest, '
                   'max(occurred_at) AS newest FROM outbox_events WHERE topic LIKE %s GROUP BY topic',
                   ('%RETURN_INVENTORY_DISPOSITION%',))
            # Inspect metadata first because inbox schemas differ between services.
            select('inbox_columns', 'SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() '
                   'AND table_name=%s', ('inbox_events',))
            columns = {r['COLUMN_NAME'] if 'COLUMN_NAME' in r else r.get('column_name') for r in row.get('inbox_columns', [])}
            if 'command_id' in columns:
                select('matching_inbox', 'SELECT command_id, event_id, status, inspection_id, processed_at '
                       'FROM inbox_events WHERE command_id=%s', (COMMAND_ID,))
                ids = [candidate['command_id'] for candidate in candidates]
                return_ids = sorted({candidate['return_id'] for candidate in candidates})
                if ids:
                    placeholders = ','.join(['%s'] * len(ids))
                    select('failed_order_command_inboxes', 'SELECT command_id, event_id, status, inspection_id, '
                           'processed_at FROM inbox_events WHERE command_id IN (' + placeholders + ')', ids)
                if return_ids:
                    placeholders = ','.join(['%s'] * len(return_ids))
                    select('failed_order_return_inspections', 'SELECT BIN_TO_UUID(i.command_id) AS command_id, '
                           'BIN_TO_UUID(i.return_id) AS return_id, BIN_TO_UUID(i.id) AS inspection_id, i.status, '
                           'BIN_TO_UUID(l.return_item_id) AS return_item_id, l.quantity, l.posted '
                           'FROM customer_return_inspections i JOIN customer_return_inspection_lines l ON l.inspection_id=i.id '
                           'WHERE i.is_deleted=0 AND BIN_TO_UUID(i.return_id) IN (' + placeholders + ')', return_ids)
            elif 'payload_json' in columns:
                select('matching_ack_inbox', 'SELECT event_id, status, processed_at FROM inbox_events '
                       'WHERE payload_json LIKE %s', ('%' + COMMAND_ID + '%',))
    finally:
        connection.rollback()
        connection.close()
    return row


def kafka_audit(bootstrap, limit):
    from confluent_kafka import Consumer, TopicPartition, KafkaError
    from confluent_kafka.admin import AdminClient
    common = {'bootstrap.servers': bootstrap, 'client.id': 'return-pipeline-readonly-audit',
              'socket.timeout.ms': 6000, 'log_level': 0}
    admin = AdminClient(common)
    metadata = admin.list_topics(timeout=8)
    result = {'bootstrap': bootstrap, 'brokers': [{'host': b.host, 'port': b.port} for b in metadata.brokers.values()],
              'group_joined': False, 'offset_commits': 0, 'produced_messages': 0, 'topics': []}
    consumer = Consumer(common | {'group.id': 'readonly-unjoined-' + str(uuid.uuid4()),
                                  'enable.auto.commit': False, 'enable.auto.offset.store': False,
                                  'allow.auto.create.topics': False,
                                  'enable.partition.eof': True})
    try:
        for name, topic in metadata.topics.items():
            if 'RETURN_INVENTORY_DISPOSITION' not in name:
                continue
            for partition in topic.partitions:
                tp = TopicPartition(name, partition)
                low, high = consumer.get_watermark_offsets(tp, timeout=8, cached=False)
                start = max(low, high - limit)
                row = {'topic': name, 'partition': partition, 'low': low, 'high': high,
                       'scan_start': start, 'scanned': 0, 'matches': [], 'complete': False}
                result['topics'].append(row)
                if start == high:
                    row['complete'] = True
                    continue
                consumer.assign([TopicPartition(name, partition, start)])
                deadline = time.monotonic() + 20
                while time.monotonic() < deadline:
                    message = consumer.poll(1)
                    if message is None:
                        continue
                    if message.error():
                        if message.error().code() == KafkaError._PARTITION_EOF:
                            row['complete'] = True
                        else:
                            row['error_code'] = message.error().code()
                        break
                    if message.offset() >= high:
                        row['complete'] = True
                        break
                    row['scanned'] += 1
                    value = (message.value() or b'').decode('utf-8', errors='replace')
                    if COMMAND_ID in value or RETURN_ID in value:
                        # Exact transport evidence only, no customer data or raw payload.
                        row['matches'].append({'offset': message.offset(), 'timestamp': message.timestamp()[1],
                                               'matches_command': COMMAND_ID in value, 'matches_return': RETURN_ID in value})
                    if message.offset() + 1 >= high:
                        row['complete'] = True
                        break
    finally:
        consumer.close()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--kafka-bootstrap', default='100.99.2.30:19092')
    parser.add_argument('--scan-limit', type=int, default=5000)
    args = parser.parse_args()
    os.environ.update(SPRING_FLYWAY_ENABLED='false', SPRING_SQL_INIT_MODE='never',
                      SPRING_JPA_HIBERNATE_DDL_AUTO='none', STORAGE_INITIALIZATION_ENABLED='false')
    result = {'observed_at': datetime.now(timezone.utc).isoformat(), 'mutations_executed': False, 'databases': []}
    candidates = []
    for service in ('Order', 'Inventory'):
        try:
            evidence = db_audit(service, candidates)
            result['databases'].append(evidence)
            if service == 'Order':
                candidates = evidence.get('failed_command_replay_candidates', [])
        except Exception as error:
            result['databases'].append({'service': service, 'error_type': type(error).__name__})
    try:
        result['kafka'] = kafka_audit(args.kafka_bootstrap, args.scan_limit)
    except Exception as error:
        result['kafka'] = {'error_type': type(error).__name__,
                           'error_code': error.args[0].code() if error.args and hasattr(error.args[0], 'code') else None}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, default=str), encoding='utf-8')
    print(json.dumps(result, default=str))


if __name__ == '__main__':
    main()
