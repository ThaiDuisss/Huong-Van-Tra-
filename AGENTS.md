# Repository migration rules

- Every newly added Flyway migration must use `VyyyyMMddHHmmss__description.sql`.
- Generate the actual creation timestamp in `Asia/Bangkok` (UTC+07:00); check existing
  versions in the service before creating the file. Never reuse a version. If needed,
  wait for the next second rather than inventing a timestamp.
- Historical migrations retain their original names and contents. Do not rewrite,
  rename, baseline or repair applied migrations to adopt this convention.
- New versions must follow the existing service history with `outOfOrder=false`.
  Check the live history separately: the naming checker does not connect to databases.
- Before review, run `python -B scripts/migrations/check_migration_naming.py` and
  `python -B -m unittest discover -s scripts/migrations -p "test_*.py"`.
  For a branch/PR, pass `--base-ref <base-commit>` so every migration added on the
  branch is checked. The default baseline is `HEAD`, for local uncommitted changes.
  `.github/workflows/migration-naming.yml` runs both checks against the PR base commit.
- The baseline allows existing historical names (including older numeric and
  two-digit-year versions); it is not permission to reuse those formats for new files.
- The checker also records the verified, already-applied File-Service V6 filename
  and checksum for genuine source recovery. That exception requires matching content;
  never fabricate a checksum-matching file or add exceptions for new migrations.
- Read-only runtime audits must disable Flyway, SQL initialization and storage
  initialization explicitly. Shared DB migrations and storage writes require user
  authorization; do not log environment secrets.

# Java conventions

Use standard imports and simple class names, not fully qualified names in fields,
variables or method signatures. JPQL constructor/type expressions are the exception.
