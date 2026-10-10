import hashlib
import hmac
from dataclasses import dataclass
from uuid import UUID

from fastapi import HTTPException, Request

from app.core.config import get_settings

AI_REPORT_ROLES = frozenset({
    "SUPER_ADMIN", "ADMIN", "MANAGER", "INVENTORY_STAFF", "QC", "ACCOUNTANT", "SALE_STAFF",
})


@dataclass(frozen=True)
class ReportUser:
    id: str
    roles: frozenset[str]


def require_report_user(request: Request) -> ReportUser:
    names = ("X-User-ID", "X-User_Roles", "X-Request-ID", "Authorization")
    # Reject duplicate signed headers, rather than authenticate one interpretation.
    values = []
    for name in names:
        headers = request.headers.getlist(name)
        if len(headers) != 1 or not headers[0].strip():
            raise HTTPException(401, "Missing authenticated user context")
        values.append(headers[0])
    signatures = request.headers.getlist("X-Signature")
    secret = get_settings().internal_header_secret
    if not secret or len(signatures) != 1:
        raise HTTPException(401, "Invalid authenticated user context")
    expected = hmac.new(secret.encode(), "\n".join(values).encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected.encode(), signatures[0].encode()):
        raise HTTPException(401, "Invalid authenticated user context")
    try:
        user_id = str(UUID(values[0]))
    except ValueError:
        raise HTTPException(401, "A human user is required") from None
    roles = frozenset(role.strip().removeprefix("ROLE_") for role in values[1].split(","))
    if not roles.intersection(AI_REPORT_ROLES):
        raise HTTPException(403, "AI Report access denied")
    return ReportUser(user_id, roles)
