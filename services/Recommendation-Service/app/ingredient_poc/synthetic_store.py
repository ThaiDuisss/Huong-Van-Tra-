"""Isolated-MySQL-only writer and publisher. No service settings or V1 tables.

Only the owned Docker runner provisions this narrowly scoped test writer. A new
connection verifies server/database/current-user identity before a transaction.
The writer has SELECT/INSERT/UPDATE on the 12 PoC tables; no DDL or DELETE.
"""
from copy import deepcopy
from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
import re
import ssl
import time
from uuid import UUID

import pymysql
from sqlalchemy import bindparam, create_engine, insert, select, text, update
from sqlalchemy.engine import URL
from sqlalchemy.exc import DBAPIError, IntegrityError

from app.ingredient_poc import schema as s
from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.repository import ImportConflict
from app.ingredient_poc.synthetic import TABLES, canonical_json, loads, table_checksums
from app.ingredient_poc.synthetic_validation import validate


@dataclass(repr=False, frozen=True)
class ResearchTarget:
    port: int
    database: str
    server_uuid: str
    shared_server_uuid: str
    username: str
    password: str = field(repr=False)

    def __post_init__(self):
        match = re.fullmatch(r"ingredient_poc_(fresh|upgrade)_([0-9a-f]{12})", self.database)
        if not match or not 1024 <= self.port <= 65535 or self.port in (3306, 33060):
            raise ValueError("only an owned loopback research endpoint is allowed")
        if self.username != "poc_writer_" + match[2]:
            raise ValueError("owned least-privileged writer required")
        if UUID(self.server_uuid) == UUID(self.shared_server_uuid):
            raise ValueError("shared server identity denied")

    def connect(self):
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        tls.check_hostname = False
        tls.verify_mode = ssl.CERT_NONE  # Owned loopback fixture; never shared credentials.
        conn = pymysql.connect(host="127.0.0.1", port=self.port, user=self.username,
                               password=self.password, database=self.database, ssl=tls,
                               connect_timeout=5, read_timeout=30, write_timeout=30,
                               autocommit=False, charset="utf8mb4")
        try:
            with conn.cursor() as q:
                q.execute("SELECT DATABASE(),@@server_uuid,VERSION(),CURRENT_USER(),@@sql_mode")
                db, server, version, user, mode = q.fetchone()
                if (db != self.database or server != self.server_uuid or server == self.shared_server_uuid
                        or not version.startswith("8.4.") or user.split("@")[0] != self.username
                        or not {"STRICT_TRANS_TABLES", "STRICT_ALL_TABLES"}.intersection(mode.split(","))):
                    raise ValueError("research identity/strict mode mismatch; writes denied")
                q.execute("SET SESSION innodb_lock_wait_timeout=3")
                q.execute("SET SESSION lock_wait_timeout=5")
            conn.rollback()
            return conn
        except BaseException:
            conn.close()
            raise

    def engine(self):
        return create_engine(URL.create("mysql+pymysql", username=self.username, password=self.password,
                                        host="127.0.0.1", port=self.port, database=self.database),
                             creator=self.connect, echo=False, pool_pre_ping=True, hide_parameters=True)


def retry_transaction(engine, operation, *, attempts=3):
    """Retry the entire fresh transaction only for MySQL deadlock/lock timeout."""
    if not 1 <= attempts <= 3:
        raise ValueError("at most three whole-transaction attempts")
    for attempt in range(attempts):
        try:
            with engine.begin() as conn:
                return operation(conn)
        except DBAPIError as error:
            code = error.orig.args[0] if error.orig.args else None
            if code not in (1205, 1213) or attempt + 1 == attempts:
                raise
            time.sleep(0.05 * (attempt + 1))


def _database_row(table, row):
    result = dict(row)
    for c in table.c:
        value = result[c.name]
        if isinstance(value, datetime):
            if value.tzinfo is None:
                raise ValueError("naive timestamp at persistence boundary")
            result[c.name] = value.astimezone(timezone.utc).replace(tzinfo=None)
        elif isinstance(value, (dict, list)):
            result[c.name] = json.loads(canonical_json(value))
    return result


def _batch(conn, table, rows):
    for start in range(0, len(rows), 200):
        conn.execute(insert(table), [_database_row(table, r) for r in rows[start:start + 200]])


def read_tables(conn, dataset_id):
    result = {}
    for short in TABLES:
        table = s.metadata.tables["ingredient_poc_" + short]
        rows = [dict(r) for r in conn.execute(select(table).where(table.c.dataset_id == dataset_id)
                                             .order_by(table.c.id)).mappings()]
        for row in rows:
            for k, value in row.items():
                if isinstance(value, datetime):
                    row[k] = value.replace(tzinfo=timezone.utc)
        result[short] = rows
    return result


def _locked_dataset(conn, dataset_id):
    # First data read in every authoring/publish transaction, before any read view.
    return conn.execute(select(s.datasets).where(s.datasets.c.id == dataset_id).with_for_update()).mappings().first()


