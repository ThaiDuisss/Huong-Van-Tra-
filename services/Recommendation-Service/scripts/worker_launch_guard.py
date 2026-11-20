"""Local launch authorization only. Never import app, SQLAlchemy or Kafka here."""
from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import stat
from types import MappingProxyType
from typing import Mapping


APPROVAL_DIRECTORY = Path("/run/hvtra-worker-approval")
APPROVAL_FILE = APPROVAL_DIRECTORY / "approved-binding.json"
PASSWORD_FILE = Path("/run/secrets/recommendation_worker_mysql_password")
ORDER_KEYRING_FILE = Path("/run/secrets/recommendation_worker_order_kafka_keys")
GATES = {f"G{i:02}" for i in range(1, 9)}
TARGET_FIELDS = {
    "environment": "HVTRA_WORKER_ENVIRONMENT",
    "writer_role": "HVTRA_WORKER_WRITER_ROLE",
    "binding_revision": "HVTRA_WORKER_BINDING_REVISION",
    "kafka_cluster_id": "HVTRA_KAFKA_CLUSTER_ID",
    "kafka_security_protocol": "HVTRA_WORKER_KAFKA_SECURITY_PROTOCOL",
    "order_stream_id": "HVTRA_ORDER_STREAM_ID",
    "return_stream_id": "HVTRA_RETURN_STREAM_ID",
    "mysql_server_id": "HVTRA_MYSQL_SERVER_ID",
    "kafka_bootstrap_servers": "KAFKA_BOOTSTRAP_SERVERS",
    "kafka_group_id": "KAFKA_GROUP_ID",
    "kafka_order_completed_topic": "KAFKA_ORDER_COMPLETED_TOPIC",
    "kafka_return_completed_topic": "KAFKA_RETURN_COMPLETED_TOPIC",
    "kafka_auto_offset_reset": "KAFKA_AUTO_OFFSET_RESET",
    "mysql_host": "MYSQL_HOST",
    "mysql_port": "MYSQL_PORT",
    "mysql_database": "MYSQL_DATABASE",
    "mysql_user": "MYSQL_USER",
}


class LaunchDenied(Exception):
    """Only controlled reason codes, never input values or underlying exceptions."""

    def __init__(self, code, reason):
        self.exit_code = code
        self.reason = reason
        super().__init__(reason)


@dataclass(frozen=True)
class LaunchPlan:
    # Contains a credential; never include the plan in logging/serialization.
    settings: Mapping[str, str] = field(repr=False)


def _read_owned_file(path, limit):
    """Trust the deployment owner, not a file chosen through worker environment.

    Linux profile runs as a non-root UID. The directory/file must be root-owned,
    non-symlink and not group/world writable. Host/root deployment authority is
    outside this guard's threat boundary. A read-only bind is also in the profile.
    """
    if os.name != "posix" or os.geteuid() == 0 or not hasattr(os, "O_NOFOLLOW"):
        raise LaunchDenied(77, "UNTRUSTED_PROVISIONING")
    try:
        parent = path.parent.stat(follow_symlinks=False)
        if (not stat.S_ISDIR(parent.st_mode) or parent.st_uid != 0
                or parent.st_mode & (stat.S_IWGRP | stat.S_IWOTH)):
            raise LaunchDenied(77, "UNTRUSTED_PROVISIONING")
        # A misprovisioned FIFO must fail its fstat check instead of blocking open.
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, "rb") as stream:
            info = os.fstat(stream.fileno())
            if (not stat.S_ISREG(info.st_mode) or info.st_uid != 0
                    or info.st_mode & (stat.S_IWGRP | stat.S_IWOTH)):
                raise LaunchDenied(77, "UNTRUSTED_PROVISIONING")
            data = stream.read(limit + 1)
        if not data or len(data) > limit:
            raise LaunchDenied(78, "PROVISIONED_FILE_INVALID")
        return data
    except LaunchDenied:
        raise
    except (OSError, ValueError):
        raise LaunchDenied(78, "PROVISIONED_FILE_UNAVAILABLE") from None


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError()
        result[key] = value
    return result


def _reject_constant(_value):
    raise ValueError()


def _text(value):
    return (isinstance(value, str) and 0 < len(value) <= 1024
            and value == value.strip() and not any(ord(c) < 33 for c in value))


def _time(value):
    if not isinstance(value, str):
        raise ValueError()
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError()
    return parsed.astimezone(timezone.utc)


