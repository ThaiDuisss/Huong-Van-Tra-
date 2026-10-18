from datetime import timedelta
import asyncio
import contextlib
import hashlib
import hmac
import io
from types import SimpleNamespace
from uuid import uuid4
import unittest
from unittest.mock import patch

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
import httpx
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from starlette.responses import StreamingResponse
from starlette.requests import ClientDisconnect

from app.api.ai_reports import get_report_service, router
from app.clients.file_client import FileClient
from app.core.ai_report_auth import AI_REPORT_ROLES
from app.database.session import get_db
from app.models.ai_report import AiReport, AiReportImport
from app.schemas.ai_report import TrustedReportFile
from app.service.ai_report_service import AiReportService, now

A, B, FILE = str(uuid4()), str(uuid4()), str(uuid4())
SECRET = "test-report-secret"
BASE = "/api/recommendation/ai-reports"


def signed(user=A, roles="SALE_STAFF"):
    headers = {"X-User-ID": user, "X-User_Roles": roles,
        "X-Request-ID": str(uuid4()), "Authorization": "Bearer test-token"}
    raw = "\n".join(headers.values())
    headers["X-Signature"] = hmac.new(SECRET.encode(), raw.encode(), hashlib.sha256).hexdigest()
    return headers


class Files:
    def __init__(self):
        self.bindings = {}
        self.owner = A
        self.kind = "PDF"
        self.active = True
        self.deleted = False
        self.public = False
        self.fail_bind = False
        self.bind_calls = 0

    def metadata(self, file_id, owner_id):
        if owner_id != self.owner:
            raise HTTPException(403, "Wrong owner")
        return TrustedReportFile(fileId=file_id, ownerId=self.owner, fileName="Báo cáo.pdf",
            fileType=self.kind, contentType="application/pdf", sizeBytes=16,
            status="ACTIVE" if self.active else "PENDING", visibility="PUBLIC" if self.public else "PRIVATE",
            deleted=self.deleted)

    def bind(self, report_id, file_id, owner_id):
        self.bind_calls += 1
        if self.fail_bind:
            raise HTTPException(502, "Timeout")
        if file_id in self.bindings and self.bindings[file_id] != report_id:
            raise HTTPException(409, "Different binding")
        self.bindings[file_id] = report_id

    def download(self, report):
        if self.bindings.get(report.file_id) != report.id:
            raise HTTPException(403, "Binding mismatch")
        return StreamingResponse(iter([b"%PDF-1.7\n", b"report"]), media_type="application/pdf",
            headers={"Content-Disposition": "attachment; filename=report.pdf"})


