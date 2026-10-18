from urllib.parse import quote, urlsplit

import httpx
from fastapi import HTTPException
from starlette.responses import StreamingResponse

from app.core.config import get_settings
from app.core.internal_auth import generate_internal_auth_headers
from app.schemas.ai_report import TrustedReportDownload, TrustedReportFile

FILE_MIMES = {
    "PDF": "application/pdf",
    "XLSX": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
}


class OriginalFileResponse(StreamingResponse):
    def __init__(self, *args, close_callback, **kwargs):
        super().__init__(*args, **kwargs)
        self.close_callback = close_callback

    async def __call__(self, scope, receive, send):
        try:
            await super().__call__(scope, receive, send)
        finally:
            # Also close if the browser disconnects before the iterator starts.
            self.close_callback()


class FileClient:
    def _request(self, method: str, path: str, *, expected_status: int = 200, **kwargs):
        settings = get_settings()
        try:
            with httpx.Client(timeout=settings.ai_report_file_timeout_seconds, follow_redirects=False) as client:
                headers = generate_internal_auth_headers(user_id="service:recommendation-service", roles="SYSTEM")
                if "content" in kwargs:
                    headers["Content-Type"] = "application/octet-stream"
                response = client.request(method, settings.file_service_url.rstrip("/") + path,
                    headers=headers,
                    **kwargs)
                if response.status_code >= 400:
                    status = response.status_code if response.status_code in {403, 404, 409, 415, 422} else 502
                    raise HTTPException(status, "File authorization or validation failed")
                if response.status_code != expected_status:
                    raise HTTPException(502, "Unexpected File-Service response")
                return None if expected_status == 204 else response.json()
        except (httpx.HTTPError, ValueError):
            raise HTTPException(502, "File-Service unavailable") from None

    def metadata(self, file_id: str, owner_id: str) -> TrustedReportFile:
        try:
            return TrustedReportFile.model_validate(self._request("POST", "/internal/file/ai-reports/metadata",
                json={"fileId": file_id, "ownerId": owner_id}))
        except ValueError:
            raise HTTPException(502, "Invalid trusted file metadata") from None

    def bind(self, report_id: str, file_id: str, owner_id: str) -> None:
        self._request("PUT", f"/internal/file/ai-reports/{report_id}/binding",
            expected_status=204, json={"fileId": file_id, "ownerId": owner_id})

    def orphan_candidates(self):
        return self._request("GET", "/internal/file/ai-reports/orphans")

    def store_generated(self, record):
        return TrustedReportFile.model_validate(self._request("PUT",
            f"/internal/file/ai-reports/{record.id}/generated", content=record.workbook,
            params={"ownerId": record.created_by, "fileName": record.file_name, "sha256": record.sha256},
            # _request supplies only server-side service authentication headers.
        ))

    def download(self, report, generated=False) -> StreamingResponse:
        try:
            access = TrustedReportDownload.model_validate(self._request("POST",
                f"/internal/file/ai-reports/{report.id}/{'generated-download-url' if generated else 'download-url'}", json={"fileId": report.file_id}))
            if access.contentType != FILE_MIMES.get(report.file_type):
                raise ValueError("Content type differs from report")
            parsed = urlsplit(access.url)
        except ValueError:
            raise HTTPException(502, "Invalid trusted download metadata") from None
        allowed = {origin.strip().rstrip("/") for origin in get_settings().ai_report_storage_origins.split(",")}
        origin = f"{parsed.scheme}://{parsed.netloc}"
        if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password or origin not in allowed:
            raise HTTPException(502, "Untrusted storage origin")
        client = httpx.Client(timeout=get_settings().ai_report_file_timeout_seconds, follow_redirects=False)
        try:
            # A new client sends no Gateway JWT or service authentication to storage.
            upstream = client.send(client.build_request("GET", access.url,
                headers={"If-Match": '"' + access.etag + '"', "Accept-Encoding": "identity"}), stream=True)
            if upstream.status_code != 200:
                raise HTTPException(502, "Original file unavailable")
            expected_size = access.sizeBytes
            if upstream.headers.get("content-encoding", "identity") != "identity":
                raise HTTPException(502, "Unexpected original file encoding")
            if upstream.headers.get("content-length") != str(expected_size):
                raise HTTPException(502, "Original file size mismatch")
        except Exception as error:
            if "upstream" in locals():
                upstream.close()
            client.close()
            if isinstance(error, httpx.HTTPError):
                raise HTTPException(502, "Storage unavailable") from None
            raise

        def chunks():
            count = 0
            try:
                for chunk in upstream.iter_raw():
                    count += len(chunk)
                    if count > expected_size:
                        raise RuntimeError("Original file exceeds trusted size")
                    yield chunk
                if count != expected_size:
                    raise RuntimeError("Original file truncated")
            finally:
                upstream.close()
                client.close()

        original = access.fileName
        safe = original.replace("\\", "/").split("/")[-1]
        safe = "".join(c for c in safe if ord(c) >= 32 and ord(c) != 127)[:255] or "report"
        ascii_name = "".join(c if 32 <= ord(c) < 127 and c not in '\"\\;' else "_" for c in safe)
        def close():
            upstream.close()
            client.close()

        return OriginalFileResponse(chunks(), close_callback=close, media_type=FILE_MIMES[report.file_type], headers={
            "Content-Disposition": f"attachment; filename=\"{ascii_name}\"; filename*=UTF-8''{quote(safe, safe='')}",
            "Content-Length": str(expected_size), "Cache-Control": "private, no-store",
            "X-Content-Type-Options": "nosniff",
        })
