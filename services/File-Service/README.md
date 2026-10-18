# File-Service runtime and migration safety

On 2026-10-12 the operator confirmed that File-Service (8101) and shared MySQL
(`100.93.252.63:3306`) had been stopped after the reported startup success. Cleanup
does not restart them or establish current readiness. Migration states below are
historical audit observations, not a new check of the shared server.

This workspace remains on `feature/order-service`. Integration commits `0dc2b1f5`
and `9a71b6c3` are not yet incorporated. Preserve the integration branch/worktree at
`E:/HVTra-integration/20261012-002258/worktree`, including its Feign scanning fixes,
strict Flyway configuration and `services/File-Service/docs/shared-migration-plan.md`.

Development credentials belong only in ignored `file.env`. Load its datasource,
MinIO and existing `INTERNAL_HEADER_SECRET` variables into the process; never commit
the file, print secrets, or borrow another service's credentials. `application.yml`
uses those variables without datasource/MinIO fallbacks.

## MinIO initialization

`file.storage.initialize-buckets` defaults to `true`, preserving existing behavior:
check/create the configured buckets and set the public bucket's read policy.
Set it to `false` for an audit: Spring does not instantiate `MinioBucketInitializer`,
so this initializer performs no storage reads or writes. The MinIO client and API
authorization remain configured normally. Existing bucket policies are untouched.

For a read-only startup audit, use all of these runtime overrides:

```text
--spring.flyway.enabled=false
--spring.jpa.hibernate.ddl-auto=validate
--spring.sql.init.mode=never
--file.storage.initialize-buckets=false
--spring.datasource.hikari.connection-init-sql=SET SESSION TRANSACTION READ ONLY
--spring.datasource.hikari.read-only=true
--eureka.client.enabled=false
--spring.kafka.admin.auto-create=false
--spring.kafka.listener.auto-startup=false
--kafka-config.enabled=false
--kafka-consumer-config.enabled=false
--server.address=127.0.0.1
--server.port=8101
--spring.main.lazy-initialization=false
--management.endpoint.health.show-details=never
```

Pass each option as one argument (including the connection-init SQL). Do not disable
authentication/HMAC checks. These options are for an explicitly requested audit;
normal startup must retain Flyway validation. A health result alone does not verify
upload, binding, sharing or download behavior.

File-Service explicitly scans both its own JPA entities/repositories and the shared
`BusinessSequence` entity/repository. Component scanning Common-Service alone does
not register its JPA repository; the shared sequence service otherwise prevents startup.
This scan performs no schema creation with `ddl-auto=validate`.

## Migration convention and current blockers

New migrations use `VyyyyMMddHHmmss__description.sql` with their actual creation time
in `Asia/Bangkok`. Check DB history first; keep `outOfOrder=false`. Historical V1–V6
must remain unchanged. Run the repository naming checker documented in `AGENTS.md`.

The shared `file_db` has applied `V6__create_business_sequences.sql`, checksum
`-478525518`. Original source was recovered byte-for-byte from commit
`e7f883fb843872e373174bf694dd17b40e0fa4d4`, blob
`1736ee05f27c418b5d5744dc69b1fb372b7973c6`, on 2026-10-11.
SHA-256 is `3a158eba8385819faf2ad208247b38d26f38635dc786afa0a0be11d0cbb5490b`.
Both Flyway CLI 13.7.0 and runtime 11.7.2 calculate the applied checksum.
Read-only `info` reports V1–V6 Success and AI binding Pending; standalone validation
with only `*:pending` ignored passes. No migrate/repair/baseline/history edit occurred.
The naming checker records this applied version as a checksum-guarded recovery
exception, so verified original V6 can keep its historical name when recovered.
That exception does not establish source provenance or authorize a fabricated replacement.

The unapplied AI binding script is now
`V20261010221221__unique_ai_report_original_binding.sql` (unchanged SQL, checksum
`-996233198`). It adds two stored generated `BINARY(16)` columns and unique indexes
for non-deleted `AI_REPORT`/`ORIGINAL` references. It does not update business columns
or delete rows; ALTER computes the stored generated values for existing rows.
Other/deleted references generate NULL keys; a deleted/inactive parent file alone
does not exclude a still-active reference. The live collation controls string matching.
MySQL 8.4 supports indexed stored generated columns; see the
[official generated-column documentation](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html).

`fk_file_references_file` is absent even though V1/V2 declare it. Types are compatible
and the last read-only audit found zero orphan references, but no trustworthy evidence
establishes why the FK disappeared. After authorized isolated MySQL 8.4.11 tests,
`V20261011203800__restore_file_references_foreign_key.sql` implements conservative
ADD/NO_OP/STOP gates. It adds the original restrictive FK only when absent, preserves
equivalent renamed FKs and rejects orphan/conflicting schemas without editing rows
or dropping constraints. The [FK recovery design](docs/fk-recovery.md) records the
metadata gates, helper procedure privileges and failure lifecycle. The complete
fresh chain passes Flyway CLI 13.7.0 and File-Service runtime 11.7.2 validation;
details and the completed test-container cleanup are in the
[isolated verification report](docs/isolated-mysql-verification.md).
These results do not authorize shared migration or establish business E2E readiness.

## Shared execution runbook

Do not run this plan until original V6 recovery, strict history validation and the FK
investigation are complete. No shared database/storage write is authorized by this guide.

Use the integration worktree's `services/File-Service/docs/shared-migration-plan.md`
as the full operational runbook. Recheck history/checksums, schema/data preconditions
and privileges; obtain a complete `file_db` backup with tested restore, an effective
write gate and explicit approval for AI `20261010221221`, then FK `20261011203800`.
Keep the gate through both stages and final validation. Never repair, baseline, clean
or edit history to hide a failure. Shared business E2E needs separate authorization.

The [last shared audit](../../.tmp/file-service-shared-readiness-20261012/readiness-report.md)
and its JSON evidence remain available. Its V1–V6 Success/two Pending result is a
historical snapshot. Older audit scripts/reports have verified copies in
`E:/HVTra-integration/20261012-002258/backup/files`; intermediate local copies and
audit dependencies were removed. Restore the harness/dependencies before reuse.

Public APIs remain `POST /api/file/init-upload`, `POST /api/file/{fileId}/complete`,
`PUT /api/file/attach` and `GET /api/file/{fileId}/download-url`. They require the
existing authenticated Gateway/HMAC context; private downloads retain owner checks.
Internal `/internal/file/ai-reports` APIs retain the signed
`service:recommendation-service` caller plus `ROLE_SYSTEM` requirement. File export
means downloading original bytes through the signed URL, not generating Excel rows.
