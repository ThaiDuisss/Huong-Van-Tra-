"""Dry-run only. There is deliberately no production persistence mode.

From the service directory, use the service virtualenv:
    python -B scripts/import_historical_report_purchases.py
    python -B scripts/import_historical_report_purchases.py --diagnostic-assume-utc

Both commands read recommend.env explicitly, close a READ ONLY MySQL snapshot,
then use SQLite memory. Reports use the external root configured in AGENTS.md.
Diagnostic evidence is never verified business evidence. This CLI cannot write
MySQL, initialize schema, train a model or dispatch a Kafka event.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys

SERVICE_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = SERVICE_ROOT.parents[1]
sys.path.insert(0, str(SERVICE_ROOT))


def parser():
    result = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    result.add_argument("--dry-run", action="store_true", help="Explicit spelling of the only supported mode")
    result.add_argument("--strict", action="store_true", help="Strict policy is always applied")
    result.add_argument("--diagnostic-assume-utc", action="store_true",
                        help="Counterfactual SQLite simulation only; never verifies source timezone")
    return result


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
        from app.service.offline_purchase_adapter import analyze_historical_source, DIAGNOSTIC_WARNING
        from app.service.offline_purchase_workbook import verify_sales_only_workbook
        stage = "configuration"
        settings = Settings(_env_file=SERVICE_ROOT / "recommend.env")
        stage = "read_only_source"
        source = read_historical_source(settings)
        # Reader closes/rolls back MySQL before any simulation or SQLite DDL.
        stage = "analysis"
        run = analyze_historical_source(source, diagnostic_assume_utc=arguments.diagnostic_assume_utc)
        cohort = [d.evidence for d in run.decisions if d.evidence and d.category in
                  (("READY", "NOT_VERIFIED") if arguments.diagnostic_assume_utc else ("READY",))]
        stage = "sqlite_workbook"
        workbook = verify_sales_only_workbook(cohort)
        stage = "external_report"
        report = save_result(run.summary, workbook, source.metadata)
        if arguments.diagnostic_assume_utc:
            print(DIAGNOSTIC_WARNING, file=sys.stderr)
        print(json.dumps(dict(summary=run.summary, workbook=workbook, report=str(report)),
                         default=str, ensure_ascii=False))
        return 0  # Zero READY is a valid dry-run outcome, not an error.
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
