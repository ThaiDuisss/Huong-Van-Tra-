#!/usr/bin/env bash
# Read-only host evidence. Run on the Docker host, after identifying names below.
set -euo pipefail
if [[ $# -ne 2 ]]; then
  echo "Usage: bash audit-connect-host.sh <connect-container> <mysql-container>" >&2
  echo "Identify containers first: docker ps --format '{{.ID}} {{.Names}} {{.Image}} {{.Networks}}'" >&2
  exit 2
fi
connect_container=$1
mysql_container=$2
for container in "$connect_container" "$mysql_container"; do
  # Never dump .Config.Env, credentials, complete inspect output or application logs.
  docker inspect --format '{{json .}}' "$container" | jq '{
    Id, Name, image: .Config.Image, state: .State.Status, started: .State.StartedAt,
    compose: {project: .Config.Labels["com.docker.compose.project"],
      service: .Config.Labels["com.docker.compose.service"],
      source_files: .Config.Labels["com.docker.compose.project.config_files"],
      working_dir: .Config.Labels["com.docker.compose.project.working_dir"]},
    networks: (.NetworkSettings.Networks | with_entries(.value |= {NetworkID, IPAddress, Aliases, DNSNames}))
  }'
done
docker exec "$connect_container" getent hosts mysql
docker exec "$connect_container" getent hosts kafka
# Existing Connect images normally include bash. Failure means this check is
# unavailable; do not install packages or alter the running container for audit.
docker exec "$connect_container" bash -c 'timeout 4 bash -c "exec 3<>/dev/tcp/mysql/3306" && echo MYSQL_TCP_OK'
docker exec "$connect_container" bash -c 'timeout 4 bash -c "exec 3<>/dev/tcp/kafka/29092" && echo KAFKA_TCP_OK'
