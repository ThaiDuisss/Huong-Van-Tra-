"""Read retained Kafka records directly from the verified single-broker endpoint.

Diagnostic fallback for a broken advertised listener; never joins a group,
commits offsets, creates topics or produces records. Implements only read APIs
Metadata v0, ListOffsets v1 and Fetch v4 from the Apache Kafka 3.8 protocol.
Unsupported record compression or incomplete responses fail closed.
"""
import argparse
from datetime import datetime, timezone
import gzip
import json
from pathlib import Path
import socket
import struct
import time


def pack(fmt, *values):
    return struct.pack('>' + fmt, *values)


def string(value):
    raw = value.encode()
    return pack('h', len(raw)) + raw


class Reader:
    def __init__(self, raw):
        self.raw, self.pos = raw, 0

    def take(self, count):
        if count < 0 or self.pos + count > len(self.raw):
            raise ValueError('Incomplete Kafka response')
        value = self.raw[self.pos:self.pos + count]
        self.pos += count
        return value

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]

    def text(self):
        return self.take(self.number('h')).decode()

    def varint(self):
        value = shift = 0
        while shift < 70:
            byte = self.number('B')
            value |= (byte & 127) << shift
            if byte < 128:
                return (value >> 1) ^ -(value & 1)
            shift += 7
        raise ValueError('Invalid variable integer')


class Broker:
    def __init__(self, host, port):
        self.host, self.port = host, port

    def request(self, key, version, body):
        if key not in (1, 2, 3):
            raise ValueError('Only Kafka read APIs are permitted')
        raw = pack('hhi', key, version, 31) + string('return-readonly-audit') + body
        with socket.create_connection((self.host, self.port), timeout=6) as connection:
            connection.settimeout(8)
            connection.sendall(pack('i', len(raw)) + raw)
            def read(count):
                chunks = bytearray()
                while len(chunks) < count:
                    part = connection.recv(count - len(chunks))
                    if not part:
                        raise ValueError('Connection closed before response completed')
                    chunks.extend(part)
                return bytes(chunks)
            length = struct.unpack('>i', read(4))[0]
            if not 4 <= length <= 16 * 1024 * 1024:
                raise ValueError('Kafka response exceeds diagnostic bound')
            reader = Reader(read(length))
            if reader.number('i') != 31:
                raise ValueError('Mismatched correlation ID')
            return reader

    def metadata(self):
        r = self.request(3, 0, pack('i', 0))
        brokers = []
        for _ in range(r.number('i')):
            brokers.append({'id': r.number('i'), 'host': r.text(), 'port': r.number('i')})
        topics = []
        for _ in range(r.number('i')):
            error, name = r.number('h'), r.text()
            for _ in range(r.number('i')):
                partition_error, partition, leader = r.number('h'), r.number('i'), r.number('i')
                for _ in range(2):
                    r.take(4 * r.number('i'))
                if 'RETURN_INVENTORY_DISPOSITION' in name:
                    topics.append({'topic': name, 'partition': partition, 'leader': leader,
                                   'error': error or partition_error})
        return brokers, topics

    def offset(self, topic, partition, timestamp):
        r = self.request(2, 1, pack('ii', -1, 1) + string(topic) + pack('iiq', 1, partition, timestamp))
        if r.number('i') != 1 or r.text() != topic or r.number('i') != 1 or r.number('i') != partition:
            raise ValueError('Unexpected ListOffsets response')
        error = r.number('h')
        r.number('q')
        offset = r.number('q')
        if error:
            raise ValueError('ListOffsets broker error ' + str(error))
        return offset

    def fetch(self, topic, partition, offset):
        body = pack('iiii', -1, 100, 1, 4 * 1024 * 1024) + pack('b', 0)
        body += pack('i', 1) + string(topic) + pack('iiqi', 1, partition, offset, 4 * 1024 * 1024)
        r = self.request(1, 4, body)
        r.number('i')  # throttle
        if r.number('i') != 1 or r.text() != topic or r.number('i') != 1 or r.number('i') != partition:
            raise ValueError('Unexpected Fetch response')
        error = r.number('h')
        r.number('q')  # high watermark
        r.number('q')  # last stable offset
        aborted_count = r.number('i')
        if aborted_count > 0:
            raise ValueError('Transactional records require an aborted transaction filter')
        if error:
            raise ValueError('Fetch broker error ' + str(error))
        return r.take(max(0, r.number('i')))


