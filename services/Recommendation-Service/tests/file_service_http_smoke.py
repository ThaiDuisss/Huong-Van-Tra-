"""Launched by Java's isolated HTTP test. H2/SQLite + mocked MinIO, never shared E2E."""
from pathlib import Path
import hashlib
import io
import sys
from types import SimpleNamespace
from uuid import uuid4
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from app.api.ai_reports import router, get_report_service
from app.api.ai_reports import get_business_report_service
from app.clients.file_client import FileClient
from app.core.internal_auth import generate_internal_auth_headers
from app.database.session import get_db
from app.models.ai_report import AiReport, AiReportImport
from app.models.business_report import BusinessReportExport, CompletedPurchaseOrder, RecommendationSnapshot
from app.service.business_report_service import BusinessReportService
from app.schemas.business_report import BusinessExportRequest
from tests.test_business_reports import snapshot, order
from openpyxl import load_workbook
from app.schemas.ai_report import AiReportCreate
from app.service.ai_report_service import AiReportService

BASE = "/api/recommendation/ai-reports"
OWNER, STAFF, OTHER = (str(uuid4()) for _ in range(3))


def signed(user=OWNER, roles="SALE_STAFF"):
    return generate_internal_auth_headers(user_id=user, roles=roles, authorization="Bearer synthetic-jwt")


