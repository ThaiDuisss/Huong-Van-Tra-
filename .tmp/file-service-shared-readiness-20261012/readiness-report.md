# File-Service shared readiness — 2026-10-12, UTC+07:00

**NO-GO for shared migration; normal startup and business E2E remain BLOCKED.**
Live read-only checks used the integrated source at
`E:/HVTra-integration/20261012-002258/worktree`. Credentials were read in memory
from the original ignored `services/File-Service/file.env`; no credential value
was printed, changed or copied. Database audit began at 01:39:13 local time.

| Area | Result | Current evidence |
|---|---|---|
| Tailscale | PASS | Backend Running, local node and target peer online; target /32 route uses Tailscale |
| Shared MySQL | PASS | TCP/auth/schema reads; 100.93.252.63:3306; selected file_db; MySQL 8.4.11; server 51dada3adecb |
| Shared MinIO | PASS | HTTPS/TCP, health HTTP 200, authenticated bucket listing and policy reads |
| Migration preconditions | PASS | V1–V6 Success and matching checksums; exactly two reviewed Pending; no targeted schema drift |
| Backup / restore drill | BLOCKED | No supplied manifest or tested restore; no backup/restore authorization; backup account/tool execution path not approved |
| Maintenance / write gate | BLOCKED | No assigned owner or complete remote writer inventory; no implemented gate; global transaction/MDL visibility denied |
| Shared migration execution | BLOCKED | No execution approval; backup and maintenance gates incomplete |
| Normal startup / health | BLOCKED | Not started; pending migrations and deployment dependencies unresolved |
| Business E2E | BLOCKED | Not attempted; separate DB/storage test-write and cleanup authorization required |

## Live database and source verification

| Version | Script | State | Flyway checksum |
|---|---|---|---|
| 1 | V1__init_schema.sql | Success | 1563888655 |
| 2 | V2__create_file_references_if_missing.sql | Success | 1876724228 |
| 3 | V3__fix_audit_column_types.sql | Success | -1382391432 |
| 4 | V4__update_files_table_schema.sql | Success | 1408072548 |
| 5 | V5__delete_column_refType.sql | Success | -93814498 |
| 6 | V6__create_business_sequences.sql | Success | -478525518 |
| 20261010221221 | V20261010221221__unique_ai_report_original_binding.sql | Pending | -996233198 |
| 20261011203800 | V20261011203800__restore_file_references_foreign_key.sql | Pending | 1229623600 |

All eight source SHA-256/checksums match the verified integration test artifact.
Flyway CLI 13.7.0 info and validate were run with explicit target/schema/location,
an empty config, and session transaction read-only. Validation ignoring only Pending
PASS: JSON validationSuccessful=true, no invalid migrations. Strict validation
returned validationSuccessful=false solely for the two resolved/unapplied versions.
Its process exit code was zero; JSON was checked rather than relying on that code.
No failed/missing/future/checksum mismatch was found.

Counts and full-row fingerprints before/after audit remained unchanged:
files 15, file_references 0, business_sequences 0, Flyway history 6.
Closing verification again found the same fingerprints. These snapshots establish
observed stability, not a maintenance write gate or a guarantee against future writes.

AI preconditions PASS: zero active AI_REPORT/ORIGINAL duplicate groups by file_id
or ref_id; zero NULL/invalid binary/boolean inputs; no generated-column or unique
index collisions. Keys are ordinary BINARY(16) NOT NULL; predicate strings are
VARCHAR(50), utf8mb4_0900_ai_ci; is_deleted is TINYINT(1) NOT NULL. Deleted/non-AI
references produce NULL generated keys; an active reference to a deleted parent
still generates keys. No live generated columns exist yet.

FK preconditions PASS: both tables are InnoDB and not partitioned; compatible keys;
visible full BTREE parent PRIMARY(id) and child file_id-leading index; zero orphans;
no equivalent/renamed FK, canonical constraint collision or helper routine collision.
The expected FK remains absent. Its disappearance is still unexplained; the owner
must confirm the intended restrictive relationship before approving recovery.

Live files/file_references DDL matches the isolated fixture after excluding the two
pending migrations' effects. Identity/history/columns/indexes/FKs/DDL also match the
previous shared preflight. business_sequences inherits the shared database's older
utf8mb4_unicode_ci default; the isolated fixture uses utf8mb4_0900_ai_ci. This known
database-default difference is unaffected by the pending pair and was not changed.
Reported total table data/index allocation is 245760 bytes; the reference table has
zero rows. A small table does not remove ALTER TABLE metadata-lock/rebuild risk.
Server disk headroom and worst-case lock duration remain unverified.

