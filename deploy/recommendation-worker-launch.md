# Recommendation worker: disabled managed launch profile

This profile is source preparation only. It does not authorize shared ingestion.
External worker ownership and K1 gates G01–G08 are still unverified. Do not start
a second writer, change the existing group, reset offsets or replay events to
discover ownership.

`compose.recommendation-worker.json` is a standalone Compose document (JSON is
also valid YAML), deliberately absent from the normal production Compose and CI.
Its only service has the opt-in `recommendation-worker` profile, `restart: no`,
no published/exposed HTTP ports, no dependencies, no migration and no health
check borrowed from the API. It uses the existing Recommendation Dockerfile;
the image's FastAPI CMD is unchanged. The inherited image EXPOSE metadata does
not start a listener or publish a port. Only this service overrides the command
with `python -B scripts/run_kafka_worker.py`.

Selecting a Compose service directly can bypass profile selection. The Python
guard remains mandatory even then. A missing host mount may prevent container
creation before the guard: that is a refusal, never permission to create a dummy
approval file. Neither approval nor credential files are shipped in the repo.

## Independent approval boundary

The deployment owner must provide two separate read-only mounts:

| Container location | Purpose |
| --- | --- |
| `/run/hvtra-worker-approval/approved-binding.json` | Non-secret, time-bounded owner approval and exact target binding |
| `/run/secrets/recommendation_worker_mysql_password` | UTF-8 password; an optional single final newline is removed |

Mount sources have no usable defaults and `create_host_path: false`. The profile
runs as UID/GID 65532. The Linux guard requires UID 0 ownership of each file and
its immediate directory, no group/world write access, a regular non-symlink
file and a non-symlink directory. Filesystem/host deployment authority is the
trust root; this is not a cryptographic signature or protection from a hostile
host/root/image operator. Windows direct activation and running the guard as
root are refused; do not weaken these checks to make Docker Desktop binds work.
The binding path cannot be selected via worker environment or a CLI option.

Binding schema (no activatable example or default approval):

| Field | Required content |
| --- | --- |
| `schema_version` | Integer 1 |
| `approval.status` | `APPROVED` |
| `approval.approval_id`, `approved_by` | Accountable approval reference/actor |
| `approval.not_before`, `expires_at` | Explicit timezone timestamps; current time must be in this interval |
| `approval.gate_evidence` | Exactly G01–G08, each with its owner-verified evidence reference |
| `target` | Exactly the non-secret fields in `scripts/worker_launch_guard.py:TARGET_FIELDS`, all nonempty strings |

Unknown/duplicate JSON fields, missing prerequisites and changed target values
are refused. `target` binds environment, writer role, revision, broker cluster,
both stream incarnations, bootstrap endpoints, existing group, both topic names,
offset fallback, Kafka security protocol and DB server/host/port/schema/principal.
The only supported writer role is `recommendation-kafka-worker`.

The existing Kafka factory has no SASL/TLS configuration mapping. K2 requires an
explicit owner-approved `PLAINTEXT` protocol on both sides and refuses SASL/TLS
configuration rather than ignoring it. This is not a recommendation to use an
unprotected broker: if the intended broker requires TLS/SASL, activation remains
blocked until a separate reviewed change adds that path. No network identity,
grants, offsets, group existence or database schema is verified by this guard.

## Configuration and dotenv isolation

`HVTRA_WORKER_ENABLED` must be exactly `true`. Missing/empty/`false` is disabled;
other spellings are invalid. Every target field and manual-commit=false must be
explicit. No default host, group, topic or password from Settings may authorize a
launch. The approved binding is compared before the password file is read.
Environment names must also fit the existing Settings literals: `production`,
`development` or `test`. None of these labels proves isolation or approval.

The Compose document uses only explicit `HVTRA_*` worker interpolation inputs,
not the API's MYSQL/KAFKA variables; it has no `env_file` or password environment
passthrough. Compose itself is an external client: an eventual authorized operator
must disable its automatic dotenv loading (`COMPOSE_DISABLE_ENV_FILE=1`) and
provide a reviewed explicit interpolation environment. Do not dump `compose config`
or source the API's `.env`. K2 does not execute Compose or inspect that environment.

After authorization, the dedicated Python process discards ambient Settings/debug
environment, passes only the validated DB/Kafka settings, disables SQL echo and
sets `Settings.model_config['env_file'] = None` **before** importing core.config,
database.session or app.kafka. The API process and its dotenv behavior are
unchanged. Reusing a process that already imported runtime modules is refused.
Guard output uses fixed reason codes. Runtime logging redacts the loaded password,
DB principal, URL-encoded forms and URI strings; this does not turn raw logs into
a durable receipt or audit ledger.

## Exit policy and remaining blockers

| Code | Meaning |
| --- | --- |
| 0 | Intentionally disabled; worker was not imported or started |
| 64 | Invalid explicit opt-in/configuration |
| 77 | Unauthorized target or untrusted/expired/unapproved provisioning |
| 78 | Missing/invalid approval, configuration, credential or unsupported security path |
| 70 | Runtime failure or unclassified return from legacy worker.main |
| 130 | KeyboardInterrupt propagated to the launcher |

The legacy worker returns normally for both a signal stop and protective stop.
K2 does not modify that control flow, add callbacks or DB fencing. It conservatively
maps **every** normal worker return to `WORKER_STOP_UNCLASSIFIED`/70, including a
graceful SIGTERM return; it never reports such a return as silent success. A future
worker-lifecycle task must supply reliable normal/abnormal stop reasons. Automatic
restart remains disabled. Existing rebalance/assignment, DB-commit-before-ACK,
operational RETURN no-DB ACK and PRODUCT/CUSTOM_BUNDLE boundaries remain unchanged.

## Offline review only

`tests/test_worker_launch.py` uses fictional target metadata and mocked root-owned
files/worker imports; those fixtures are not production approvals. Consumer
regressions must run with network/process/DB connection guards and fake settings.
Do not run a worker/API, build/start the image, register a connector, query a broker
or run a connection-capable smoke test as part of this source-only task.

Shared activation still requires actual owner inventory and K1 G01–G08 evidence,
reviewed input/image provenance, fault/rebalance verification and a cutover/stop
plan. No approval has been supplied by this change.
