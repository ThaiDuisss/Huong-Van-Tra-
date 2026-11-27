"""Official Ingredient report lifecycle and archived BOM research artifact checks."""
from copy import deepcopy
from datetime import date, datetime
from io import BytesIO
import hashlib
import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4
from zipfile import ZipFile

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from openpyxl import load_workbook
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.api.ai_reports import router, get_business_report_service
from app.core.ai_report_auth import AI_REPORT_ROLES
from app.database.session import get_db
from app.models.ai_report import AiReport
from app.models.business_report import BusinessReportExport
from app.schemas.business_report import BusinessExportRequest
from app.service.business_report_service import BusinessReportService
from app.service.bom_report_source import BomExportDataError, build_bom, select_bom_source
from app.ingredient_poc.recommendation.model import IngredientRecommender
from app.ingredient_demo.workbook import SHEETS, WORKBOOK_SCHEMA, table_rows
from tests.test_ai_reports import A, B, SECRET, signed
from tests.test_business_reports import DbFixture, GeneratedFiles
from tests.test_recommendation_modes_security import settings

CONFIG = os.environ.get("HVTRA_INGREDIENT_TEST_CONFIG")
LEGACY_SOURCE = {"kind": "SYNTHETIC_BOM_RESEARCH", "source_kind": "SYNTHETIC", "research_only": True,
          "production_approved": False, "dataset_id": "fixture-dataset", "dataset_sha256": "a" * 64,
          "model_sha256": "b" * 64, "model_fingerprint": "c" * 64, "model_version": "ingredient-research-v1",
          "protocol": {"name": "BOM_TEST_TOP5_REPEAT_V1", "mode": "CUSTOM_BOM", "policy": "REPEAT", "top_k": 5,
                       "as_of": "2026-04-01T00:00:00+00:00", "label_end_exclusive": "2026-05-01T00:00:00+00:00"}}


SOURCE = {"kind": "OPERATIONAL", "version": "hvtra-ingredient-report-v1"}
INGREDIENT_EVIDENCE = {"report_type": "INGREDIENT", "report_source": SOURCE, "data_status": "OK"}