def records(raw):
    r = Reader(raw)
    while r.pos < len(raw):
        base, length = r.number('q'), r.number('i')
        batch = Reader(r.take(length))
        batch.number('i')  # leader epoch
        if batch.number('b') != 2:
            raise ValueError('Only Kafka record format v2 is supported')
        batch.number('i')  # CRC; diagnostic reader does not certify CRC integrity
        attributes = batch.number('h')
        batch.number('i')  # last offset delta
        first_timestamp = batch.number('q')
        batch.take(8 + 8 + 2 + 4)
        count = batch.number('i')
        content = batch.take(length - batch.pos)
        compression = attributes & 7
        if compression == 1:
            content = gzip.decompress(content)
        elif compression != 0:
            raise ValueError('Unsupported compression codec ' + str(compression))
        entries = Reader(content)
        for _ in range(count):
            record_length = entries.varint()
            record = Reader(entries.take(record_length))
            record.number('b')
            timestamp = first_timestamp + record.varint()
            offset = base + record.varint()
            key_length = record.varint()
            key = record.take(key_length) if key_length >= 0 else b''
            value_length = record.varint()
            value = record.take(value_length) if value_length >= 0 else b''
            yield offset, timestamp, key, value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='100.99.2.30')
    parser.add_argument('--port', type=int, default=19092)
    parser.add_argument('--data-audit', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--limit', type=int, default=5000)
    args = parser.parse_args()
    data = json.loads(args.data_audit.read_text(encoding='utf-8'))
    commands = {r['command_id'] for db in data['databases'] for r in db.get('failed_command_replay_candidates', [])}
    event_ids = {r['event_id'] for db in data['databases'] for r in db.get('matching_outbox', [])}
    broker = Broker(args.host, args.port)
    result = {'observed_at': datetime.now(timezone.utc).isoformat(), 'mutations_executed': False,
              'offsets_committed': 0, 'groups_joined': 0, 'broker_endpoint': f'{args.host}:{args.port}', 'partitions': []}
    try:
        result['advertised_brokers'], topics = broker.metadata()
        if len(result['advertised_brokers']) != 1:
            raise ValueError('Direct fallback requires a verified single broker')
        for topic in topics:
            row = dict(topic)
            result['partitions'].append(row)
            if topic['error']:
                continue
            try:
                low = broker.offset(topic['topic'], topic['partition'], -2)
                high = broker.offset(topic['topic'], topic['partition'], -1)
                offset = max(low, high - args.limit)
                row.update(low=low, high=high, scan_start=offset, read_records=0, matches=[], complete=False)
                deadline = time.monotonic() + 25
                while offset < high and time.monotonic() < deadline:
                    previous = offset
                    for position, timestamp, key, value in records(broker.fetch(topic['topic'], topic['partition'], offset)):
                        offset = max(offset, position + 1)
                        if position < previous or position >= high:
                            continue
                        row['read_records'] += 1
                        text = (key + b' ' + value).decode('utf-8', errors='replace')
                        hits = sorted(command for command in commands if command in text)
                        if hits:
                            row['matches'].append({'offset': position, 'timestamp': timestamp,
                                                   'command_ids': hits, 'event_ids': sorted(e for e in event_ids if e in text)})
                    if offset <= previous:
                        break
                row['complete'] = offset >= high
            except Exception as error:
                row['error_type'] = type(error).__name__
                row['complete'] = False
    except Exception as error:
        result['error_type'] = type(error).__name__
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
