"""Exercise the real standalone ASGI API and export evidence outside the project."""
import argparse
from datetime import datetime
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import secrets
import sys
import traceback
from unittest.mock import patch

SERVICE = Path(__file__).resolve().parents[1]
PROJECT = SERVICE.parents[1]
sys.path.insert(0, str(SERVICE))


def guard(event, args):
    if event == "open" and isinstance(args[0], (str, bytes)) and Path(os.fsdecode(args[0])).name == "evaluation.json":
        raise RuntimeError("Latent sidecar access is forbidden")
    if event in ("subprocess.Popen", "os.system"):
        raise RuntimeError("Subprocess startup is disabled for verification")
    if event == "socket.connect":
        internal = any(f.name == "_fallback_socketpair" and f.filename.endswith("socket.py")
                       for f in traceback.extract_stack())
        if not (internal and args[1][0] in ("127.0.0.1", "::1") and args[1][1] >= 49152):
            raise RuntimeError("Outbound services are disabled for verification")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--reference-metrics", type=Path, required=True)
    parser.add_argument("--reference-metrics-sha256", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    if output == PROJECT or PROJECT in output.parents:
        parser.error("output-dir must be outside the project")
    output.mkdir(parents=True, exist_ok=False)
    os.environ.update(SPRING_FLYWAY_ENABLED="false", SPRING_SQL_INIT_MODE="never",
                      SPRING_JPA_HIBERNATE_DDL_AUTO="none", STORAGE_INITIALIZATION_ENABLED="false",
                      PYTHONDONTWRITEBYTECODE="1")
    sys.addaudithook(guard)
    from fastapi.testclient import TestClient
    from openpyxl import load_workbook
    from app.ingredient_demo.api import create_app
    from app.ingredient_demo.settings import IngredientDemoSettings
    from app.ingredient_demo.workbook import SHEETS, WORKBOOK_SCHEMA, table_rows, verify_workbook
    from app.ingredient_poc.recommendation.evaluation import demos
    from app.ingredient_poc.recommendation.model import IngredientRecommender

    config = json.loads(args.config.read_text(encoding="utf-8"))
    allowed = {"dataset_path", "dataset_sha256", "dataset_id", "model_path", "model_sha256"}
    if set(config) != allowed:
        raise ValueError("config must contain only the five explicit artifact identity fields")
    secret = secrets.token_urlsafe(48)  # Verification credential is never persisted or printed.
    settings = IngredientDemoSettings(**config, demo_enabled=True, demo_environment="test", demo_token=secret)

    def write_json(name, value):
        with (output / name).open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, ensure_ascii=True, sort_keys=True, indent=2)

    with patch.object(IngredientRecommender, "fit", side_effect=AssertionError("Refit is forbidden")):
        with TestClient(create_app(settings)) as client:
            unauthorized = client.get("/api/v1/ingredient-demo/customers")
            assert unauthorized.status_code == 401
            client.headers.update({"Authorization": "Bearer " + secret})
            catalog = client.get("/api/v1/ingredient-demo/customers")
            assert catalog.status_code == 200 and len(catalog.json()["customers"]) == 400
            write_json("customers.json", catalog.json())
            service = client.app.state.ingredient_service
            # Select cohorts from real history and execute the model, never read demos.json.
            expected = demos(service._model)
            responses = {}
            for cohort, value in expected.items():
                params = {key: value[key] for key in ("customer_id", "as_of", "mode", "policy")}
                response = client.get("/api/v1/ingredient-demo/recommendations", params=params)
                assert response.status_code == 200
                responses[cohort] = response.json()
                assert responses[cohort]["recommendations"] == value["recommendations"]
                assert responses[cohort]["returned_k"] == 5
            write_json("api-demos.json", responses)
            snapshot = service.report_snapshot()
            reference = args.reference_metrics.read_bytes()
            assert hashlib.sha256(reference).hexdigest() == args.reference_metrics_sha256
            # Pin the original artifact as a preservation check, not as this report's metrics.
            assert json.loads(reference)["dataset_id"] == config["dataset_id"]
            assert snapshot["protocol"]["mode"] == "CUSTOM_BOM"
            assert snapshot["protocol"]["phase3_original_metrics"] is False
            write_json("bom-report-protocol.json", snapshot["protocol"])
            write_json("bom-report-summary.json", snapshot["summary"])
            write_json("evaluation-records.json", snapshot["evaluation"])
            write_json("all-customer-recommendations.json", snapshot["predictions"])
            response = client.get("/api/v1/ingredient-demo/reports/excel")
            assert response.status_code == 200
            workbook = load_workbook(BytesIO(response.content))
            assert workbook.sheetnames == SHEETS
            workbook_evidence = verify_workbook(workbook, service.data, snapshot)
            assert [len(list(table_rows(s))) for s in workbook.worksheets[1:]] == [400, 80, 400, 348]
            comparison = list(table_rows(workbook.worksheets[3]))
            stats = list(table_rows(workbook.worksheets[2]))
            assert sum(r[2] for r in comparison) == sum(r[3] for r in stats) == snapshot["summary"]["recommendations"]
            assert sum(r[4] for r in comparison) == sum(r[5] for r in stats) == snapshot["summary"]["matches"]
            for sheet in workbook:
                assert sheet.freeze_panes is None and sheet.sheet_view.showGridLines
                for row in sheet:
                    for cell in row:
                        assert cell.data_type not in ("e", "f")
                        if isinstance(cell.value, datetime):
                            assert cell.number_format.lower() == "dd/mm/yyyy"
                        if cell.number_format == "0.00%":
                            assert isinstance(cell.value, (int, float)) and 0 <= cell.value <= 1
            filename = "Ingredient_Recommendation_Research_20260401.xlsx"
            with (output / filename).open("xb") as stream:
                stream.write(response.content)
            assert response.content == client.get("/api/v1/ingredient-demo/reports/excel").content
    forbidden = {"app.main", "app.database.session", "pymysql", "confluent_kafka", "redis"}
    assert not forbidden.intersection(sys.modules), "Production runtime leaked into the standalone app"
    result = {"status": "PASS", "dataset_id": config["dataset_id"], "artifact_identities": config,
              "workbook_schema": WORKBOOK_SCHEMA, "workbook_evidence": workbook_evidence,
              "sheet_data_rows": {s.title: len(list(table_rows(s))) for s in workbook},
              "all_customers": 400, "bom_recommendations": snapshot["summary"]["recommendations"],
              "protocol": snapshot["protocol"], "summary": snapshot["summary"],
              "phase3_reference_sha_verified": True, "phase3_metrics_identical": False,
              "refit_called": False, "production_runtime_modules_loaded": [],
              "outbound_network_and_latent_sidecar_guard": True,
              "demo_returned_k": {name: row["returned_k"] for name, row in responses.items()}}
    write_json("verification.json", result)
    write_json("checksums.json", {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in sorted(output.iterdir()) if p.is_file()})
    print(json.dumps({"status": "PASS", "output_dir": str(output), "demo_returned_k": result["demo_returned_k"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