class OfficialIngredientLifecycleTests(DbFixture, unittest.TestCase):
    def setUp(self):
        super().setUp()
        AiReport.__table__.create(self.engine)
        self.files = GeneratedFiles()
        self.service = BusinessReportService(self.files)
        self.app = FastAPI(); self.app.include_router(router)
        self.app.dependency_overrides[get_db] = lambda: self.db
        self.app.dependency_overrides[get_business_report_service] = lambda: self.service
        self.cfg = settings(ai_report_data_source="OPERATIONAL")
        for target, value in (("app.core.ai_report_auth.get_settings", SimpleNamespace(internal_header_secret=SECRET)),
                              ("app.service.business_report_service.get_settings", self.cfg),
                              ("app.service.business_report_service.build_ingredient_workbook",
                               (b"isolated frozen Ingredient bytes", INGREDIENT_EVIDENCE))):
            context = patch(target, return_value=value); context.start(); self.addCleanup(context.stop)
        self.client = TestClient(self.app); self.addCleanup(self.client.close)
        self.base = "/api/recommendation/ai-reports/exports"
        self.payload = {"reportType": "INGREDIENT", "requestId": str(uuid4()),
                        "fromDate": "2026-04-01", "toDate": "2026-04-30"}

    def export(self, payload=None, headers=None):
        return self.client.post(self.base, json=payload or self.payload, headers=headers if headers is not None else signed())

    def test_official_ingredient_uses_same_authenticated_route_and_durable_receipt(self):
        with patch("app.service.business_report_service.build_workbook", side_effect=AssertionError("No Product data")):
            r = self.export(); self.assertEqual(r.status_code, 200)
        receipt = r.json()
        self.assertEqual(receipt["reportType"], "INGREDIENT")
        self.assertEqual(receipt["createdBy"], A)
        self.assertEqual(receipt["status"], "COMPLETE")
        self.assertEqual(receipt["sourceKind"], "OPERATIONAL")
        self.assertEqual(receipt["dataStatus"], "OK")
        self.assertEqual(receipt["provenance"], SOURCE)
        self.assertEqual(receipt["fileName"], "AI_Report_HuongVanTra_20260401_20260430.xlsx")
        self.assertEqual(receipt, self.export().json())
        row = self.db.get(BusinessReportExport, receipt["id"])
        self.assertEqual(row.created_by, A)
        self.assertEqual(row.request_key, self.payload["requestId"])
        self.assertEqual(len(self.files.records), 1)
        self.assertEqual(self.db.scalar(select(func.count()).select_from(AiReport)), 0)
        # Established staff-sharing policy, not anonymous/owner-free public storage access.
        response = self.client.get(self.base + "/" + receipt["id"] + "/download", headers=signed(B))
        self.assertEqual(response.status_code, 200)
        self.assertEqual(hashlib.sha256(response.content).hexdigest(), receipt["sha256"])

    def test_all_official_roles_and_no_jwt_unsigned_forged_context_or_service_denied(self):
        receipt = self.export().json()
        for role in AI_REPORT_ROLES:
            headers = signed(roles=role)
            self.assertEqual(self.client.get(self.base + "/" + receipt["id"], headers=headers).status_code, 200)
        cases = [({}, 401), ({"Authorization": "Bearer untrusted-client-token"}, 401),
                 (signed(roles="CUSTOMER"), 403), (signed("service:recommendation-service", "SYSTEM"), 401)]
        missing_jwt = signed(); missing_jwt.pop("Authorization"); cases.append((missing_jwt, 401))
        forged = signed(); forged["X-User_Roles"] = "ADMIN"; cases.append((forged, 401))
        demo = {"Authorization": "Bearer private-demo-fixture"}; cases.append((demo, 401))
        for headers, status in cases:
            for method, path, payload in (("POST", self.base, self.payload),
                                          ("GET", self.base + "/" + receipt["id"], None),
                                          ("GET", self.base + "/" + receipt["id"] + "/download", None)):
                with self.subTest(method=method, status=status):
                    response = self.client.request(method, path, json=payload, headers=headers)
                    self.assertEqual(response.status_code, status)
                    self.assertNotIn("private-demo-fixture", response.text)

    def test_schema_rejects_unknown_type_period_and_client_artifact_or_identity_fields(self):
        self.assertEqual(BusinessExportRequest(requestId=uuid4(), fromDate="2026-04-01", toDate="2026-04-30").reportType, "PRODUCT")
        for extra in ({"reportType": "UNIFIED"}, {"fromDate": None},
                      {"fromDate": "2026-11-19", "toDate": "2026-11-18"},
                      {"dataset_path": "C:/untrusted.json"}, {"model_sha256": "0" * 64}, {"ownerId": B}, {"roles": "ADMIN"}):
            self.assertEqual(self.export(dict(self.payload, **extra)).status_code, 422)

    def test_same_request_id_cannot_change_product_ingredient_type_even_when_period_matches(self):
        receipt = self.export().json()
        changed = dict(self.payload, reportType="PRODUCT", fromDate="2026-04-01", toDate="2026-04-30")
        self.assertEqual(self.export(changed).status_code, 409)
        self.assertEqual(self.export().json()["id"], receipt["id"])
        other = self.export(headers=signed(B)).json()
        self.assertNotEqual(other["id"], receipt["id"])
        self.assertEqual(other["createdBy"], B)

    def test_legacy_period_is_authenticated_and_ingredient_dates_reach_builder(self):
        path = self.base + "/bom-period"
        self.assertEqual(self.client.get(path).status_code, 401)
        self.assertEqual(self.client.get(path, headers=signed(roles="CUSTOMER")).status_code, 403)
        self.assertEqual(self.client.get(path, headers=signed()).json(), {"fromDate": "2026-04-01", "toDate": "2026-04-30"})
        selected = dict(self.payload, fromDate="2026-05-07", toDate="2026-05-07")
        with patch("app.service.business_report_service.build_ingredient_workbook",
                   return_value=(b"filtered", dict(INGREDIENT_EVIDENCE, data_status="NO_DATA"))) as builder:
            result = self.export(selected)
            self.assertEqual(result.status_code, 200)
            self.assertEqual(result.json()["fileName"], "AI_Report_HuongVanTra_20260507_20260507.xlsx")
            self.assertEqual(result.json()["dataStatus"], "NO_DATA")
            self.assertEqual(builder.call_args.args[:4], (self.db, self.cfg, date(2026, 5, 7), date(2026, 5, 7)))
        self.assertEqual(self.export(dict(selected, toDate="2026-05-08")).status_code, 409)

    def test_legacy_product_response_and_generator_remain_unchanged(self):
        payload = dict(requestId=str(uuid4()), fromDate="2026-04-01", toDate="2026-04-30")
        with patch("app.service.business_report_service.build_ingredient_workbook", side_effect=AssertionError("No Ingredient route")), \
             patch("app.service.bom_report_source.load_research", side_effect=AssertionError("No eager research load")):
            response = self.export(payload)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(set(response.json()), {"id", "fileId", "fileName", "fromDate", "toDate", "generatedAt", "sha256"})
        self.assertEqual(self.export(dict(payload, reportType="CUSTOM_BOM")).status_code, 409)

    def test_retry_after_storage_timeout_or_process_restart_reuses_frozen_bytes(self):
        self.files.fail = True
        self.assertEqual(self.export().status_code, 502)
        record = self.db.scalar(select(BusinessReportExport)); frozen = record.workbook
        self.assertEqual(record.status, "PREPARED")
        self.files.fail = False
        self.db.close()
        self.db = Session(self.engine, expire_on_commit=False)
        self.addCleanup(self.db.close)
        self.service = BusinessReportService(self.files)
        with patch("app.service.business_report_service.build_ingredient_workbook", side_effect=AssertionError("Must not regenerate")):
            response = self.export()
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.db.get(BusinessReportExport, response.json()["id"]).workbook, frozen)
        self.assertEqual(len(self.files.records), 1)

    def test_remote_success_local_commit_failure_recovers_same_ingredient_file(self):
        commit = self.db.commit
        calls = 0
        def fail_finalization():
            nonlocal calls
            calls += 1
            if calls == 3:
                raise RuntimeError("Isolated local finalization failure")
            commit()
        with patch.object(self.db, "commit", side_effect=fail_finalization):
            with self.assertRaises(RuntimeError):
                self.service.export(self.db, BusinessExportRequest(**self.payload), A)
        self.assertEqual(self.export().status_code, 200)
        self.assertEqual(len(self.files.records), 1)

    def test_new_legacy_reports_are_retired_before_persistence(self):
        with patch("app.service.business_report_service.build_workbook", side_effect=AssertionError("No fallback")):
            response = self.export(dict(self.payload, reportType="CUSTOM_BOM"))
        self.assertEqual(response.status_code, 410)
        self.assertEqual(len(self.files.records), 0)
        self.assertEqual(self.db.scalar(select(func.count()).select_from(BusinessReportExport)), 0)

    def test_archived_prepared_bom_retries_frozen_bytes(self):
        legacy = dict(self.payload, reportType="CUSTOM_BOM")
        workbook = b"previously frozen workbook"
        export_id = str(uuid4())
        digest = hashlib.sha256(workbook).hexdigest()
        self.db.add(BusinessReportExport(
            id=export_id, created_by=A, request_key=legacy["requestId"], from_date=date(2026, 4, 1),
            to_date=date(2026, 4, 30), generated_at=datetime(2026, 4, 30), status="PREPARED",
            file_name="AI_Report_HuongVanTra_20260401_20260430.xlsx", workbook=workbook, sha256=digest,
            evidence={"report_type": "CUSTOM_BOM", "report_source": LEGACY_SOURCE},
        ))
        self.db.commit()
        receipt = self.export(legacy).json()
        self.assertEqual(receipt["sha256"], digest)
        self.assertEqual(receipt["reportType"], "CUSTOM_BOM")
        self.assertTrue(receipt["researchOnly"])
        self.assertEqual(self.export(legacy).json(), receipt)
        response = self.client.get(f"{self.base}/{export_id}/download", headers=signed(B))
        self.assertEqual(response.content, workbook)


