#!/usr/bin/env bash
set -e

CONNECT_URL="${CONNECT_URL:-http://100.99.2.30:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# DB_HOST = dia chi MySQL NHIN TU BEN TRONG container Kafka Connect tren box 2.
# Connector dang tro Kafka bang ten container ("kafka:29092"), nen thuong DB_HOST
# cung la ten container MySQL trong cung compose network, khong phai IP cua may.
# Khong dat default: dien sai host thi connector chay nhung khong doc duoc binlog.
if [ -z "${DB_HOST:-}" ]; then
  echo "Thieu DB_HOST." >&2
  echo "  DB_HOST=<ten container MySQL tren box 2, vi du mysql> $0" >&2
  exit 1
fi

# Password cua user debezium. Default giu nguyen gia tri cu de khong pha dang ky
# hien tai, nhung day la credential nam trong git -> nen rotate.
DB_PASSWORD="${DEBEZIUM_DB_PASSWORD:-debezium}"

for file in "$SCRIPT_DIR"/*-connector.json; do
  [ -e "$file" ] || continue
  NAME=$(jq -r '.name' "$file")
  echo "Registering/Updating $NAME..."
  CONFIG=$(jq --arg dbHost "$DB_HOST" --arg dbPass "$DB_PASSWORD" \
    '.config | ."database.hostname" = $dbHost | ."database.password" = $dbPass' "$file")
  curl -s -X PUT -H "Content-Type: application/json" \
    --data "$CONFIG" \
    "$CONNECT_URL/connectors/$NAME/config" | jq .name
done

echo ""
echo "Connector status:"
curl -s "$CONNECT_URL/connectors?expand=status" | jq .