class AiReportApiTests(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        AiReport.__table__.create(self.engine)
        AiReportImport.__table__.create(self.engine)
        self.db = Session(self.engine, expire_on_commit=False)
        self.files = Files()
        self.service = AiReportService(self.files)
        self.app = FastAPI()
        self.app.include_router(router)
        self.app.dependency_overrides[get_report_service] = lambda: self.service
        self.app.dependency_overrides[get_db] = lambda: self.db
        self.auth_settings = patch("app.core.ai_report_auth.get_settings", return_value=SimpleNamespace(internal_header_secret=SECRET))
        self.auth_settings.start()
        self.client = TestClient(self.app)
        self.payload = {"name": " Report ", "version": " V1 ", "fileId": FILE}

    def tearDown(self):
        self.client.close()
        self.auth_settings.stop()
        self.db.close()
        self.engine.dispose()

    def create(self, headers=None):
        return self.client.post(BASE, json=self.payload, headers=headers or signed())

    def test_unauthenticated_all_operations_rejected(self):
        report_id = str(uuid4())
        for method, path, body in [("GET", BASE, None), ("POST", BASE, self.payload),
            ("PATCH", BASE + "/" + report_id, {"name": "R", "version": "V"}),
            ("GET", BASE + "/" + report_id + "/download", None)]:
            with self.subTest(method=method):
                self.assertEqual(self.client.request(method, path, json=body).status_code, 401)

    def test_invalid_signature(self):
        headers = signed()
        headers["X-Signature"] = "bad"
        self.assertEqual(self.create(headers).status_code, 401)

    def test_forged_role(self):
        headers = signed(roles="CUSTOMER")
        headers["X-User_Roles"] = "ADMIN"
        self.assertEqual(self.create(headers).status_code, 401)

    def test_forged_user(self):
        headers = signed()
        headers["X-User-ID"] = B
        self.assertEqual(self.create(headers).status_code, 401)

    def test_duplicate_signed_headers(self):
        headers = list(signed().items()) + [("X-User_Roles", "ADMIN")]
        self.assertEqual(self.create(headers).status_code, 401)

    def test_disallowed_role(self):
        self.assertEqual(self.create(signed(roles="CUSTOMER")).status_code, 403)

    def test_service_principal_not_a_human(self):
        self.assertEqual(self.create(signed(user="service:recommendation-service", roles="ADMIN")).status_code, 401)

    def test_all_existing_allowed_roles(self):
        for role in AI_REPORT_ROLES:
            with self.subTest(role=role):
                self.assertEqual(self.client.get(BASE, headers=signed(roles=role)).status_code, 200)

    def test_role_prefix_supported(self):
        self.assertEqual(self.create(signed(roles="ROLE_ADMIN")).status_code, 201)

    def test_create_trusted_metadata_and_audit(self):
        response = self.create()
        self.assertEqual(response.status_code, 201)
        report = response.json()
        self.assertEqual((report["name"], report["version"], report["createdBy"]), ("Report", "V1", A))
        self.assertEqual(report["fileName"], "Báo cáo.pdf")
        self.assertTrue(report["createdAt"].endswith("Z"))
        self.assertEqual(self.files.bindings[FILE], report["id"])
        self.assertEqual(self.db.scalar(select(AiReportImport)).status, "COMPLETE")

    def test_unknown_metadata_rejected(self):
        for key in ["createdBy", "ownerId", "fileName", "fileType"]:
            with self.subTest(key=key):
                payload = dict(self.payload, **{key: "forged"})
                self.assertEqual(self.client.post(BASE, json=payload, headers=signed()).status_code, 422)

    def test_validation(self):
        for change in [{"name": "  "}, {"name": "a" * 201}, {"version": " "},
            {"version": "v" * 51}, {"fileId": "invalid"}]:
            with self.subTest(change=change):
                self.assertEqual(self.client.post(BASE, json=dict(self.payload, **change), headers=signed()).status_code, 422)

    def test_wrong_owner_rejected_and_failure_durable(self):
        self.assertEqual(self.create(signed(user=B)).status_code, 403)
        self.assertEqual(self.db.scalar(select(AiReportImport)).status, "FAILED")
        self.assertIsNone(self.db.scalar(select(AiReport)))
        self.assertFalse(self.files.bindings)

    def test_invalid_file_type(self):
        self.files.kind = "DOCX"
        self.assertEqual(self.create().status_code, 415)
        self.assertFalse(self.files.bindings)

    def test_rejected_non_owner_cannot_reserve_owners_file(self):
        self.assertEqual(self.create(signed(user=B)).status_code, 403)
        self.assertEqual(self.create(signed(user=A)).status_code, 201)
        self.assertEqual(len(self.db.scalars(select(AiReport)).all()), 1)

    def test_deleted_inactive_public_files_rejected(self):
        for flag in ["active", "deleted", "public"]:
            with self.subTest(flag=flag):
                self.files.active, self.files.deleted, self.files.public = True, False, False
                setattr(self.files, flag, flag != "active")
                self.assertEqual(self.create().status_code, 409)
                self.assertFalse(self.files.bindings)

    def test_identical_retry_no_duplicate_or_rebind(self):
        first, second = self.create(), self.create()
        self.assertEqual((first.status_code, second.status_code), (201, 200))
        self.assertEqual(first.json()["id"], second.json()["id"])
        self.assertEqual(len(self.db.scalars(select(AiReport)).all()), 1)
        self.assertEqual(self.files.bind_calls, 1)

    def test_conflicting_retry(self):
        self.create()
        self.payload["name"] = "Other"
        self.assertEqual(self.create().status_code, 409)
        self.assertEqual(self.create(signed(user=B)).status_code, 409)

    def test_update_preserves_file_and_creator(self):
        report = self.create().json()
        response = self.client.patch(BASE + "/" + report["id"], headers=signed(user=B),
            json={"name": " New ", "version": " V2 "})
        self.assertEqual(response.status_code, 200)
        result = response.json()
        self.assertEqual((result["name"], result["version"], result["updatedBy"]), ("New", "V2", B))
        for key in ["fileId", "fileName", "fileType", "createdBy"]:
            self.assertEqual(result[key], report[key])
        self.assertEqual(self.create().status_code, 200)

    def test_update_cannot_replace_file(self):
        report = self.create().json()
        self.assertEqual(self.client.patch(BASE + "/" + report["id"], headers=signed(),
            json={"name": "R", "version": "V", "fileId": str(uuid4())}).status_code, 422)

    def test_list_pagination_sort(self):
        for i in range(5):
            report = AiReport(id=f"{i:036}", name=str(i), version="V1", file_id=str(uuid4()),
                file_name="r.pdf", file_type="PDF", created_by=A,
                created_at=now(), updated_at=now() + timedelta(minutes=i), is_deleted=False)
            self.db.add(report)
        self.db.commit()
        page = self.client.get(BASE + "?page=1&size=2", headers=signed()).json()
        self.assertEqual([r["name"] for r in page["content"]], ["2", "1"])
        self.assertEqual((page["totalElements"], page["totalPages"], page["first"], page["last"]), (5, 3, False, False))
        for query in ["size=101", "page=-1", "size=0"]:
            self.assertEqual(self.client.get(BASE + "?" + query, headers=signed()).status_code, 422)

    def test_missing_report_download_update(self):
        path = BASE + "/" + str(uuid4())
        self.assertEqual(self.client.get(path + "/download", headers=signed()).status_code, 404)
        self.assertEqual(self.client.patch(path, json={"name": "R", "version": "V"}, headers=signed()).status_code, 404)

    def test_soft_deleted_hidden_everywhere(self):
        report = self.create().json()
        entity = self.db.get(AiReport, report["id"])
        entity.is_deleted = True
        self.db.commit()
        self.assertEqual(self.client.get(BASE, headers=signed()).json()["totalElements"], 0)
        self.assertEqual(self.client.get(BASE + "/" + report["id"] + "/download", headers=signed()).status_code, 404)
        self.assertEqual(self.create().status_code, 409)

    def test_shared_staff_download_by_report_id(self):
        report = self.create(signed(user=A)).json()
        page = self.client.get(BASE, headers=signed(user=B)).json()
        self.assertEqual(page["content"][0]["id"], report["id"])
        download = self.client.get(BASE + "/" + report["id"] + "/download", headers=signed(user=B))
        self.assertEqual(download.status_code, 200)
        self.assertEqual(download.content, b"%PDF-1.7\nreport")

    def test_binding_failure_no_fake_success_retry_recovers(self):
        self.files.fail_bind = True
        self.assertEqual(self.create().status_code, 502)
        intent_id = self.db.scalar(select(AiReportImport)).id
        self.assertIsNone(self.db.scalar(select(AiReport)))
        self.files.fail_bind = False
        report = self.create().json()
        self.assertEqual(report["id"], intent_id)

    def test_bind_success_local_commit_failure_recovers_same_identity(self):
        original_commit = self.db.commit
        commits = 0
        def uncertain_commit():
            nonlocal commits
            commits += 1
            if commits == 2:
                raise RuntimeError("Commit unavailable")
            original_commit()
        with patch.object(self.db, "commit", side_effect=uncertain_commit):
            with self.assertRaises(RuntimeError):
                self.create()
        intent = self.db.scalar(select(AiReportImport))
        self.assertEqual(intent.status, "PENDING")
        self.assertEqual(self.files.bindings[FILE], intent.id)
        self.assertIsNone(self.db.scalar(select(AiReport)))
        self.assertEqual(self.create().json()["id"], intent.id)

    def test_get_metadata_and_unauthorized_or_deleted_lookup(self):
        report = self.create().json()
        path = BASE + "/" + report["id"]
        self.assertEqual(self.client.get(path, headers=signed(user=B)).json(), report)
        self.assertEqual(self.client.get(path).status_code, 401)
        self.assertEqual(self.client.get(path, headers=signed(roles="CUSTOMER")).status_code, 403)
        self.db.get(AiReport, report["id"]).is_deleted = True
        self.db.commit()
        self.assertEqual(self.client.get(path, headers=signed()).status_code, 404)

    def test_reconciliation_restores_missing_binding_and_local_state_repeatedly(self):
        report = self.create().json()
        intent = self.db.scalar(select(AiReportImport))
        self.files.bindings.clear()
        intent.status, intent.last_error = "PENDING", "HTTP_502"
        self.db.commit()
        for _ in range(2):
            recovered = self.service.reconcile(self.db, intent)
            self.assertEqual(recovered.id, report["id"])
            self.assertEqual(intent.status, "COMPLETE")
            self.assertIsNone(intent.last_error)
        self.assertEqual(self.files.bindings, {FILE: report["id"]})
        self.assertEqual(len(self.db.scalars(select(AiReport)).all()), 1)

    def test_reconciliation_outage_preserves_completed_report_and_retry(self):
        report = self.create().json()
        intent = self.db.scalar(select(AiReportImport))
        self.files.bindings.clear()
        self.files.fail_bind = True
        with self.assertRaises(HTTPException) as raised:
            self.service.reconcile(self.db, intent)
        self.assertEqual(raised.exception.status_code, 502)
        self.assertEqual(intent.status, "COMPLETE")
        self.assertIsNotNone(self.db.get(AiReport, report["id"]))
        self.files.fail_bind = False
        self.service.reconcile(self.db, intent)
        self.assertEqual(self.files.bindings[FILE], report["id"])

    def test_reconciliation_never_revives_deleted_or_replaces_original(self):
        report = self.create().json()
        intent = self.db.scalar(select(AiReportImport))
        entity = self.db.get(AiReport, report["id"])
        entity.is_deleted = True
        self.db.commit()
        with self.assertRaises(HTTPException):
            self.service.reconcile(self.db, intent)
        entity.is_deleted = False
        self.db.commit()
        self.files.bindings[FILE] = str(uuid4())
        with self.assertRaises(HTTPException) as raised:
            self.service.reconcile(self.db, intent)
        self.assertEqual(raised.exception.status_code, 409)
        self.assertNotEqual(self.files.bindings[FILE], entity.id)

    def test_reconciliation_recovers_binding_success_local_commit_failure(self):
        original_commit = self.db.commit
        commits = 0
        def commit():
            nonlocal commits
            commits += 1
            if commits == 2:
                raise RuntimeError("failure")
            original_commit()
        with patch.object(self.db, "commit", side_effect=commit):
            with self.assertRaises(RuntimeError):
                self.create()
        intent = self.db.scalar(select(AiReportImport))
        self.assertEqual(self.service.reconcile(self.db, intent).id, intent.id)
        self.assertEqual(len(self.db.scalars(select(AiReport)).all()), 1)

    def test_cli_repair_requires_explicit_retry_before_database_access(self):
        from app.ai_report_reconciliation import main
        with patch("sys.argv", ["reconcile", "--repair-bindings"]), \
            patch("app.ai_report_reconciliation.SessionLocal") as session_factory, \
            contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                main()
            self.assertEqual(raised.exception.code, 2)
            session_factory.assert_not_called()

    def test_cli_completed_binding_repair_is_opt_in(self):
        from app.ai_report_reconciliation import main
        self.create()
        intent = self.db.scalar(select(AiReportImport))
        intent.updated_at = now() - timedelta(minutes=10)
        self.db.commit()
        with patch("app.ai_report_reconciliation.SessionLocal") as factory, \
            patch("app.ai_report_reconciliation.AiReportService", return_value=self.service), \
            contextlib.redirect_stdout(io.StringIO()) as output:
            factory.return_value.__enter__.return_value = self.db
            with patch("sys.argv", ["reconcile", "--retry"]):
                main()
            self.assertEqual(output.getvalue(), "")
            self.files.bindings.clear()
            with patch("sys.argv", ["reconcile", "--retry", "--repair-bindings"]):
                main()
            self.assertEqual(self.files.bindings[FILE], intent.id)
            self.assertIn('"result": "COMPLETE"', output.getvalue())


class FileClientTests(unittest.TestCase):
    def setUp(self):
        self.settings = SimpleNamespace(file_service_url="http://file.test", ai_report_file_timeout_seconds=5,
            ai_report_storage_origins="http://storage.test")
        self.report = SimpleNamespace(id=str(uuid4()), file_id=FILE, file_type="PDF")
        self.access = {"url": "http://storage.test/bucket/key?signature=test", "fileName": '../Báo cáo\r\n.pdf',
            "sizeBytes": 15, "etag": "original-etag", "expiresInSeconds": 300, "contentType": "application/pdf"}
        self.settings_patch = patch("app.clients.file_client.get_settings", return_value=self.settings)
        self.settings_patch.start()

    def tearDown(self):
        self.settings_patch.stop()

    def test_stream_original_filename_no_auth_to_storage(self):
        seen = []
        def transport(request):
            seen.append(request)
            return httpx.Response(200, stream=httpx.ByteStream(b"%PDF-1.7\nreport"), headers={"content-length": "15"})
        real_client = httpx.Client
        with patch.object(FileClient, "_request", return_value=self.access), patch("app.clients.file_client.httpx.Client",
            side_effect=lambda **kwargs: real_client(transport=httpx.MockTransport(transport), **kwargs)):
            app = FastAPI()
            app.get("/download")(lambda: FileClient().download(self.report))
            response = TestClient(app).get("/download")
        self.assertEqual(response.content, b"%PDF-1.7\nreport")
        self.assertIn("filename*=UTF-8''B%C3%A1o%20c%C3%A1o.pdf", response.headers["content-disposition"])
        self.assertEqual(response.headers["cache-control"], "private, no-store")
        self.assertNotIn("authorization", seen[0].headers)
        self.assertNotIn("x-signature", seen[0].headers)
        self.assertEqual(seen[0].headers["if-match"], '"original-etag"')

    def test_untrusted_urls_rejected_before_fetch(self):
        for url in ["http://evil.test/file", "file:///secret", "http://u:p@storage.test/file"]:
            with self.subTest(url=url), patch.object(FileClient, "_request", return_value=dict(self.access, url=url)):
                with self.assertRaises(HTTPException) as raised:
                    FileClient().download(self.report)
                self.assertEqual(raised.exception.status_code, 502)

    def test_no_content_bind_response_is_success(self):
        with patch("app.clients.file_client.generate_internal_auth_headers", return_value=signed(user="service:recommendation-service", roles="SYSTEM")), \
            patch("app.clients.file_client.httpx.Client") as mocked:
            mocked.return_value.__enter__.return_value.request.return_value = httpx.Response(204)
            self.assertIsNone(FileClient().bind(self.report.id, FILE, A))

    def test_storage_redirect_rejected(self):
        real_client = httpx.Client
        with patch.object(FileClient, "_request", return_value=self.access), patch("app.clients.file_client.httpx.Client",
            side_effect=lambda **kwargs: real_client(transport=httpx.MockTransport(
                lambda request: httpx.Response(302, headers={"location": "http://evil.test"})), **kwargs)):
            with self.assertRaises(HTTPException):
                FileClient().download(self.report)

    def test_disconnect_before_first_chunk_closes_storage_client(self):
        real_client = httpx.Client
        clients = []
        def factory(**kwargs):
            client = real_client(transport=httpx.MockTransport(lambda request: httpx.Response(200,
                stream=httpx.ByteStream(b"%PDF-1.7\nreport"), headers={"content-length": "15"})), **kwargs)
            clients.append(client)
            return client
        with patch.object(FileClient, "_request", return_value=self.access), \
            patch("app.clients.file_client.httpx.Client", side_effect=factory):
            response = FileClient().download(self.report)
        async def receive():
            return {"type": "http.disconnect"}
        async def send(message):
            raise OSError("Browser disconnected")
        with self.assertRaises(ClientDisconnect):
            asyncio.run(response({"type": "http", "asgi": {"spec_version": "2.4"}}, receive, send))
        self.assertTrue(clients[0].is_closed)

    def test_unexpected_binding_status_never_claims_success(self):
        for status, content in [(200, b''), (202, b'{}'), (302, b'{}')]:
            with self.subTest(status=status), patch("app.clients.file_client.generate_internal_auth_headers", return_value={}), \
                patch("app.clients.file_client.httpx.Client") as mocked:
                mocked.return_value.__enter__.return_value.request.return_value = httpx.Response(status, content=content)
                with self.assertRaises(HTTPException) as raised:
                    FileClient().bind(self.report.id, FILE, A)
                self.assertEqual(raised.exception.status_code, 502)

    def test_invalid_download_contract_rejected_before_storage_io(self):
        for change in [{"etag": None}, {"etag": 'bad"etag'}, {"sizeBytes": 0}, {"expiresInSeconds": 301},
            {"expiresInSeconds": 0}, {"contentType": "text/html"}, {"url": None}, {"fileName": ""}]:
            with self.subTest(change=change), patch.object(FileClient, "_request", return_value=dict(self.access, **change)), \
                patch("app.clients.file_client.httpx.Client") as mocked:
                with self.assertRaises(HTTPException) as raised:
                    FileClient().download(self.report)
                self.assertEqual(raised.exception.status_code, 502)
                mocked.assert_not_called()

    def test_expired_missing_or_changed_storage_capability_is_safe_failure(self):
        real_client = httpx.Client
        for status in [403, 404, 412]:
            with self.subTest(status=status), patch.object(FileClient, "_request", return_value=self.access), \
                patch("app.clients.file_client.httpx.Client", side_effect=lambda **kwargs: real_client(
                    transport=httpx.MockTransport(lambda request: httpx.Response(status)), **kwargs)):
                with self.assertRaises(HTTPException) as raised:
                    FileClient().download(self.report)
                self.assertEqual(raised.exception.status_code, 502)


if __name__ == "__main__":
    unittest.main()
