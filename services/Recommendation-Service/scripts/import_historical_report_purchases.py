"""Historical purchase reconciliation CLI (dry-run by default; controlled --apply with backup).

From the service directory, use the service virtualenv:
    python -B scripts/import_historical_report_purchases.py
    python -B scripts/import_historical_report_purchases.py --diagnostic-assume-utc
    python -B scripts/import_historical_report_purchases.py --apply

Default dry-run reads a READ ONLY MySQL snapshot, then uses SQLite memory.
Controlled --apply writes a pre-apply backup first, enforces strict READY +
VerificationPolicy gates, runs a single atomic MySQL transaction, and rolls back
on any identity, hash, line, or negative net quantity conflict.
"""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import sys

SERVICE_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = SERVICE_ROOT.parents[1]
DEFAULT_BACKUP_DIR = Path(r"D:\Huong-Van-Tra-Backup\AI-Recommendation\Level-2\backups")
sys.path.insert(0, str(SERVICE_ROOT))


def parser():
    result = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    result.add_argument("--dry-run", action="store_true", help="Read-only analysis and SQLite simulation")
    result.add_argument("--apply", action="store_true", help="Controlled transactional backfill of strictly READY orders")
    result.add_argument("--strict", action="store_true", help="Strict policy is always applied")
    result.add_argument("--diagnostic-assume-utc", action="store_true",
                        help="Counterfactual SQLite simulation only; never verifies source timezone")
    result.add_argument("--policy-json", type=Path, default=None,
                        help="Optional JSON file containing verified provenance/timezone/unit/usage policy")
    result.add_argument("--backup-dir", type=Path, default=DEFAULT_BACKUP_DIR,
                        help="External directory for pre-apply backup snapshots")
    return result


def load_policy(policy_path):
    from app.service.offline_purchase_adapter import VerificationPolicy
    if policy_path is None:
        return VerificationPolicy()
    raw = json.loads(Path(policy_path).read_text(encoding="utf-8"))
    tz = timezone.utc if raw.get("source_timezone") == "UTC" else None
    return VerificationPolicy(
        source_timezone=tz,
        timezone_evidence=str(raw.get("timezone_evidence") or ""),
        provenance_evidence=str(raw.get("provenance_evidence") or ""),
        unit_contract_evidence=str(raw.get("unit_contract_evidence") or ""),
        usage_approval=str(raw.get("usage_approval") or ""),
    )