## MinIO

Endpoint `https://storage.hvtra.id.vn` comes from legitimate file.env and the YAML
mapping. TLS validation was kept enabled. DNS resolution and port 443 passed.
Authenticated GET/list/policy requests passed without any object upload/download.

- hvt-file-public exists and has an unconditional anonymous s3:GetObject Allow.
- hvt-file-private exists; authenticated policy read returned NoSuchBucketPolicy.
  There is no bucket policy granting anonymous access; authenticated users' IAM
  permissions were not exhaustively audited.

No bucket creation/deletion, policy change or storage initialization was performed.
Normal startup must explicitly set `file.storage.initialize-buckets=false` because
the source default true invokes public-policy writes even when buckets already exist.

## Backup and restore preparation — proposal only

Windows PATH has no mysql, mysqldump or mysqlsh client. Docker is running and its
existing isolated MySQL container contains mysqldump 8.4.11; only `--version` was
executed. That container and volume were not modified and were not given shared
credentials. A reviewed DBA-side client, or a separately approved backup-client
container with a verified route to the shared host, is needed for actual execution.

Full backup scope: the entire file_db schema, all table data and indexes/constraints,
database defaults, flyway_schema_history, business_sequences, views, routines,
events and triggers. Live metadata currently shows four InnoDB base tables, no
views, routines/events or triggers. Repeat the inventory at backup time; never
restrict the dump to the two altered tables. Backup remains private data even though
its credential file is separate; store it outside Git with restricted access,
encryption, retention and a manifest containing timestamp, server identity, tool
version, file size/SHA-256, complete object inventory and source fingerprints.

The proposed command below is **NOT executed**. `<protected-client.cnf>` would be
created only after separate approval, with correctly escaped legitimate credentials,
restricted ACLs and cleanup. No password may appear in arguments or logs.

```text
<approved-mysqldump-8.4> --defaults-extra-file=<protected-client.cnf>
  --host=100.93.252.63 --port=3306 --protocol=TCP
  --single-transaction --quick --hex-blob --no-tablespaces
  --routines --events --triggers --set-gtid-purged=OFF
  --skip-lock-tables --databases file_db
  --result-file=<encrypted-or-protected-backup-directory>/file_db.sql
```

All observed tables are InnoDB, making a single-transaction data snapshot suitable.
Freeze schema DDL throughout the dump; ALTER/CREATE/DROP/RENAME/TRUNCATE can invalidate
its consistency. GTID is currently OFF and binlog ON with 30-day expiry. Binlog
listing is denied (1227); binlogs alone do not prove a usable backup or PITR path.
The proposed command does not flush logs or take a global read lock. For the migration
backup, keep the approved write gate active as well and reject any dump error.
These semantics and dump privilege requirements follow the
[MySQL 8.4 mysqldump documentation](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html).

Current grants are ALL PRIVILEGES on file_db.*, global USAGE, no active roles or
grant option. Required migration privileges are declared, including SELECT, ALTER,
REFERENCES, CREATE ROUTINE, EXECUTE and ALTER ROUTINE; no DDL privilege probe ran.
The proposed --routines backup needs global SELECT according to the documented
mysqldump requirement, which this account lacks. Have the DBA select an approved
complete-backup account/path; do not silently omit routines or create credentials.

Restore drill preparation, requiring separate approval:

1. Create a new isolated MySQL 8.4.11 instance with newly generated test credentials,
   a new volume, loopback-only port and no connection to the shared network. Do not
   reuse or delete existing containers/volumes. No shared credentials enter it.
2. Restore the reviewed, checksum-verified dump only into that instance. A dump using
   --databases may create a logical file_db there; it must be on the isolated server,
   never at 100.93.252.63. Run import with fail-on-error handling and protect the
   real data now present in the test environment.
3. Compare schema/defaults/constraints/routines/events, all row counts and fingerprints,
   business sequence values and all six history scripts/checksums/success values to
   the backup-time manifest. Pending-only validation must PASS. Record restore time,
   tool versions, evidence and recovery owner.
4. Optionally, only with approval for test writes, apply the exact pending pair to
   this restored replica stage by stage; verify all eight Success, strict validation,
   expected AI indexes/restrictive FK, no helper and unchanged business data. This
   complements previous synthetic tests with the actual backup format.
5. Keep the protected drill artifacts until the owner authorizes cleanup. Rehearsal
   results and a signed-off recovery procedure are required before shared migration.

No backup file or restore target was created during this task.

## Maintenance and deployment discovery

