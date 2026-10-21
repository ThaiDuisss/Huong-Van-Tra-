"""Existing four-line HMAC plus a request-bound, expiring v2 extension.

JWT verification remains at Gateway. Human ownership comes ONLY from its signed
customer claim assertion, never subject equality or a raw browser header.
"""
from functools import lru_cache
import hashlib
import hmac
import re
from threading import Lock
import time
from uuid import UUID

from fastapi import HTTPException, Request

from app.core.config import get_settings


BASE = ("X-User-ID", "X-User_Roles", "X-Request-ID", "Authorization")
EXTENSION = ("X-Internal-Timestamp", "X-Internal-Nonce", "X-Customer-ID", "X-Token-Expires-At")
STAFF_ROLES = frozenset({"SUPER_ADMIN", "ADMIN", "MANAGER", "SALE_STAFF"})


def validate_security(settings):
    secret = settings.internal_header_secret
    if not secret.strip() or secret.strip() == "dev-internal-header-secret" or '\n' in secret or '\r' in secret:
        raise ValueError("Explicit internal authentication secret is required")
    if settings.app_environment == "production":
        if len(secret.encode()) < 32:
            raise ValueError("Production internal secret must have at least 32 bytes")
        if (settings.internal_replay_backend != "redis" or not settings.internal_replay_redis_url
                or not settings.internal_replay_redis_url.get_secret_value().strip()):
            raise ValueError("Production requires a shared Redis replay store")
    if not role_set(settings.recommendation_staff_roles) <= STAFF_ROLES:
        raise ValueError("Unsupported recommendation staff permission")


def role_set(value):
    return frozenset(r.strip().removeprefix('ROLE_') for r in value.split(',') if r.strip())


def context_text(values, extension, method, path, query):
    return '\n'.join(('hvtra-internal-v2', *values, *extension, method, path, query))


class MemoryReplay:
    """Explicit isolated development/test only; never a production fallback."""
    def __init__(self, capacity=10000):
        self.entries = {}
        self.lock = Lock()
        self.capacity = capacity

    def accept(self, key, ttl):
        now = time.monotonic()
        with self.lock:
            self.entries = {k:v for k,v in self.entries.items() if v > now}
            if key in self.entries:
                return False
            if len(self.entries) >= self.capacity:
                raise HTTPException(503, "Replay protection unavailable")
            self.entries[key] = now + ttl
            return True


class RedisReplay:
    def __init__(self, url):
        from redis import Redis
        self.client = Redis.from_url(url, socket_timeout=2, socket_connect_timeout=2)

    def accept(self, key, ttl):
        from redis.exceptions import RedisError
        try:
            return bool(self.client.set('hvtra:recommendation:nonce:' + key, '1', nx=True, ex=ttl))
        except RedisError:
            raise HTTPException(503, "Replay protection unavailable") from None


@lru_cache
def _configured_replay_store():
    settings = get_settings()
    validate_security(settings)
    if settings.internal_replay_backend == 'memory':
        return MemoryReplay()
    if not settings.internal_replay_redis_url:
        raise HTTPException(503, "Replay protection unavailable")
    try:
        return RedisReplay(settings.internal_replay_redis_url.get_secret_value())
    except (ValueError, ImportError):
        raise HTTPException(503, "Replay protection unavailable") from None


_store_lock = Lock()


def get_replay_store():
    # functools caching alone can construct two memory stores on concurrent misses.
    with _store_lock:
        return _configured_replay_store()


def single(request, name, allow_empty=False):
    values = request.headers.getlist(name)
    if len(values) != 1 or len(values[0]) > 16384 or '\n' in values[0] or '\r' in values[0]:
        raise HTTPException(401, "Invalid authenticated context")
    if not values[0].strip() and not allow_empty:
        raise HTTPException(401, "Missing authenticated context")
    return values[0]


def verify_context(request):
    settings = get_settings()
    try:
        validate_security(settings)
    except ValueError:
        raise HTTPException(503, "Authentication configuration unavailable") from None
    values = tuple(single(request, n, n == 'X-User_Roles') for n in BASE)
    extension = tuple(single(request, n, n == 'X-Customer-ID') for n in EXTENSION)
    secret = settings.internal_header_secret.encode()
    try:
        path = request.scope['raw_path'].decode('ascii')
        query = request.scope['query_string'].decode('ascii')
    except UnicodeDecodeError:
        raise HTTPException(401, "Invalid request encoding") from None
    for name, raw in (('X-Signature', '\n'.join(values)), ('X-Context-Signature', context_text(
            values, extension, request.method, path, query))):
        supplied = single(request, name)
        if not re.fullmatch('[0-9a-f]{64}', supplied) or not hmac.compare_digest(
                hmac.new(secret, raw.encode(), hashlib.sha256).hexdigest(), supplied):
            raise HTTPException(401, "Invalid authenticated context")
    try:
        timestamp, expiry = int(extension[0]), int(extension[3])
        nonce = str(UUID(extension[1]))
    except ValueError:
        raise HTTPException(401, "Malformed authenticated context") from None
    now = time.time()
    skew = settings.internal_context_max_skew_seconds
    if abs(now - timestamp) > skew:
        raise HTTPException(401, "Expired authenticated context")
    identity, roles = values[0], role_set(values[1])
    if identity.startswith('service:'):
        if roles != {'SYSTEM'} or values[3] != 'Bearer ' + identity or expiry != 0 or extension[2]:
            raise HTTPException(401, "Invalid internal service context")
    else:
        try:
            identity = str(UUID(identity))
        except ValueError:
            raise HTTPException(401, "Invalid user identity") from None
        if not values[3].startswith('Bearer ') or expiry <= now:
            raise HTTPException(401, "Expired or invalid user token")
    key = hashlib.sha256((identity + '\n' + nonce).encode()).hexdigest()
    if not get_replay_store().accept(key, 2*skew+1):
        raise HTTPException(401, "Replayed authenticated context")
    return identity, roles, extension[2], settings


def authorize_customer(customer_id: str, request: Request) -> str:
    identity, roles, mapped, settings = verify_context(request)
    try:
        target = str(UUID(customer_id))
    except ValueError:
        raise HTTPException(422, "Customer ID must be a UUID") from None
    if identity.startswith('service:'):
        allowed = {n.strip() for n in settings.recommendation_internal_callers.split(',') if n.strip()}
        if identity.removeprefix('service:') not in allowed:
            raise HTTPException(403, "Internal caller not authorized")
    elif roles & role_set(settings.recommendation_staff_roles):
        pass  # Explicitly configured cross-customer permission, disabled by default.
    else:
        if not mapped:
            raise HTTPException(503, "Customer identity mapping unavailable")
        try:
            owner = str(UUID(mapped))
        except ValueError:
            raise HTTPException(401, "Invalid verified customer identity") from None
        if owner != target:
            raise HTTPException(403, "Customer access denied")
    return target


def authorize_catalog_sync(request: Request):
    identity, roles, _, settings = verify_context(request)
    allowed = {n.strip() for n in settings.catalog_sync_internal_callers.split(',') if n.strip()}
    if not identity.startswith('service:') or identity.removeprefix('service:') not in allowed or roles != {'SYSTEM'}:
        raise HTTPException(403, "Catalog synchronization denied")
