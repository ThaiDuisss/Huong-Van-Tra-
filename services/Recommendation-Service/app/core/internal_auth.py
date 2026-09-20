import hashlib
import hmac
import uuid

from app.core.config import settings


def generate_internal_auth_headers(
    user_id: str | None = None,
    roles: str | None = None,
    request_id: str | None = None,
    authorization: str | None = None,
) -> dict[str, str]:
    service_name = settings.internal_service_name
    uid = user_id or f"service:{service_name}"
    r = roles or "SYSTEM"
    req_id = request_id or str(uuid.uuid4())
    auth = authorization or f"Bearer service:{service_name}"

    raw = "\n".join([
        uid,
        r,
        req_id,
        auth,
    ])

    signature = hmac.new(
        settings.internal_header_secret.encode("utf-8"),
        raw.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()

    return {
        "X-User-ID": uid,
        "X-User_Roles": r,
        "X-Request-ID": req_id,
        "Authorization": auth,
        "X-Signature": signature,
    }