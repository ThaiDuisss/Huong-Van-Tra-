# Return/refund MySQL integration tests

Run from this module with Java 21, Maven and a working Docker Linux engine:

```shell
mvn -Pintegration-tests verify
```

The profile adds `src/integration-test/java` only for this run and uses Failsafe
(`*MySqlIT`). It does not run the unit suite. Run that separately:

```shell
mvn -Punit-tests clean verify
```

Do not activate both profiles together. Testcontainers 2.0.5 and MySQL 8.4.7 are
pinned. The tests never accept a JDBC URL or credentials for a development or
production server. They create their own disposable container, database
`order_return_refund_integration_test` and schema-limited account with a random
password. Before Flyway runs, the test verifies the actual JDBC URL, database,
current account and grants. Main application YAML is excluded by the profile.
The full production Flyway history runs unchanged; Hibernate validates rather
than creates/updates the schema. Flyway clean, baseline-on-migrate and
out-of-order execution are disabled. There are no DROP/TRUNCATE/cleanup scripts.

Only PaymentClient and InventoryClient are mocked. The service, repositories,
JPA, transaction manager, calculator, history writer and outbox writes are real.
No HTTP server, gateway, Kafka broker, Redis or external service is started.
Refund service calls use a request-scoped EntityManager, matching OSIV lifecycle,
without holding a transaction over the mocked Payment call. The test methods
have no wrapping `@Transactional`.

Return concurrency tests synchronize independent transactions, hold the first
real order lock using a test-only AOP probe, and observe MySQL
`performance_schema.data_lock_waits` before releasing it. The probe records the
distinct connection IDs and EntityManager identities. Root access is used only
inside the owned container to read lock-wait metadata.

The refund concurrency test deliberately overlaps both calls inside the Payment
mock. It characterizes two remote dispatches with the same return identity and
tests local convergence when Payment deduplicates. This does **not** prove that
real money is refunded only once. Payment ledger/idempotency remains an external
contract; Order has no local refund-ledger table in this flow. Do not interpret
a green characterization test as proof of Order-side single-dispatch safety.

Controlled failure probes execute and flush the actual outbox/persistence code
before throwing. They do not mock a repository or transaction manager. The
refund rollback test explicitly retains the mocked external success and checks
that lookup replay can reconcile the failed local write.

Results: `target/failsafe-reports`. Datasource, committed-state and concurrency
evidence: `target/return-refund-evidence`. Passwords are omitted. Copy these
before a later `clean` run. Docker/image/migration/context failure must be
reported as blocked/unverified business scenarios, not as a successful suite.
