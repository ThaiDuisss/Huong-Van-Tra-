# Return pipeline recovery — L03/L04/L08

Environment boundary: `100.99.2.30` is the **production** target of this audit.
Kafka localhost is working normally according to the user and is a separate
environment. Kafka runtime is outside Task 1 acceptance. No Kafka configuration
change or service restart is authorized in this task. Operational steps below
are a handoff for a separately authorized production task, not actions to run now.
Before later Catalog → Recommendation deployment, explicitly identify the target
environment, service/DB/broker endpoints and topic/group identities.

This repository contains connector templates and registration scripts, but no
deployment definition for the remote MySQL/Kafka/Connect stack. The only Compose
file found is `web-app/docker-compose.yml` for the frontend. Do not treat that
file as the deployment source for host `100.99.2.30`.

## Read-only evidence from the host manager

Run on the Docker host, replacing only the observed container names:

```bash
docker ps --format '{{.ID}} {{.Names}} {{.Image}} {{.Networks}}'
bash infra/debezium/audit-connect-host.sh <connect-container> <mysql-container>
docker network ls --format '{{.ID}} {{.Name}} {{.Driver}}'
docker network inspect <observed-network> --format '{{json .Containers}}'
```

The script retains only image/state, Compose source labels and network aliases.
Do not send `docker inspect` output containing environment variables, `docker
compose config` output containing expanded credentials, or secrets from `.env`.
If Compose labels are absent, provide the actual deployment method and manifest
location. Verify whether MySQL container hostname `968ab1c8f052` still identifies
the database read by the services; an old container or replica is not a substitute.

Expected evidence before proposing any hostname change:

- Connect and the actual MySQL container share a user-defined Docker network.
- That network exposes the stable service name/alias `mysql` to Connect.
- From Connect, `mysql:3306` and `kafka:29092` resolve and accept TCP connections.
- The broker's external advertised listener is reachable by the service hosts.
  Read-only Metadata currently advertises `172.31.42.27:19092` to clients of
  `100.99.2.30:19092`; client transport failure is independently observed.
- Identify the Compose project/service/file using labels before editing the
  authoritative source. Restore its network membership/alias persistently;
  avoid a one-off IP change or an untracked `docker network connect` patch.

The connector templates intentionally require `__DB_HOST__`. The live value is
`mysql`; no repo evidence currently proves that this hostname should be replaced.
The host manager must return the network/source evidence above before an actual
deployment patch can be written against the correct source.

## Deployment order (requires explicit authorization for shared mutations)

1. Keep CDC tasks stopped/failed while deploying application safety guards. Do
   not invoke the existing `register-connectors.*` scripts as a diagnostic: they
   PUT every connector config and can overwrite existing database credentials.
2. Deploy Order first: retain FAILED reservations; reject new identities for a
   return with FAILED commands; retain PENDING after delivery recovery exhausts;
   expose `executionAllowed` on command GET. GET a known PENDING command and
   confirm the field exists before enabling the new Inventory consumer.
3. Deploy Inventory: require authoritative executable PENDING command and exact
   item/quantity, serialize allocation/posting on existing item rows, refresh
   locked inspections, preserve POSTED idempotency. Deploy frontend guards too.
   Test an unavailable/old Order contract: stock writes must stay blocked.
4. Review every FAILED command against Inventory inbox, inspections, ledger and
   Kafka retention evidence. Do not reset FAILED to PENDING. Existing unsafe
   retries with a different commandId need individual reconciliation. A FAILED
   command remains blocked in this patch; no automatic retry approval is exposed.
5. After replay safety and authorization, apply the verified network fix in the
   real deployment source. Preserve connector names, credentials, offsets,
   topic.prefix, server.id, schema history and outbox rows. Network restoration
   itself can enable automatic retry, so it belongs behind the mutation gate.
6. With authorization, restart only failed tasks individually. Example after
   guards and network verification, never during this audit:

   ```bash
   curl --fail -X POST 'http://100.99.2.30:8083/connectors/order-outbox-connector/restart?includeTasks=true&onlyFailed=true'
   curl --fail -X POST 'http://100.99.2.30:8083/connectors/inventory-outbox-connector/restart?includeTasks=true&onlyFailed=true'
   ```

   Payment also has a FAILED task. Audit its backlog/consumers independently
   before restarting it; return disposition guards do not prove payment safety.
7. Confirm connector **and task** RUNNING, inspect exact eventId/commandId at
   both inboxes and source offsets, and check ledger totals. RUNNING alone is
   insufficient. If the saved binlog position has expired, stop and investigate;
   do not delete offsets/history or force a new snapshot.
8. Run the shared E2E only after separate authorization: new run-specific return
   data for RESTOCK, DAMAGED and WRITE_OFF; receive; submit; inspection CREATED;
   actual condition; POSTED; ACK PROCESSED; remaining zero; no repeated action.
   Repeat same command and POST; transaction and balance totals must not change.
   Include partial/multiple items, different-command collision and delayed ACK.

## Rollback

If verification fails, pause the affected connector/task and application
consumers under the same operational authorization, retain all records and
offsets, and collect evidence. Keep reservation/Inventory guards while CDC may
replay. Rolling back to the old application while backlog can arrive recreates
the double-processing risk. Revert only the reviewed deployment-source patch if
needed; do not wipe or restore DB data, offsets, history or topics to force PASS.

## Repeatable local verification

```powershell
python -B scripts/audit_return_pipeline.py --output .local/return-pipeline-fix-20261104/runtime-audit.json
services/Recommendation-Service/.venv/Scripts/python.exe -B scripts/audit_return_pipeline_data.py --output .local/return-pipeline-fix-20261104/data-audit.json
python -B scripts/audit_kafka_return_records.py --data-audit .local/return-pipeline-fix-20261104/data-audit.json --output .local/return-pipeline-fix-20261104/kafka-records.json
```

The direct Kafka reader is a diagnostic for the observed single broker only.
It never joins groups or commits offsets. It reports retention low/high offsets,
scan bounds and completeness; missing retained records cannot prove an event was
never published before retention. Its limited codec support is explicit.

Protocol/source references: [Kafka Fetch schema](https://github.com/apache/kafka/blob/3.8/clients/src/main/resources/common/message/FetchRequest.json),
[MySQL connector offset/history behavior](https://debezium.io/documentation/reference/stable/connectors/mysql.html).
