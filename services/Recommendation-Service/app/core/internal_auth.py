import hashlib
import hmac
import uuid
import time

from app.core.config import settings


def generate_internal_auth_headers(
    user_id: str | None = None,
    roles: str | None = None,
    request_id: str | None = None,
    authorization: str | None = None,
    *, method: str | None = None, path: str | None = None, query: str = "",
    timestamp: int | None = None, nonce: str | None = None,
    customer_id: str = "", token_expires_at: int = 0,
) -> dict[str, str]:
    service_name = settings.internal_service_name
    if not settings.internal_header_secret or settings.internal_header_secret == "dev-internal-header-secret":
        raise ValueError("Explicit internal authentication secret is required")
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

    headers = {
        "X-User-ID": uid,
        "X-User_Roles": r,
        "X-Request-ID": req_id,
        "Authorization": auth,
        "X-Signature": signature,
    }
    if method is not None or path is not None:
        if not method or not path:
            raise ValueError("Both method and downstream path are required")
        from app.core.recommendation_auth import context_text
        extension = (str(int(time.time()) if timestamp is None else timestamp),
                     nonce or str(uuid.uuid4()), customer_id, str(token_expires_at))
        headers.update(zip(("X-Internal-Timestamp", "X-Internal-Nonce", "X-Customer-ID", "X-Token-Expires-At"), extension))
        headers['X-Context-Signature'] = hmac.new(settings.internal_header_secret.encode(),
            context_text((uid, r, req_id, auth), extension, method, path, query).encode(), hashlib.sha256).hexdigest()
    return headers
