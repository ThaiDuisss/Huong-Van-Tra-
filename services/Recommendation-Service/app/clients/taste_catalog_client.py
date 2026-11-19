from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import time
from uuid import uuid4

import httpx
from pydantic import ValidationError

from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings
from app.schemas.taste_catalog import (
    Envelope,
    SnapshotError,
    VerifiedSnapshot,
    parse_json,
    utc_timestamp,
    verify_payload,
)

PATH = "/internal/taste-catalog/snapshot"
ACTOR = "service:recommendation-service"
SIGNATURE = "X-Taste-Snapshot-Signature"
DOMAIN = "hvtra-catalog-taste-v1"


class TasteCatalogClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        configured = settings.taste_catalog_sync_secret
        secret = configured.get_secret_value() if configured else ""
        if not secret.strip() or len(secret.encode()) < 32 or secret == settings.internal_header_secret:
            raise SnapshotError("INTERNAL_AUTH_BLOCKED")
        self.secret = secret.encode()
        try:
            self.origin = httpx.URL(settings.catalog_service_url)
        except httpx.InvalidURL:
            raise SnapshotError("CATALOG_UNAVAILABLE") from None
        if (self.origin.scheme not in {"http", "https"} or not self.origin.host or self.origin.userinfo
                or self.origin.path not in ("", "/") or self.origin.query or self.origin.fragment):
            raise SnapshotError("CATALOG_UNAVAILABLE")

    def _sign(self, *fields: str) -> str:
        return hmac.new(self.secret, "\n".join((DOMAIN, *fields)).encode(), hashlib.sha256).hexdigest()

    def fetch(self) -> VerifiedSnapshot:
        timestamp, nonce = str(int(time.time())), uuid4().hex
        try:
            headers = generate_internal_auth_headers(user_id=ACTOR, roles="SYSTEM", authorization=f"Bearer {ACTOR}")
        except ValueError:
            raise SnapshotError("INTERNAL_AUTH_BLOCKED") from None
        headers.update({
            "X-Taste-Snapshot-Timestamp": timestamp,
            "X-Taste-Snapshot-Nonce": nonce,
            SIGNATURE: self._sign("request", ACTOR, "GET", PATH, timestamp, nonce),
            "Accept": "application/json",
            "Accept-Encoding": "identity",
        })
        body, signature = self._request(headers)
        try:
            envelope = Envelope.model_validate(parse_json(body))
        except (ValidationError, RecursionError):
            raise SnapshotError("INVALID_SNAPSHOT") from None
        expected = self._sign("response", nonce, envelope.snapshotId, envelope.sourceReadAt, envelope.payloadSha256)
        if len(signature) != 64 or not signature.isascii() or not hmac.compare_digest(signature, expected):
            raise SnapshotError("INTERNAL_AUTH_BLOCKED")
        if envelope.snapshotId != "taste-v1-" + envelope.payloadSha256:
            raise SnapshotError("INVALID_SNAPSHOT")
        source_read_at = utc_timestamp(envelope.sourceReadAt)
        age = datetime.now(timezone.utc) - source_read_at
        if age < timedelta(seconds=-30) or age >= timedelta(seconds=self.settings.taste_catalog_ttl_seconds):
            raise SnapshotError("STALE")
        verify_payload(envelope.payload, envelope.payloadSha256,
                       self.settings.taste_catalog_max_bytes, self.settings.taste_catalog_max_variants)
        return VerifiedSnapshot(envelope.snapshotId, source_read_at, envelope.payloadSha256, envelope.payload)

    def _request(self, headers: dict[str, str]) -> tuple[bytes, str]:
        timeout = self.settings.taste_catalog_timeout_seconds
        deadline = time.monotonic() + timeout
        max_response = self.settings.taste_catalog_max_bytes + 4096
        try:
            with httpx.Client(base_url=self.origin, timeout=timeout, trust_env=False, follow_redirects=False) as client:
                with client.stream("GET", PATH, headers=headers) as response:
                    if response.status_code in (401, 403):
                        raise SnapshotError("INTERNAL_AUTH_BLOCKED")
                    if response.status_code == 413:
                        raise SnapshotError("CAPACITY_ERROR")
                    if response.status_code != 200:
                        raise SnapshotError(self._error_code(response, deadline))
                    content_type = response.headers.get("content-type", "").split(";", 1)[0].strip().lower()
                    if content_type != "application/json" or response.headers.get("content-encoding", "identity") != "identity":
                        raise SnapshotError("INVALID_SNAPSHOT")
                    body = bytearray()
                    for chunk in response.iter_raw():
                        if time.monotonic() >= deadline:
                            raise SnapshotError("CATALOG_TIMEOUT")
                        if len(body) + len(chunk) > max_response:
                            raise SnapshotError("CAPACITY_ERROR")
                        body.extend(chunk)
                    return bytes(body), response.headers.get(SIGNATURE, "")
        except httpx.TimeoutException:
            raise SnapshotError("CATALOG_TIMEOUT") from None
        except httpx.HTTPError:
            raise SnapshotError("CATALOG_UNAVAILABLE") from None

    def _error_code(self, response: httpx.Response, deadline: float) -> str:
        if response.status_code not in (500, 503) or response.headers.get("content-encoding", "identity") != "identity":
            return "CATALOG_UNAVAILABLE"
        body = bytearray()
        for chunk in response.iter_raw():
            if len(body) + len(chunk) > 1024 or time.monotonic() >= deadline:
                return "CATALOG_UNAVAILABLE"
            body.extend(chunk)
        try:
            error = parse_json(body)
        except SnapshotError:
            return "CATALOG_UNAVAILABLE"
        code = error.get("code") if isinstance(error, dict) else None
        if response.status_code == 500 and code == "TASTE_SNAPSHOT_INVALID":
            return "INVALID_SNAPSHOT"
        if response.status_code == 503 and code == "TASTE_SNAPSHOT_AUTH_UNAVAILABLE":
            return "INTERNAL_AUTH_BLOCKED"
        return "CATALOG_UNAVAILABLE"