def xlsx():
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        archive.writestr("_rels/.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        archive.writestr("xl/workbook.xml", '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Synthetic" sheetId="1" r:id="rId1"/></sheets></workbook>')
        archive.writestr("xl/_rels/workbook.xml.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        archive.writestr("xl/worksheets/sheet1.xml", '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Synthetic original report</t></is></c></row></sheetData></worksheet>')
    return stream.getvalue()


def pdf():
    """One-page synthetic original with a real cross-reference table, not a header stub."""
    content = b"BT /F1 12 Tf 40 100 Td (Synthetic original report) Tj ET\n"
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"endstream"]
    data = bytearray(b"%PDF-1.7\n")
    offsets = [0]
    for number, body in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f"{number} 0 obj\n".encode() + body + b"\nendobj\n")
    start = len(data)
    data.extend(b"xref\n0 6\n0000000000 65535 f \n")
    for offset in offsets[1:]:
        data.extend(f"{offset:010d} 00000 n \n".encode())
    data.extend(f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n".encode())
    return bytes(data)


def main(port):
    endpoint = f"http://127.0.0.1:{port}"
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    AiReport.__table__.create(engine)
    AiReportImport.__table__.create(engine)
    for model in (BusinessReportExport, CompletedPurchaseOrder, RecommendationSnapshot):
        model.__table__.create(engine)
    service = AiReportService()
    with Session(engine, expire_on_commit=False) as db, httpx.Client(base_url=endpoint, timeout=10) as java:
        app = FastAPI()
        app.include_router(router)
        app.dependency_overrides[get_db] = lambda: db
        app.dependency_overrides[get_report_service] = lambda: service
        business = BusinessReportService()
        app.dependency_overrides[get_business_report_service] = lambda: business
        db.execute(__import__("sqlalchemy").text("CREATE TABLE products(id TEXT, name TEXT)"))
        db.add_all([snapshot(), order()]); db.commit()
        with TestClient(app) as api:
            for kind, mime, data in [("pdf", "application/pdf", pdf()),
                ("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx())]:
                init = java.post("/api/file/init-upload", headers=signed(), json={"originalFilename": f"Báo cáo.{kind}",
                    "contentType": mime, "sizeBytes": len(data), "purpose": "USER_DOCUMENT", "ownerType": "AI_REPORT"})
                assert init.status_code == 200, ("init", init.status_code)
                upload = init.json()["data"]
                assert upload["visibility"] == "PRIVATE" and upload["publicUrl"] is None
                assert java.put(upload["uploadUrl"], content=data, headers={"Content-Type": mime}).status_code == 200
                file_id = upload["fileId"]
                complete = java.post(f"/api/file/{file_id}/complete", headers=signed())
                assert complete.status_code == 200, ("complete", complete.status_code)
                assert java.post(f"/api/file/{file_id}/complete", headers=signed()).status_code == 200
                payload = {"name": "Original", "version": "V1", "fileId": file_id}
                assert api.post(BASE, json=payload).status_code == 401
                assert api.post(BASE, json=payload, headers=signed(OTHER, "CUSTOMER")).status_code == 403
                assert api.post(BASE, json=payload, headers=signed(STAFF)).status_code == 403
                # Rehearse remote binding success followed by a failed local transaction.
                if kind == "xlsx":
                    from unittest.mock import patch
                    commit = db.commit
                    calls = 0
                    def fail_second_commit():
                        nonlocal calls
                        calls += 1
                        if calls == 2:
                            raise RuntimeError("Synthetic local commit failure")
                        commit()
                    with patch.object(db, "commit", side_effect=fail_second_commit):
                        try:
                            service.create(db, AiReportCreate(**payload), OWNER)
                        except RuntimeError:
                            pass
                        else:
                            raise AssertionError("Local commit failure was not exercised")
                    intent = db.scalar(select(AiReportImport).where(AiReportImport.file_id == file_id,
                        AiReportImport.created_by == OWNER))
                    report = service.reconcile(db, intent)
                    report_id = report.id
                else:
                    created = api.post(BASE, json=payload, headers=signed())
                    assert created.status_code == 201, ("import", created.status_code, created.text)
                    report_id = created.json()["id"]
                retry = api.post(BASE, json=payload, headers=signed())
                assert retry.status_code == 200 and retry.json()["id"] == report_id
                assert api.get(BASE + "/" + report_id, headers=signed(STAFF)).status_code == 200
                assert api.get(BASE + "/" + report_id + "/download", headers=signed(OTHER, "CUSTOMER")).status_code == 403
                downloaded = api.get(BASE + "/" + report_id + "/download", headers=signed(STAFF))
                assert downloaded.status_code == 200, ("download", downloaded.status_code)
                assert hashlib.sha256(downloaded.content).digest() == hashlib.sha256(data).digest()
                assert downloaded.headers["content-type"] == mime
                assert "filename*=UTF-8''" in downloaded.headers["content-disposition"]
                assert downloaded.headers["cache-control"] == "private, no-store"
                # Owner sharing is confined to the report API, not generic private file access.
                generic = java.get(f"/api/file/{file_id}/download-url", headers=signed(STAFF))
                assert generic.status_code != 200 or generic.json().get("data") is None
                for _ in range(2):
                    intent = db.scalar(select(AiReportImport).where(AiReportImport.id == report_id))
                    assert service.reconcile(db, intent).id == report_id
                print(f"PASS {kind.upper()}: real Java HTTP upload/complete/bind/download; checksum; retry; reconciliation")
            assert len(db.scalars(select(AiReport)).all()) == 2
            payload = dict(fromDate="2026-10-11", toDate="2026-10-12", requestId=str(uuid4()))
            exports = BASE + "/exports"
            # File-Service commit survives a failed local finalization; retry
            # reuses exact frozen workbook and one GENERATED reference.
            from unittest.mock import patch
            commit = db.commit; calls = 0
            def fail_export_finalization():
                nonlocal calls
                calls += 1
                if calls == 3: raise RuntimeError("Synthetic generated local commit failure")
                commit()
            with patch.object(db, "commit", side_effect=fail_export_finalization):
                try: business.export(db, BusinessExportRequest(**payload), OWNER)
                except RuntimeError: pass
                else: raise AssertionError("Generated local commit failure not exercised")
            created = api.post(exports, json=payload, headers=signed())
            assert created.status_code == 200, ("generated", created.status_code, created.text)
            record = created.json()
            assert api.post(exports, json=payload, headers=signed()).json() == record
            assert api.post(exports, json=payload).status_code == 401
            assert api.post(exports, json=payload, headers=signed(OTHER, "CUSTOMER")).status_code == 403
            downloaded = api.get(exports+"/"+record["id"]+"/download", headers=signed(STAFF))
            assert downloaded.status_code == 200
            assert hashlib.sha256(downloaded.content).hexdigest() == record["sha256"]
            from app.service.business_report_workbook import SHEETS
            assert load_workbook(io.BytesIO(downloaded.content)).sheetnames == SHEETS
            assert api.get(BASE+"/"+record["id"]+"/download", headers=signed()).status_code == 404
            assert len(db.scalars(select(BusinessReportExport)).all()) == 1
            print("PASS GENERATED: actual four-sheet workbook -> real Java HTTP/private reservation/binding -> authorized download; checksum; failed local commit recovery")
            bad_headers = signed()
            bad_headers["X-Signature"] = "invalid"
            body = {"fileId": str(uuid4()), "ownerId": OWNER}
            assert java.post("/internal/file/ai-reports/metadata", json=body, headers=bad_headers).status_code == 401
            assert java.post("/internal/file/ai-reports/metadata", json=body,
                headers=signed("service:other", "SYSTEM")).status_code == 403
            assert java.post("/internal/file/ai-reports/metadata", json=body,
                headers=generate_internal_auth_headers(user_id="service:recommendation-service", roles="SYSTEM")).status_code == 404
            print("PASS isolation: SQLite Recommendation DB, H2 File DB, mocked MinIO capabilities; NOT real MySQL/MinIO E2E")
    engine.dispose()


if __name__ == "__main__":
    main(int(sys.argv[1]))
