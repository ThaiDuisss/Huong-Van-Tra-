#!/usr/bin/env bash
set -e

CONNECT_URL="${CONNECT_URL:-http://100.99.2.30:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for file in "$SCRIPT_DIR"/*-connector.json; do
  [ -e "$file" ] || continue
  NAME=$(jq -r '.name' "$file")
  echo "Registering/Updating $NAME..."
  CONFIG=$(jq '.config' "$file")
  curl -s -X PUT -H "Content-Type: application/json" \
    --data "$CONFIG" \
    "$CONNECT_URL/connectors/$NAME/config" | jq .name
done

echo ""
echo "Connector status:"
curl -s "$CONNECT_URL/connectors?expand=status" | jq .