def _verify_locked(conn, row, bundle):
    if row is None or row["source_kind"] != "SYNTHETIC":
        raise ImportConflict("missing dataset or wrong origin")
    expected = bundle["dataset"]
    for name in ("id", "dataset_name", "schema_version", "source_kind", "source_system",
                 "source_snapshot_hash", "validation_policy_version"):
        if row[name] != expected[name]:
            raise ImportConflict("dataset identity or source checksum differs")
    if row["created_at"].replace(tzinfo=timezone.utc) != expected["created_at"]:
        raise ImportConflict("dataset creation evidence differs")
    for name, value in expected["manifest_json"].items():
        if name != "validation_complete" and canonical_json(row["manifest_json"].get(name)) != canonical_json(value):
            raise ImportConflict("stored manifest differs")
    observed = read_tables(conn, expected["id"])
    if table_checksums(observed) != expected["manifest_json"]["table_checksums"]:
        raise ImportConflict("stored rows differ; no partial overwrite or repair")
    verified = {"dataset": deepcopy(expected), "tables": observed, "evaluation": bundle["evaluation"]}
    checked = validate(verified)
    if row["status"] in ("VALIDATED", "PUBLISHED"):
        manifest = row["manifest_json"]
        if (manifest.get("validation_complete") is not True
                or manifest.get("validated_content_fingerprint") != expected["manifest_json"]["content_fingerprint"]
                or manifest.get("validation_report_hash") != content_hash(checked)):
            raise ImportConflict("frozen validation evidence differs")
    return checked


class SyntheticStore:
    def __init__(self, target):
        if not isinstance(target, ResearchTarget):
            raise TypeError("a verified research target is required, never an arbitrary engine/URL")
        self.target = target
        self.engine = target.engine()

    def close(self):
        self.engine.dispose()

    def import_dataset(self, bundle):
        bundle = loads(canonical_json(bundle))  # Own a stable input, independent of caller mutation.
        report = validate(bundle)
        ds = bundle["dataset"]

        def operation(conn):
            row = _locked_dataset(conn, ds["id"])
            if row is not None:
                if row["status"] not in ("STAGED", "VALIDATED", "PUBLISHED"):
                    raise ImportConflict("terminal rejected dataset cannot be reopened")
                checked = _verify_locked(conn, row, bundle)
                return {"decision": "UNCHANGED", "status": row["status"], "validation": checked}
            _batch(conn, s.datasets, [ds])
            for short in ("units", "ingredients", "products", "customers", "variants", "orders"):
                _batch(conn, s.metadata.tables["ingredient_poc_" + short], bundle["tables"][short])
            envelopes = [{**r, "validation_status": "STAGED"} for r in bundle["tables"]["recipes"]]
            _batch(conn, s.recipes, envelopes)
            _batch(conn, s.recipe_items, bundle["tables"]["recipe_items"])
            conn.execute(update(s.recipes).where(s.recipes.c.id == bindparam("recipe_key"),
                                                 s.recipes.c.dataset_id == ds["id"])
                         .values(validation_status="VALIDATED"),
                         [{"recipe_key": r["id"]} for r in bundle["tables"]["recipes"]])
            _batch(conn, s.sale_items, bundle["tables"]["sale_items"])
            _batch(conn, s.feedback, bundle["tables"]["feedback"])
            checked = _verify_locked(conn, _locked_dataset(conn, ds["id"]), bundle)
            if checked != report:
                raise ImportConflict("validation changed between memory and database")
            return {"decision": "NEW", "status": "STAGED", "validation": checked}

        try:
            return retry_transaction(self.engine, operation)
        except IntegrityError as error:
            raise ImportConflict("business/identity conflict; entire transaction rolled back") from error

    def publish(self, bundle):
        bundle = loads(canonical_json(bundle))
        validate(bundle)

        def operation(conn):
            row = _locked_dataset(conn, bundle["dataset"]["id"])
            checked = _verify_locked(conn, row, bundle)
            if row["status"] == "PUBLISHED":
                return {"decision": "UNCHANGED", "status": "PUBLISHED", "validation": checked}
            if row["status"] != "STAGED":
                raise ImportConflict("publish requires an intact staged dataset")
            manifest = deepcopy(bundle["dataset"]["manifest_json"])
            manifest.update(validation_complete=True, validated_content_fingerprint=manifest["content_fingerprint"],
                            validation_report_hash=content_hash(checked))
            conn.execute(update(s.datasets).where(s.datasets.c.id == row["id"])
                         .values(manifest_json=json.loads(canonical_json(manifest)), status="VALIDATED"))
            # Same lock + transaction: no intermediate externally visible partial publish.
            conn.execute(update(s.datasets).where(s.datasets.c.id == row["id"]).values(status="PUBLISHED"))
            return {"decision": "PUBLISHED", "status": "PUBLISHED", "validation": checked}

        return retry_transaction(self.engine, operation)

    def inspect(self, bundle):
        """Post-publish SELECT verification using the same scoped content checks."""
        validate(bundle)
        with self.engine.connect() as conn:
            row = conn.execute(select(s.datasets).where(s.datasets.c.id == bundle["dataset"]["id"])).mappings().first()
            report = _verify_locked(conn, row, bundle)
            if row["status"] == "PUBLISHED":
                manifest = row["manifest_json"]
                if (manifest.get("validation_complete") is not True
                        or manifest.get("validated_content_fingerprint") != bundle["dataset"]["manifest_json"]["content_fingerprint"]
                        or manifest.get("validation_report_hash") != content_hash(report)):
                    raise ImportConflict("published validation evidence differs")
            return {"status": row["status"], "table_checksums": table_checksums(read_tables(conn, row["id"])),
                    "validation": report}
