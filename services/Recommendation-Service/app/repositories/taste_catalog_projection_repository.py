from contextlib import contextmanager
from datetime import datetime, timedelta
import hashlib

from sqlalchemy import select, text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.models.taste_catalog_projection import TasteCatalogProjection
from app.schemas.taste_catalog import SnapshotError, VerifiedSnapshot

SOURCE = "catalog"


class TasteCatalogProjectionRepository:
    def __init__(self, engine: Engine):
        self.engine = engine

    @contextmanager
    def synchronize(self, expected_database: str | None = None):
        # MySQL advisory ownership spans the HTTP request without a write transaction.
        with self.engine.connect() as connection:
            connection.execution_options(isolation_level="AUTOCOMMIT")
            db_name = connection.scalar(text("SELECT DATABASE()"))
            if not db_name or expected_database is not None and db_name != expected_database:
                raise SnapshotError("DATASOURCE_MISMATCH")
            lock_name = hashlib.sha256(f"hvtra:taste:{db_name}".encode()).hexdigest()
            try:
                acquired = connection.scalar(text("SELECT GET_LOCK(:key, 0)"), {"key": lock_name})
            except BaseException:
                # The server might have acquired the lock even if its reply was lost.
                connection.invalidate()
                raise
            if acquired != 1:
                raise SnapshotError("SYNC_BUSY" if acquired == 0 else "SYNC_LOCK_UNAVAILABLE")
            try:
                connection.commit()
                yield connection, lock_name
            finally:
                self._release(connection, lock_name)

    def _release(self, connection: Connection, lock_name: str) -> None:
        if connection.invalidated:
            return
        try:
            connection.rollback()
            result = connection.scalar(text("SELECT RELEASE_LOCK(:key)"), {"key": lock_name})
            if result != 1:
                connection.invalidate()
        except SQLAlchemyError:
            connection.invalidate()

    def publish(self, connection: Connection, lock_name: str, snapshot: VerifiedSnapshot,
                ttl: timedelta) -> tuple[str, int]:
        connection.execution_options(isolation_level="READ COMMITTED")
        with Session(bind=connection) as session, session.begin():
            owned = session.scalar(text("SELECT IS_USED_LOCK(:key) = CONNECTION_ID()"), {"key": lock_name})
            if owned != 1:
                raise SnapshotError("SYNC_LOCK_LOST")
            now = session.scalar(text("SELECT UTC_TIMESTAMP(6)"))
            if not isinstance(now, datetime):
                raise SnapshotError("PROJECTION_UNAVAILABLE")
            read_at = snapshot.source_read_at.replace(tzinfo=None)
            if now - read_at >= ttl or read_at - now > timedelta(seconds=30):
                raise SnapshotError("STALE")
            projection = session.scalar(select(TasteCatalogProjection)
                                        .where(TasteCatalogProjection.source_key == SOURCE).with_for_update())
            if projection is None:
                session.add(TasteCatalogProjection(
                    source_key=SOURCE, projection_revision=1, source_read_at=read_at, synced_at=now,
                    payload_sha256=snapshot.digest, payload=snapshot.payload))
                return "PUBLISHED", 1
            revision = projection.projection_revision
            if projection.payload_sha256 == snapshot.digest and projection.payload == snapshot.payload:
                if max(now - projection.synced_at, now - projection.source_read_at) >= ttl / 4:
                    projection.source_read_at = read_at
                    projection.synced_at = now
                return "UNCHANGED", revision
            projection.projection_revision = revision + 1
            projection.source_read_at = read_at
            projection.synced_at = now
            projection.payload_sha256 = snapshot.digest
            projection.payload = snapshot.payload
            return "PUBLISHED", revision + 1

    def read(self) -> TasteCatalogProjection | None:
        with Session(self.engine) as session:
            return session.get(TasteCatalogProjection, SOURCE)
