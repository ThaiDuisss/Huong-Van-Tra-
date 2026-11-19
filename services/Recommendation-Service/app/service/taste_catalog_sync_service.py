import argparse
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from functools import lru_cache
import logging
from threading import Lock
from typing import Literal

from sqlalchemy.exc import SQLAlchemyError

from app.clients.taste_catalog_client import TasteCatalogClient
from app.core.config import get_settings
from app.core.settings import Settings
from app.database.session import engine
from app.repositories.taste_catalog_projection_repository import TasteCatalogProjectionRepository
from app.schemas.taste_catalog import Payload, Product, Profile, SnapshotError, Variant, verify_payload

logger = logging.getLogger(__name__)
Readiness = Literal["READY", "EMPTY", "UNAVAILABLE", "STALE", "CAPACITY_ERROR"]


@dataclass(frozen=True)
class SyncResult:
    outcome: Literal["PUBLISHED", "UNCHANGED", "SKIPPED", "FAILED"]
    projection_revision: int | None = None
    reason: str | None = None


@dataclass(frozen=True)
class RankableVariant:
    product: Product
    variant: Variant
    profile: Profile


@dataclass(frozen=True)
class ProjectionView:
    status: Readiness
    reason: str | None
    last_sync_error: str | None
    projection_revision: int | None = None
    catalog_snapshot_id: str | None = None
    source_read_at: datetime | None = None
    synced_at: datetime | None = None
    payload: Payload | None = None

    @property
    def supported_taxonomies(self) -> tuple[str, ...]:
        return tuple(self.payload.supportedTaxonomyVersions) if self.payload else ()

    def supports_preparation(self, taxonomy_version: str, preparation_code: str) -> bool:
        return bool(self.payload and any(
            profile.taxonomyVersion == taxonomy_version and profile.preparationCode == preparation_code
            for profile in self.payload.profiles
        ))

    def rankable_variants(self, taxonomy_version: str, preparation_code: str) -> tuple[RankableVariant, ...]:
        if self.status != "READY" or self.payload is None:
            return ()
        variants = {variant.id: variant for variant in self.payload.variants}
        products = {product.id: product for product in self.payload.products}
        return tuple(
            RankableVariant(products[variants[profile.variantId].productId], variants[profile.variantId], profile)
            for profile in self.payload.profiles
            if profile.taxonomyVersion == taxonomy_version and profile.preparationCode == preparation_code
        )


class TasteCatalogSyncService:
    def __init__(self, settings: Settings, repository: TasteCatalogProjectionRepository):
        self.settings = settings
        self.repository = repository
        self.ttl = timedelta(seconds=settings.taste_catalog_ttl_seconds)
        self._state_lock = Lock()
        self._last_error: str | None = None

    def sync_once(self, expected_database: str | None = None) -> SyncResult:
        try:
            client = TasteCatalogClient(self.settings)
            with self.repository.synchronize(expected_database) as (connection, lock):
                try:
                    snapshot = client.fetch()
                    outcome, revision = self.repository.publish(connection, lock, snapshot, self.ttl)
                except (SQLAlchemyError, SnapshotError) as failure:
                    return self._failed(failure)
                # Publish has already committed. Update diagnostics while still owning the sync lock.
                self._record_error(None)
            logger.info("taste_catalog_sync outcome=%s revision=%d", outcome, revision)
            return SyncResult(outcome, revision)
        except (SQLAlchemyError, SnapshotError) as failure:
            return self._failed(failure)

    def _failed(self, failure: SQLAlchemyError | SnapshotError) -> SyncResult:
        reason = failure.code if isinstance(failure, SnapshotError) else "PROJECTION_UNAVAILABLE"
        if reason == "SYNC_BUSY":
            return SyncResult("SKIPPED", reason=reason)
        self._record_error(reason)
        logger.warning("taste_catalog_sync outcome=FAILED reason=%s", reason)
        return SyncResult("FAILED", reason=reason)

    def _record_error(self, error: str | None) -> None:
        with self._state_lock:
            self._last_error = error

    def read(self) -> ProjectionView:
        with self._state_lock:
            last_error = self._last_error
        try:
            row = self.repository.read()
        except SQLAlchemyError:
            return ProjectionView("UNAVAILABLE", "PROJECTION_UNAVAILABLE", last_error)
        if row is None:
            if last_error == "CAPACITY_ERROR":
                return ProjectionView("CAPACITY_ERROR", "NEVER_SYNCHRONIZED", last_error)
            return ProjectionView("UNAVAILABLE", "NEVER_SYNCHRONIZED", last_error)

        read_at = row.source_read_at.replace(tzinfo=timezone.utc)
        synced_at = row.synced_at.replace(tzinfo=timezone.utc)
        now = datetime.now(timezone.utc)
        metadata = dict(projection_revision=row.projection_revision,
                        catalog_snapshot_id="taste-v1-" + row.payload_sha256,
                        source_read_at=read_at, synced_at=synced_at)
        if (max(now - read_at, now - synced_at) >= self.ttl
                or max(read_at - now, synced_at - now) > timedelta(seconds=30)):
            return ProjectionView("STALE", "FRESHNESS_EXPIRED", last_error, **metadata)
        try:
            payload = verify_payload(row.payload, row.payload_sha256,
                                     self.settings.taste_catalog_max_bytes, self.settings.taste_catalog_max_variants)
        except SnapshotError as failure:
            status = "CAPACITY_ERROR" if failure.code == "CAPACITY_ERROR" else "UNAVAILABLE"
            return ProjectionView(status, failure.code, last_error, **metadata)

        if payload.catalogState == "NO_TAXONOMY":
            return ProjectionView("UNAVAILABLE", "NO_TAXONOMY", last_error, **metadata)
        status = "READY" if payload.profiles else "EMPTY"
        return ProjectionView(status, None, last_error, payload=payload, **metadata)


@lru_cache
def get_taste_catalog_sync() -> TasteCatalogSyncService:
    return TasteCatalogSyncService(get_settings(), TasteCatalogProjectionRepository(engine))


def main() -> int:
    parser = argparse.ArgumentParser(description="Refresh the taste projection in a verified development database")
    parser.add_argument("--database", required=True, help="Expected local Recommendation MySQL schema")
    args = parser.parse_args()
    settings = get_settings()
    if settings.app_environment != "development" or settings.mysql_database != args.database:
        parser.error("APP_ENVIRONMENT=development and matching local database configuration are required")
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    result = get_taste_catalog_sync().sync_once(expected_database=args.database)
    return 0 if result.outcome in ("PUBLISHED", "UNCHANGED") else 1


if __name__ == "__main__":
    raise SystemExit(main())
