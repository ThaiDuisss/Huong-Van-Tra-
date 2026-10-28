"""Exclusive external artifacts and the repository-required Markdown report helper."""
from dataclasses import asdict
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

from app.recommendation.v1_model import CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT
from app.service.recommendation_service import MIN_HYBRID_PURCHASE_ORDERS
from offline_benchmark.evaluation.contract import CATALOG_POLICY, K, MODE, PROTOCOL, customer_reference
from offline_benchmark.evaluation.runner import evaluate, freeze_predictions
from offline_benchmark.evaluation.v1_state import build_opportunities
from offline_benchmark.reporting.evaluation_workbook import export_workbook, verify_workbook

SERVICE = Path(__file__).resolve().parents[2]
PROJECT = SERVICE.parents[1]
REPORT_HELPER = PROJECT / "scripts/codex/save_report.py"
BACKUP_ROOT = Path("E:/Huong-Van-Tra-Backup/codex-reports")
LOCAL = timezone(timedelta(hours=7), "Asia/Bangkok")


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def protected_hashes():
    files = []
    for directory in ("app", "artifacts", "artifacts-demo", "flyway", "offline_benchmark/outputs"):
        files.extend(p for p in (SERVICE / directory).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    files.extend(SERVICE / name for name in (".env", "recommend.env", "training.env") if (SERVICE / name).is_file())
    return {str(p.relative_to(SERVICE)): sha256(p.read_bytes()) for p in sorted(files)}


def report_helper():
    spec = importlib.util.spec_from_file_location("offline_report_storage_policy", REPORT_HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    return helper


def allocate_run(output_root, now):
    helper = report_helper()
    base = helper.verify_destination(Path(output_root))
    day = helper.verify_destination(base / now.astimezone(LOCAL).strftime("%Y-%m-%d"))
    day.mkdir(parents=True, exist_ok=True)
    for suffix in range(100000):
        stem = now.astimezone(LOCAL).strftime("%H%M%S") + "_offline-evaluation-v1"
        candidate = helper.verify_destination(day / (stem if suffix == 0 else f"{stem}-{suffix+1}"))
        try:
            candidate.mkdir(exist_ok=False)
            return candidate
        except FileExistsError:
            continue
    raise ValueError("Cannot allocate exclusive offline output directory")


def write_artifacts(directory, artifacts):
    checksums = {}
    for name, content in artifacts.items():
        if Path(name).name != name:
            raise ValueError("Artifact must have a plain filename")
        with (directory / name).open("xb") as output:
            output.write(content)
        checksums[name] = sha256(content)
    with (directory / "checksums.sha256").open("x", encoding="utf-8") as output:
        output.write("".join(f"{checksum}  {name}\n" for name, checksum in checksums.items()))
    if any(sha256((directory / name).read_bytes()) != checksum for name, checksum in checksums.items()):
        raise ValueError("Saved artifact checksum verification failed")
    return checksums


def compute(dataset, executed_at):
    before = protected_hashes()
    opportunities = build_opportunities(dataset.splits)
    frozen = freeze_predictions(opportunities, dataset.products)
    # The complete frozen payload exists before evaluate receives any TEST labels.
    result = evaluate(frozen, dataset.splits, dataset.products)
    period = (min(e.event_time for e in dataset.snapshot.purchases).date(),
              max(e.event_time for e in dataset.snapshot.purchases).date())
    workbook = export_workbook(result, dataset.products, dataset.manifest, executed_at, period)
    verification = verify_workbook(workbook, result)
    if protected_hashes() != before:
        raise ValueError("Production inputs changed during offline computation")
    verification.update(protected_files_unchanged=True, protected_file_count=len(before),
                        canonical_manifest_reproduced=True, prediction_freeze_verified=True,
                        serving_artifact_loaded=False, serving_repository_used=False,
                        mysql_writes=0, snapshots_written=0, kafka_calls=0, file_service_calls=0)
    return result, workbook, verification, before


def export_run(dataset, output_root=BACKUP_ROOT, quantity=None):
    started = datetime.now(timezone.utc)
    result, workbook, verification, preserved = compute(dataset, started)
    directory = allocate_run(output_root, datetime.now(LOCAL))
    membership = [{"customer": customer_reference(s.customer_id),
                   "train_event_ids": [e.event_id for e in s.train],
                   "test_event_ids": [e.event_id for e in s.test],
                   "train_order_ids": sorted({e.order_id for e in s.train}),
                   "test_order_ids": sorted({e.order_id for e in s.test})} for s in dataset.splits]
    state_evidence = [{k:asdict(p)[k] for k in ("opportunity_ref", "customer", "cutoff", "state_sha256",
                        "state_events_sha256", "state_event_count", "state_order_count",
                        "future_orders_excluded", "spanning_orders_excluded")}
                      for p in result.frozen.predictions]
    artifacts = {
        "AI_Offline_Evaluation_HuongVanTra_TrainTest.xlsx": workbook,
        "metrics.json": json_bytes(result.metrics),
        "predictions.json": json_bytes({"protocol":PROTOCOL, "prediction_sha256":result.frozen.sha256,
                                          "predictions":[asdict(p) for p in result.frozen.predictions]}),
        "evaluation.json": json_bytes([asdict(r) for r in result.rows]),
        "membership.json": json_bytes(membership),
        "state-evidence.json": json_bytes(state_evidence),
        "verification.json": json_bytes(verification),
    }
    code_files = list((SERVICE / "offline_benchmark/evaluation").glob("*.py")) + list(
        (SERVICE / "offline_benchmark/reporting").glob("*.py"))
    code_files += [SERVICE / p for p in ("scripts/export_offline_evaluation.py",
                   "app/recommendation/v1_model.py", "app/service/recommendation_service.py",
                   "app/service/business_report_excel.py", "offline_benchmark/split/chronological_split.py",
                   "offline_benchmark/metrics/ranking_metrics.py")]
    revision = subprocess.run(["git", "-C", str(PROJECT), "rev-parse", "HEAD"],
                              capture_output=True, text=True, check=True).stdout.strip()
    import numpy, scipy, openpyxl
    manifest = {"protocol":PROTOCOL, "mode":MODE, "k":K, "source":"ARCHIVE",
                "source_path":dataset.snapshot_path, "source_archive_sha256":dataset.archive_sha256,
                "original_manifest_file_sha256":dataset.manifest_file_sha256,
                "original_split_manifest":dataset.manifest,
                "cutoff_policy":"completion MAX(event_time) of final own TRAIN order; full TRAIN orders only <= cutoff",
                "test_policy":"all canonical TEST excluded globally until prediction freeze; no online updates",
                "catalog_policy":CATALOG_POLICY, "source_timezone":"NOT_VERIFIED; original naive timestamps",
                "ingestion_availability":"NOT_VERIFIED; event_time only", "provenance":"NOT_VERIFIED",
                "quantity":quantity or {"status":"NOT_VERIFIED", "reason":"archive_lacks_quantity_variant_uom", "workbook_quantity":"N/A"},
                "model_contract":CONTRACT_VERSION, "feature_contract":FEATURE_CONTRACT,
                "normalization_contract":NORMALIZATION_CONTRACT,
                "weights":{"content":0.6,"cf":0.4}, "routing_min_hybrid_orders":MIN_HYBRID_PURCHASE_ORDERS,
                "execution_started_at":started.isoformat(), "execution_timezone":"Asia/Bangkok, UTC+07:00",
                "prediction_sha256":result.frozen.sha256,
                "code_revision":revision,
                "source_file_sha256":{str(p.relative_to(SERVICE)):sha256(p.read_bytes()) for p in sorted(code_files)},
                "dependencies":{"python":sys.version.split()[0],"numpy":numpy.__version__,
                                "scipy":scipy.__version__,"openpyxl":openpyxl.__version__},
                "protected_file_sha256":preserved,
                "artifact_sha256":{name:sha256(content) for name,content in artifacts.items()},
                "limitations":["retrospective catalog/BOM/status", "source timezone/provenance unverified",
                               "quantity N/A; no conversion of packaged units to cups", "Test previously used for research",
                               "not official revenue, exposure or conversion evidence"]}
    artifacts["manifest.json"] = json_bytes(manifest)
    checksums = write_artifacts(directory, artifacts)
    payload = {"scope":"Causal canonical Train-only V1 offline evaluation from verified ARCHIVE; four-sheet XLSX export.",
               "changes":"Created external offline artifacts only during this run. No MySQL/Kafka/File-Service/snapshot writes or production model refresh.",
               "tests":"Original manifest verified; frozen predictions validated; saved XLSX read back and KPI/product statistics reconciled; all output SHA-256 checks passed; protected files unchanged.",
               "issues":"Quantity N/A; source timezone/provenance and historical catalog availability unverified. Technical evaluation, not an operational sales report.",
               "modified_files":[],
               "details":"Output directory: " + str(directory) + "\n\nMetrics:\n\n~~~json\n" + json.dumps(result.metrics,ensure_ascii=False,indent=2) + "\n~~~\n\nWorkbook SHA-256: " + checksums['AI_Offline_Evaluation_HuongVanTra_TrainTest.xlsx']}
    report = subprocess.run([sys.executable,"-B",str(REPORT_HELPER),"--task","offline-evaluation-v1-run"],
                            input=json.dumps(payload,ensure_ascii=False),encoding="utf-8",capture_output=True)
    if report.returncode:
        raise OSError("Artifacts saved in " + str(directory) + "; required report helper failed: " + report.stderr.strip())
    return {"directory":str(directory), "workbook":str(directory / "AI_Offline_Evaluation_HuongVanTra_TrainTest.xlsx"),
            "report":report.stdout.strip(), "metrics":result.metrics, "checksums":checksums}
