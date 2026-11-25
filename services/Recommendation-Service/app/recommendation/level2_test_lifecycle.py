"""Explicit, signed activation of staging models in a separate lifecycle test registry."""
from contextlib import contextmanager
from datetime import datetime, timezone
from hashlib import sha256
import hmac
import json
import logging
from pathlib import Path
import sqlite3
from uuid import UUID

from app.recommendation.level2_demo_registry import APPROVED, checked_document, validate_model

logger = logging.getLogger(__name__)
DOMAIN = b"hvtra-l204a-lifecycle-test-v1\n"


class TestLifecycle:
    def __init__(self, directory, jobs, key, baseline):
        root = Path(directory)
        if (not root.is_absolute() or not root.is_dir() or root.resolve().name != "l204a-lifecycle-test"
                or len(key.encode()) < 32):
            raise ValueError("ISOLATED_LIFECYCLE_TEST_REGISTRY_REQUIRED")
        self.path = root.resolve() / "versions.sqlite3"
        self.jobs = jobs
        self.key = key.encode()
        release = baseline["payload"]["taxonomies"][0]
        self._attributes = tuple(a["attributeCode"] for a in release["manifest"]["attributes"])
        self._taxonomy, self._catalog = release["manifestSha256"], baseline["payloadSha256"]
        self._update_schema()

    def _update_schema(self):
        with self._connection() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS versions
                (mode TEXT PRIMARY KEY, active TEXT, previous TEXT)""")
            db.execute("""CREATE TABLE IF NOT EXISTS approval_events
                (approval_id TEXT PRIMARY KEY, mode TEXT NOT NULL, recorded_at TEXT NOT NULL,
                 command TEXT NOT NULL, approval TEXT NOT NULL)""")

    @contextmanager
    def _connection(self):
        connection = sqlite3.connect(self.path, timeout=5)
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def _verified(self, envelope, *, command):
        approval = envelope["approval"]
        raw = json.dumps(approval, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")
        signature = hmac.new(self.key, DOMAIN + raw, sha256).hexdigest()
        if (not hmac.compare_digest(signature, envelope["signature"])
                or approval["command"] != command or approval["mode"] not in APPROVED
                or approval["scope"] != "LIFECYCLE_TEST_ONLY"):
            raise ValueError("EXPLICIT_TEST_APPROVAL_REQUIRED")
        for key in ("approval_id", "approved_by"):
            if str(UUID(approval[key])) != approval[key]:
                raise ValueError("INVALID_APPROVAL_IDENTITY")
        issued = datetime.fromisoformat(approval["approved_at"])
        if issued.tzinfo is None or issued > datetime.now(timezone.utc):
            raise ValueError("INVALID_APPROVAL_TIMESTAMP")
        return approval

    def _model(self, envelope, mode):
        approval = self._verified(envelope, command="activate")
        job = str(UUID(approval["job_id"]))
        state = self.jobs.state(job)
        if (approval["mode"] != mode or not state or state["mode"] != mode
                or state["status"] != "SUCCEEDED" or state["model_version"] != approval["model_version"]):
            raise ValueError("APPROVAL_JOB_MISMATCH")
        artifact = checked_document(self.jobs.root / f"{job}.json", approval["artifact_sha256"])
        evidence = checked_document(self.jobs.root / f"{job}.evaluation.json", approval["evaluation_sha256"])
        expected = {
            "job_id": job, "mode": mode, "horizon_days": APPROVED[mode][4],
            "model_version": state["model_version"], "dataset_fingerprint": approval["dataset_fingerprint"],
            "artifact_sha256": approval["artifact_sha256"], "provenance": "SIMULATED_DEMO",
            "scope": "VALIDATION_ONLY", "quality_promotion": "BLOCKED",
        }
        if (artifact["job_id"] != job or artifact["dataset_fingerprint"] != approval["dataset_fingerprint"]
                or any(evidence.get(k) != v for k, v in expected.items())):
            raise ValueError("APPROVAL_EVIDENCE_MISMATCH")
        return validate_model(artifact, mode, self._attributes, self._taxonomy, self._catalog,
                              version=state["model_version"])

    def _pointers(self, mode):
        with self._connection() as db:
            row = db.execute("SELECT active, previous FROM versions WHERE mode=?", (mode,)).fetchone()
        return tuple(json.loads(item) if item else None for item in row) if row else (None, None)

    def resolve(self, mode, approved_model):
        active, _ = self._pointers(mode)
        if not active:
            return approved_model
        try:
            return self._model(active, mode), None
        except (OSError, ValueError, KeyError, TypeError):
            logger.error("isolated_l2 mode=%s status=TEST_REGISTRY_ARTIFACT_INVALID", mode)
            return None, "TEST_REGISTRY_ARTIFACT_INVALID"

    def versions(self, mode, approved_model):
        active, previous = self._pointers(mode)
        fallback = approved_model[0].version if approved_model[0] is not None else None
        return {
            "active": active["approval"]["model_version"] if active else fallback,
            "previous": previous["approval"]["model_version"] if previous else fallback if active else None,
        }

    def change(self, envelope):
        command = envelope["approval"]["command"]
        if command not in {"activate", "rollback"}:
            raise ValueError("UNKNOWN_LIFECYCLE_COMMAND")
        approval = self._verified(envelope, command=command)
        mode = approval["mode"]
        if command == "activate":
            self._model(envelope, mode)
        with self._connection() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT active, previous FROM versions WHERE mode=?", (mode,)).fetchone()
            active, previous = row or (None, None)
            current = json.loads(active)["approval"]["model_version"] if active else None
            if current != approval["expected_active_test_version"] or (command == "rollback" and not active):
                raise ValueError("TEST_REGISTRY_POINTER_CHANGED")
            target = json.dumps(envelope, separators=(",", ":")) if command == "activate" else previous
            if command == "rollback" and target:
                self._model(json.loads(target), mode)
            db.execute("INSERT INTO approval_events VALUES (?,?,?,?,?)",
                       (approval["approval_id"], mode, datetime.now(timezone.utc).isoformat(), command,
                        json.dumps(envelope, separators=(",", ":"))))
            db.execute("INSERT OR REPLACE INTO versions VALUES (?,?,?)", (mode, target, active))


def load_test_lifecycle(settings, baseline):
    if not settings.level2_lifecycle_test_directory:
        return None
    if (settings.app_environment != "test" or not settings.level2_demo_training_directory
            or not settings.level2_lifecycle_approval_secret):
        raise ValueError("ISOLATED_LIFECYCLE_TEST_CONFIGURATION_REQUIRED")
    from app.training.level2_demo_training import Jobs
    key = settings.level2_lifecycle_approval_secret.get_secret_value()
    if key == settings.internal_header_secret:
        raise ValueError("SEPARATE_LIFECYCLE_APPROVAL_SECRET_REQUIRED")
    return TestLifecycle(settings.level2_lifecycle_test_directory,
                         Jobs(Path(settings.level2_demo_training_directory)), key, baseline)