def write_pre_apply_backup(settings, backup_dir: Path) -> Path:
    import pymysql
    backup_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = backup_dir / f"pre-apply-recommend_app-{stamp}.json"
    conn = pymysql.connect(
        host=settings.mysql_host,
        port=settings.mysql_port,
        user=settings.mysql_user,
        password=settings.mysql_password.get_secret_value(),
        database=settings.mysql_database,
        charset="utf8mb4",
        connect_timeout=5,
        read_timeout=15,
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SET SESSION TRANSACTION READ ONLY")
            cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            snapshot = {"captured_at_utc": datetime.now(timezone.utc).isoformat(), "tables": {}}
            for table in ("order_event_streams", "order_event_inbox", "report_purchase_orders", "order_return_lines"):
                cur.execute(f"SELECT * FROM {table}")
                snapshot["tables"][table] = cur.fetchall()
            conn.rollback()
    finally:
        conn.close()
    out.write_text(json.dumps(snapshot, default=str, ensure_ascii=False, indent=2), encoding="utf-8")
    return out


def save_result(summary, workbook, metadata):
    """Use the project report helper; no project-local output or overwrite fallback."""
    spec = importlib.util.spec_from_file_location("codex_save_report", PROJECT_ROOT / "scripts/codex/save_report.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    payload = dict(scope="Offline historical purchase importer: dry-run only, MySQL READ ONLY and isolated SQLite workbook.",
        changes="No MySQL writes, schema initialization, Kafka or external service calls. Runtime report only.",
        tests="Read-only source reconciliation, deterministic simulation and existing workbook verification.",
        issues="Provenance, timezone, UOM and usage approval remain unverified; n_lines mismatches are held for review.",
        modified_files=[], details="## Dry-run result\n\n```json\n" + json.dumps(
            dict(summary=summary, workbook=workbook, source_metadata=metadata), default=str,
            ensure_ascii=False, indent=2) + "\n```\n")
    report = helper.save_report("offline-historical-purchase-dry-run", payload)
    # Exclusive sidecar; the helper has already verified the external directory.
    with report.with_suffix(".json").open("x", encoding="utf-8") as output:
        json.dump(dict(summary=summary, workbook=workbook, source_metadata=metadata), output,
                  default=str, ensure_ascii=False, indent=2)
    return report


def main(argv=None):
    arguments = parser().parse_args(argv)
    stage = "imports"
    # Explicitly disable initialization before importing any application modules.
    os.environ.update(SPRING_FLYWAY_ENABLED="false", SPRING_SQL_INIT_MODE="never",
                      STORAGE_INITIALIZATION_ENABLED="false", PYTHONDONTWRITEBYTECODE="1")
    try:
        from app.core.settings import Settings
        from app.repositories.historical_purchase_source import read_historical_source
        from app.service.offline_purchase_adapter import (
            DIAGNOSTIC_WARNING, analyze_historical_source, apply_historical_purchases,
        )
        from app.service.offline_purchase_workbook import verify_sales_only_workbook
        stage = "configuration"
        settings = Settings(_env_file=SERVICE_ROOT / "recommend.env")
        policy = load_policy(arguments.policy_json)
        stage = "read_only_source"
        source = read_historical_source(settings, timeout_ms=25000)
        # Reader closes/rolls back MySQL before any simulation or SQLite DDL.
        stage = "analysis"
        run = analyze_historical_source(
            source,
            diagnostic_assume_utc=arguments.diagnostic_assume_utc and not arguments.apply,
            policy=policy,
        )
        cohort = [d.evidence for d in run.decisions if d.evidence and d.category in
                  (("READY", "NOT_VERIFIED") if (arguments.diagnostic_assume_utc and not arguments.apply) else ("READY",))]
        stage = "sqlite_workbook"
        workbook = verify_sales_only_workbook(cohort)
        apply_result = None
        if arguments.apply:
            stage = "pre_apply_backup"
            backup_path = write_pre_apply_backup(settings, arguments.backup_dir)
            stage = "apply_transaction"
            from sqlalchemy import create_engine
            from sqlalchemy.orm import Session
            engine = create_engine(settings.sqlalchemy_database_url, pool_pre_ping=True)
            try:
                with Session(engine) as db:
                    apply_result = apply_historical_purchases(db, run, policy=policy)
            finally:
                engine.dispose()
            apply_result["backup_path"] = str(backup_path)
        stage = "external_report"
        report = save_result(run.summary, workbook, source.metadata)
        if arguments.diagnostic_assume_utc and not arguments.apply:
            print(DIAGNOSTIC_WARNING, file=sys.stderr)
        payload = dict(summary=run.summary, workbook=workbook, report=str(report))
        if apply_result is not None:
            payload["apply"] = apply_result
        print(json.dumps(payload, default=str, ensure_ascii=False))
        return 0  # Zero READY is a valid strict outcome, not an error.
    except Exception as error:
        # Driver/parser exceptions can contain credentials or raw IDs. No traceback.
        import traceback
        locations = [dict(file=Path(frame.filename).name, line=frame.lineno, function=frame.name)
                     for frame in traceback.extract_tb(error.__traceback__)]
        print(json.dumps(dict(status="DRY_RUN_FAILED", stage=stage, error_type=type(error).__name__,
                              code=getattr(error, "code", "configuration_or_simulation_failure"),
                              locations=locations,
                              message="No production writes; inspect source bounds/config or isolated tests.")), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