def _binding(data, now):
    try:
        value = json.loads(data, object_pairs_hook=_unique_pairs,
                           parse_constant=_reject_constant)
        if set(value) != {"schema_version", "approval", "target"}:
            raise ValueError()
        if type(value["schema_version"]) is not int or value["schema_version"] != 1:
            raise ValueError()
        approval, target = value["approval"], value["target"]
        if set(approval) != {"status", "approval_id", "approved_by", "not_before",
                             "expires_at", "gate_evidence"}:
            raise ValueError()
        if approval["status"] != "APPROVED":
            raise LaunchDenied(77, "BINDING_NOT_APPROVED")
        if not all(_text(approval[key]) for key in ("approval_id", "approved_by")):
            raise ValueError()
        if not _time(approval["not_before"]) <= now < _time(approval["expires_at"]):
            raise LaunchDenied(77, "APPROVAL_OUTSIDE_WINDOW")
        gates = approval["gate_evidence"]
        if set(gates) != GATES or not all(_text(ref) for ref in gates.values()):
            raise ValueError()
        if set(target) != set(TARGET_FIELDS) or not all(_text(v) for v in target.values()):
            raise ValueError()
        return target
    except LaunchDenied:
        raise
    except (ValueError, TypeError, KeyError, AttributeError, UnicodeError, RecursionError):
        raise LaunchDenied(78, "BINDING_INVALID") from None


def authorize(environment, *, read_file=_read_owned_file, now=None):
    """None means intentionally disabled. No defaults may select a target."""
    enabled = environment.get("HVTRA_WORKER_ENABLED")
    if enabled in (None, "", "false"):
        return None
    if enabled != "true":
        raise LaunchDenied(64, "OPT_IN_INVALID")
    target = {}
    for field_name, variable in TARGET_FIELDS.items():
        candidate = environment.get(variable)
        if not _text(candidate):
            raise LaunchDenied(78, "REQUIRED_CONFIGURATION_MISSING")
        target[field_name] = candidate
    if environment.get("KAFKA_ENABLE_AUTO_COMMIT") != "false":
        raise LaunchDenied(64, "MANUAL_COMMIT_REQUIRED")
    if target["writer_role"] != "recommendation-kafka-worker":
        raise LaunchDenied(77, "WRITER_ROLE_NOT_AUTHORIZED")
    if (target["environment"] not in {"production", "development", "test"}
            or target["kafka_auto_offset_reset"] not in {"earliest", "latest", "error"}
            or target["kafka_order_completed_topic"] == target["kafka_return_completed_topic"]):
        raise LaunchDenied(64, "CONFIGURATION_INVALID")
    if not re.fullmatch(r"[1-9][0-9]{0,4}", target["mysql_port"]) or int(target["mysql_port"]) > 65535:
        raise LaunchDenied(64, "CONFIGURATION_INVALID")
    if any(char in target["mysql_host"] for char in "/@?#") or any(
            char in target["kafka_bootstrap_servers"] for char in "/@?#"):
        raise LaunchDenied(64, "ENDPOINT_MUST_NOT_CONTAIN_CREDENTIALS")
    # The current factory has no security-protocol/SASL/TLS configuration path.
    # No silently ignored security variables or insecure fallback are accepted.
    if environment.get("HVTRA_WORKER_KAFKA_SECURITY_PROTOCOL") != "PLAINTEXT":
        raise LaunchDenied(78, "UNSUPPORTED_KAFKA_SECURITY_CONFIGURATION")
    for variable in environment:
        if variable.startswith(("KAFKA_SASL_", "KAFKA_SSL_")) or variable == "KAFKA_SECURITY_PROTOCOL":
            raise LaunchDenied(78, "UNSUPPORTED_KAFKA_SECURITY_CONFIGURATION")
    clock = now or datetime.now(timezone.utc)
    approved = _binding(read_file(APPROVAL_FILE, 65536), clock)
    if target != approved:
        raise LaunchDenied(77, "TARGET_NOT_AUTHORIZED")
    try:
        password = read_file(PASSWORD_FILE, 8192).decode("utf-8")
        if password.endswith("\n"):
            password = password[:-1].removesuffix("\r")
        if not password or "\n" in password or "\r" in password or "\x00" in password:
            raise ValueError()
        keys = read_file(ORDER_KEYRING_FILE, 32768).decode("utf-8")
        if not isinstance(json.loads(keys, object_pairs_hook=_unique_pairs), dict):
            raise ValueError()
    except LaunchDenied:
        raise
    except (UnicodeError, ValueError):
        raise LaunchDenied(78, "REQUIRED_SECRET_INVALID") from None
    settings = {variable: target[key] for key, variable in TARGET_FIELDS.items()
                if variable.startswith(("MYSQL_", "KAFKA_"))}
    settings.update(APP_ENVIRONMENT=target["environment"], MYSQL_PASSWORD=password,
                    ORDER_KAFKA_VERIFICATION_KEYS=keys, MYSQL_ECHO_SQL="false", KAFKA_ENABLE_AUTO_COMMIT="false")
    return LaunchPlan(MappingProxyType(settings))
