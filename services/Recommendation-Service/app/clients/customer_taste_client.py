import base64
import hashlib
import hmac
import re
import time
from typing import Literal
from uuid import UUID, uuid4

import httpx
from pydantic import Field, ValidationError

from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings
from app.schemas.taste_catalog import Digest, Identifier, SnapshotError, StrictDocument, Version, parse_json, utc_timestamp
from app.schemas.taste_recommendation import TasteCriteriaDocument

ACTOR = "service:recommendation-service"
DOMAIN = "hvtra-customer-taste-read-v1"
SIGNATURE = "X-Taste-Read-Signature"
MAX_RESPONSE_BYTES = 20 * 1024
SurveyFailure = Literal["SURVEY_ACCESS_BLOCKED", "SURVEY_UNAVAILABLE", "SURVEY_INVALID"]


class CustomerTasteError(RuntimeError):
    def __init__(self, status: SurveyFailure):
        super().__init__(status)
        self.status = status


class SubmittedTasteSurvey(StrictDocument):
    customerId: Identifier
    status: Literal["SUBMITTED"]
    rowVersion: int = Field(ge=0, le=9223372036854775807)
    submittedAt: str
    taxonomyVersion: Version
    taxonomySha256: Digest
    criteriaSha256: Digest
    criteria: TasteCriteriaDocument


class CustomerTasteClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def latest(self, customer_id: str) -> SubmittedTasteSurvey | None:
        configured = self.settings.customer_taste_read_secret
        secret = configured.get_secret_value() if configured else ""
        catalog_config = self.settings.taste_catalog_sync_secret
        if (not secret.strip() or len(secret.encode()) < 32
                or secret == self.settings.internal_header_secret
                or catalog_config is not None and secret == catalog_config.get_secret_value()):
            raise CustomerTasteError("SURVEY_ACCESS_BLOCKED")
        try:
            origin = httpx.URL(self.settings.customer_service_url or "")
            canonical_customer = str(UUID(customer_id))
        except (httpx.InvalidURL, ValueError):
            raise CustomerTasteError("SURVEY_UNAVAILABLE") from None
        if (origin.scheme not in {"http", "https"} or not origin.host or origin.userinfo
                or origin.path not in ("", "/") or origin.query or origin.fragment):
            raise CustomerTasteError("SURVEY_UNAVAILABLE")
        if customer_id != canonical_customer:
            raise CustomerTasteError("SURVEY_INVALID")

        path = f"/internal/taste-surveys/{customer_id}/latest"
        nonce, timestamp = str(uuid4()), str(int(time.time()))
        try:
            headers = generate_internal_auth_headers(
                user_id=ACTOR, roles="SYSTEM", request_id=nonce, authorization=f"Bearer {ACTOR}",
            )
        except ValueError:
            raise CustomerTasteError("SURVEY_ACCESS_BLOCKED") from None
        signing_key = secret.encode()
        headers.update({
            "X-Taste-Read-Timestamp": timestamp,
            SIGNATURE: self._sign(signing_key, "request", ACTOR, "GET", path, timestamp, nonce),
            "Accept": "application/json",
            "Accept-Encoding": "identity",
        })
        status, body, signature = self._read(origin, path, headers)
        expected = self._sign(signing_key, "response", path, nonce, str(status),
                              base64.b64encode(body).decode("ascii"))
        if not re.fullmatch("[0-9a-f]{64}", signature) or not hmac.compare_digest(signature, expected):
            raise CustomerTasteError("SURVEY_ACCESS_BLOCKED")
        if status == 204:
            return None
        try:
            survey = SubmittedTasteSurvey.model_validate(parse_json(body))
            utc_timestamp(survey.submittedAt)
        except (ValidationError, SnapshotError, RecursionError):
            raise CustomerTasteError("SURVEY_INVALID") from None
        if survey.customerId != customer_id:
            raise CustomerTasteError("SURVEY_INVALID")
        return survey

    @staticmethod
    def _sign(secret: bytes, *fields: str) -> str:
        message = "\n".join((DOMAIN, *fields)).encode()
        return hmac.new(secret, message, hashlib.sha256).hexdigest()

    def _read(self, origin: httpx.URL, path: str, headers: dict[str, str]) -> tuple[int, bytes, str]:
        timeout = self.settings.customer_taste_timeout_seconds
        deadline = time.monotonic() + timeout
        try:
            with httpx.Client(base_url=origin, timeout=timeout, trust_env=False, follow_redirects=False) as client:
                with client.stream("GET", path, headers=headers) as response:
                    status = response.status_code
                    if status in (401, 403):
                        raise CustomerTasteError("SURVEY_ACCESS_BLOCKED")
                    if status not in (200, 204):
                        raise CustomerTasteError("SURVEY_UNAVAILABLE")
                    content_type = response.headers.get("content-type", "").split(";", 1)[0].strip().lower()
                    if (response.headers.get("content-encoding", "identity") != "identity"
                            or status == 200 and content_type != "application/json"):
                        raise CustomerTasteError("SURVEY_INVALID")
                    body = bytearray()
                    for chunk in response.iter_raw():
                        if time.monotonic() >= deadline:
                            raise CustomerTasteError("SURVEY_UNAVAILABLE")
                        if len(body) + len(chunk) > MAX_RESPONSE_BYTES:
                            raise CustomerTasteError("SURVEY_INVALID")
                        body.extend(chunk)
                    if status == 204 and body:
                        raise CustomerTasteError("SURVEY_INVALID")
                    return status, bytes(body), response.headers.get(SIGNATURE, "")
        except httpx.HTTPError:
            raise CustomerTasteError("SURVEY_UNAVAILABLE") from None
