"""Versioned, typed canonicalization for pure receipt helpers (no persistence)."""
from dataclasses import dataclass
from decimal import Decimal
import hashlib
import hmac
import json
import re
from typing import Mapping

CANONICAL_POLICY = "hvtra-json-lexical-v1"


class ReceiptPolicyError(ValueError):
    """Controlled diagnostic; never includes an input value or key material."""


class MissingHashKeyError(ReceiptPolicyError):
    pass


@dataclass(frozen=True)
class Digest:
    value: str
    policy_version: str
    key_version: str | None = None


def policy_name(value: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9_.-]{1,64}", value):
        raise ReceiptPolicyError("INVALID_POLICY_VERSION")
    return value


def json_bytes(value) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"),
                      sort_keys=True).encode("ascii")


def pointer(path: str, key: str) -> str:
    return path + "/" + key.replace("~", "~0").replace("/", "~1")


def typed_tree(value, tokens=None, path=""):
    """Tag types; preserve raw number spelling rather than JSON/float rendering."""
    if value is None:
        return ["null"]
    if type(value) is bool:
        return ["bool", value]
    if isinstance(value, str):
        return ["string", value]
    if isinstance(value, (int, Decimal)):
        if isinstance(value, Decimal) and not value.is_finite():
            raise ReceiptPolicyError("NONFINITE_NUMBER")
        evidence = (tokens or {}).get(path)
        token = evidence["token"] if evidence else getattr(value, "json_token", str(value))
        kind = evidence["kind"] if evidence else ("integer" if type(value) is int else "decimal")
        return [kind, token]
    if isinstance(value, list):
        return ["array", [typed_tree(v, tokens, path + "/" + str(i)) for i, v in enumerate(value)]]
    if isinstance(value, dict) and all(isinstance(k, str) for k in value):
        return ["object", [[k, typed_tree(value[k], tokens, pointer(path, k))] for k in sorted(value)]]
    # A native float has already lost wire evidence. Never claim it is lossless.
    raise ReceiptPolicyError("UNSUPPORTED_CANONICAL_TYPE")


def canonical_bytes(value, *, policy_version=CANONICAL_POLICY) -> bytes:
    return json_bytes([policy_name(policy_version),
                       typed_tree(value, getattr(value, "numeric_tokens", None))])


def sha256_digest(domain: str, value, *, policy_version: str) -> Digest:
    preimage = json_bytes([policy_name(domain), policy_name(policy_version), value])
    return Digest(hashlib.sha256(preimage).hexdigest(), policy_version)


def keyed_digest(domain: str, value, *, keys: Mapping[str, bytes], key_version: str,
                 policy_version=CANONICAL_POLICY) -> Digest:
    policy_name(key_version)
    key = keys.get(key_version)
    if key is None:
        raise MissingHashKeyError("HASH_KEY_UNAVAILABLE")
    if not isinstance(key, bytes) or len(key) < 16:
        raise ReceiptPolicyError("INVALID_HASH_KEY")
    preimage = json_bytes([policy_name(domain), policy_name(policy_version), key_version, value])
    return Digest(hmac.new(key, preimage, hashlib.sha256).hexdigest(), policy_version, key_version)
