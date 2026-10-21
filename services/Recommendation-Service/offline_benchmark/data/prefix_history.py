"""Read-only snapshot with full-row fingerprints of all Recommendation tables."""
import hashlib
import json

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from app.models.interaction_event import InteractionEvent
from app.models.product import Product
from offline_benchmark.data.db_audit import TABLES
from offline_benchmark.data.db_loader import read_snapshot


def fingerprint_tables(db):
    counts, hashes = {}, {}
    for model in TABLES:
        table = model.__table__
        checksum, count = hashlib.sha256(), 0
        for row in db.execute(select(table).order_by(*table.primary_key.columns)):
            checksum.update(json.dumps(list(row), default=str, ensure_ascii=False,
                                       separators=(",", ":")).encode("utf-8"))
            checksum.update(b"\n")
            count += 1
        counts[table.name], hashes[table.name] = count, checksum.hexdigest()
    return counts, hashes


def load_prefix_context(env_file=None):
    from app.core.settings import Settings

    if env_file is not None and not env_file.is_file():
        raise ValueError("Selected application env file does not exist")
    settings = Settings(_env_file=env_file) if env_file is not None else Settings()
    engine = create_engine(settings.sqlalchemy_database_url, echo=False,
                           connect_args={"connect_timeout": 10})
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            # @@transaction_read_only exposes the SESSION default, not a one-shot
            # SET TRANSACTION characteristic. Set the default on this dedicated
            # connection and explicitly require READ ONLY on the actual snapshot.
            connection.exec_driver_sql("SET SESSION TRANSACTION READ ONLY")
            connection.exec_driver_sql("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            try:
                readonly = int(connection.exec_driver_sql("SELECT @@transaction_read_only").scalar())
                if readonly != 1:
                    raise ValueError("MySQL did not confirm a read-only transaction")
                with Session(bind=connection, autoflush=False) as db:
                    snapshot = read_snapshot(db)
                    counts, hashes = fingerprint_tables(db)
                    event_counts = {row[0]: int(row[1]) for row in db.execute(
                        select(InteractionEvent.event_type, func.count())
                        .group_by(InteractionEvent.event_type))}
                    labels = {row.id: {"code": row.product_code, "name": row.name}
                              for row in db.execute(select(Product.id, Product.product_code, Product.name))}
                    return snapshot, {"database": settings.mysql_database,
                                      "table_counts": counts, "table_sha256": hashes,
                                      "event_type_counts": event_counts,
                                      "product_labels": labels,
                                      "server_confirmed_read_only": True}
            finally:
                connection.rollback()
    finally:
        engine.dispose()