@unittest.skipUnless(CONFIG, "Configure pinned external Phase 2/3 artifacts")
class OfficialBomArtifactTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        config = json.loads(Path(CONFIG).read_text(encoding="utf-8"))
        cls.cfg = settings(ai_report_bom_enabled=True, **{"ai_report_bom_" + k: v for k, v in config.items()})
        cls.guard = patch.object(IngredientRecommender, "fit", side_effect=AssertionError("Never refit"))
        cls.guard.start()
        cls.source = select_bom_source(cls.cfg)
        cls.content, cls.evidence = build_bom(cls.cfg, cls.source)

    @classmethod
    def tearDownClass(cls):
        cls.guard.stop()

    def test_workbook_protocol_and_reference_results_with_customer_lookup(self):
        workbook = load_workbook(BytesIO(self.content))
        self.assertEqual(workbook.sheetnames, SHEETS)
        self.assertNotIn("ĐỐI CHIẾU KẾT QUẢ GỢI Ý", workbook.sheetnames)
        self.assertEqual(len(workbook.sheetnames), 5)
        self.assertEqual(self.evidence["workbook_schema"], WORKBOOK_SCHEMA)
        self.assertEqual(self.evidence["workbook_evidence"]["ingredient_lines"], 348)
        summary = self.evidence["summary"]
        self.assertEqual((summary["customers"], summary["recommendations"], summary["matches"]), (400, 2000, 62))
        self.assertEqual(summary["match_rate"], 0.031)
        self.assertEqual(summary["coverage"], 79 / 80)
        self.assertEqual(self.source["protocol"]["mode"], "CUSTOM_BOM")
        self.assertTrue(self.source["research_only"])
        self.assertTrue(any("research_only=true" in str(row[0]) for row in workbook.worksheets[0].values))
        self.assertNotIn("path", json.dumps(self.source))
        statistics = list(table_rows(workbook.worksheets[2]))
        self.assertEqual(sum(r[3] for r in statistics), 2000)
        self.assertEqual(sum(r[5] for r in statistics), 62)
        # Existing File-Service contract accepts generic generated XLSX, not Product sheet names.
        with ZipFile(BytesIO(self.content)) as archive:
            self.assertIsNone(archive.testzip())
            self.assertTrue({"[Content_Types].xml", "_rels/.rels", "xl/workbook.xml"} <= set(archive.namelist()))
            self.assertLessEqual(len(archive.infolist()), 512)
            self.assertLessEqual(sum(i.file_size for i in archive.infolist()), max(1024 * 1024, len(self.content) * 100))
            self.assertFalse(any(i.filename.endswith("vbaProject.bin") for i in archive.infolist()))

    def test_wrong_hash_missing_artifact_and_repin_fail_sanitized(self):
        for change in ({"ai_report_bom_model_sha256": "0" * 64}, {"ai_report_bom_dataset_id": "not-this-dataset"},
                       {"ai_report_bom_dataset_path": "relative.json"}, {"ai_report_bom_model_path": "C:/nonexistent-private.json"}):
            cfg = self.cfg.model_copy(update=change)
            with self.assertRaises(HTTPException) as failure:
                select_bom_source(cfg)
            self.assertEqual(failure.exception.status_code, 503)
            self.assertNotIn("private.json", failure.exception.detail)
        other = deepcopy(self.source); other["model_sha256"] = "d" * 64
        with self.assertRaises(HTTPException) as failure:
            build_bom(self.cfg, other)
        self.assertEqual(failure.exception.status_code, 409)

    def test_official_adapter_rejects_content_missing_recipe_catalog(self):
        workbook = load_workbook(BytesIO(self.content))
        del workbook[SHEETS[3]]
        stream = BytesIO(); workbook.save(stream)
        with patch("app.service.bom_report_source.build_workbook", return_value=stream.getvalue()):
            with self.assertRaises(HTTPException) as failure:
                build_bom(self.cfg, self.source)
        self.assertEqual(failure.exception.status_code, 503)
        self.assertEqual(failure.exception.detail, "BOM research workbook verification failed")

    def test_two_research_periods_use_the_same_prediction_baseline(self):
        workbooks = []
        for start, end in ((date(2026, 4, 1), date(2026, 4, 15)), (date(2026, 4, 16), date(2026, 4, 30))):
            content, _ = build_bom(self.cfg, self.source, start, end)
            workbook = load_workbook(BytesIO(content))
            self.addCleanup(workbook.close)
            self.assertIn(start.strftime("%d/%m/%Y"), workbook[SHEETS[0]]["A2"].value)
            self.assertIn(end.strftime("%d/%m/%Y"), workbook[SHEETS[0]]["A2"].value)
            workbooks.append(workbook)
        first, last = workbooks
        first_rows, last_rows = [list(table_rows(wb[SHEETS[2]])) for wb in workbooks]
        self.assertNotEqual([row[4:] for row in first_rows], [row[4:] for row in last_rows])
        self.assertEqual([row[:4] for row in first_rows], [row[:4] for row in last_rows])
        self.assertEqual(list(table_rows(first[SHEETS[4]])), list(table_rows(last[SHEETS[4]])))

    def test_covered_research_day_without_purchases_does_not_emit_predictions(self):
        day = date(2026, 4, 30)
        with patch("app.service.bom_report_source.build_workbook") as workbook:
            with self.assertRaises(BomExportDataError) as failure:
                build_bom(self.cfg, self.source, day, day)
            self.assertEqual(failure.exception.status_code, 422)
            self.assertEqual(failure.exception.code, "BOM_NO_DATA")
            workbook.assert_not_called()