No local Java File-Service/Eureka process or corresponding Windows service was
observed; Docker lists only the existing isolated MySQL container. Local port 8101
is not listening. These observations do not enumerate remote deployments.

The configured Eureka URL localhost:8761 is unreachable. Gateway's conventional
local 8080 is occupied by AgentService PID 4908; it was not stopped. The configured
default Kafka 100.93.252.63:19092 accepts TCP, which does not establish Kafka protocol,
broker authentication, topic availability or publish permissions. No Eureka/Gateway/
Kafka service was started or reconfigured.

File-Service source exposes DB writers via /api/file/init-upload, upload completion,
attach and internal AI binding. Completion can update status; sequences may be
written by shared sequence service calls. No scheduled jobs or Kafka/Rabbit listener
annotations were found in File-Service or its scanned Common core. Shared DB metadata
shows no file_db events/triggers. This does not prove no deployed/custom/direct writer.
Action logging publishes to Kafka. MinIO initializer is another startup writer and
must be disabled independently.

No other account-visible file_db sessions appeared in either audit sample. Global
INNODB_TRX requires unavailable PROCESS (1227); performance_schema metadata locks,
table write counters and instrumentation SELECT are denied (1142). Therefore global
quiescence, all writer accounts, long transactions and metadata-lock absence are
**UNVERIFIED**, not zero. No remote host/deployment inventory access was supplied.

Maintenance owner must inventory all instances, internal callers, jobs, deployment
automation and direct SQL writers. Prevent new public and internal writes, stop/drain
the relevant writers using a reviewed operational gate, and prevent automated restart
or deployment. Gateway routing alone cannot block internal/direct DB writers. Account
locking/revocation or stopping instances requires explicit approval and DBA ownership;
no such action was taken. Drain already-issued presigned uploads as appropriate
(source TTL 300 seconds, actual deployment TTL must be confirmed) and in-flight
requests, then have DBA verify global transactions/MDL and disk headroom. Keep the
gate through both migrations and final verification; do not reopen after stage 1.

INTERNAL_HEADER_SECRET is present in legitimate file.env; its value was not printed.
Its agreement with trusted callers/Gateway has not been verified. The six DB/MinIO
keys alone are insufficient for the eventual launch: securely load the required
existing HMAC variable too, retaining signature enforcement. No fallback secret or
auth bypass is approved. Eureka deployment address and Gateway availability need
confirmation before claiming operational discovery/routing readiness.

## Execution and startup gates

The reviewed integrated `services/File-Service/docs/shared-migration-plan.md` remains
the execution plan. Source integrity and live data prechecks now PASS, but its backup,
maintenance/DBA and explicit migration-approval gates remain open. Only this pair
may be proposed for shared execution, AI first (-996233198), then FK (1229623600).

After each approved stage: inspect exact Flyway version/script/checksum/success,
generated columns/indexes/FK/routine metadata and unchanged business fingerprints.
After stage 1 require AI Success and only FK Pending. After stage 2 require all eight
Success, strict JSON validationSuccessful=true, restrictive FK or preserved equivalent,
both AI indexes and no helper. If stage 2 fails, preserve stage 1 and maintain the gate;
capture evidence and seek separately approved forward recovery or tested restore.
Never repair/baseline/clean or manually edit history to hide the failure.

Only after shared migrations PASS may normal startup use original env in its child
process, strict Flyway validation and Hibernate validate, with storage initializer
explicitly false. No read-only/audit-only flags will be used to claim normal readiness.
Verify actual Eureka/Gateway/Kafka, health, DI/Feign, signed caller enforcement and
unauthenticated/forged request rejection before reopening traffic.

Business E2E requires separate shared data/storage write and cleanup authorization,
approved test user/files and object/row tracking. It must cover PDF/XLSX upload and
completion, binding, original bytes/filename/content type, owner/staff/internal caller
authorization, duplicate binding rejection and cleanup. No E2E or startup ran here.

## Evidence and no-change confirmation

Evidence in this directory: live-preflight.json, infrastructure.json,
schema-comparison.json, flyway-info-strict.json,
flyway-validate-ignore-pending.json, flyway-validate-strict.json and
closing-verification.json. Scripts use SELECT/SHOW, explicitly read-only sessions
and only Flyway info/validate; MinIO operations were HTTP GET.

Only ignored audit scripts/evidence/report files under this directory were created.
Source/migrations/file.env were not edited. No shared migrate, repair, baseline,
clean, history edit, DDL, business write, bucket/policy change, backup, restore,
writer stop, normal startup, E2E, commit or push was performed. Recommendation-Service
and unrelated services were not changed.
