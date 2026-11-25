"""Runtime source adapter for approved isolated serving. All SQL here is read-only."""
from collections import Counter
from datetime import datetime, timedelta, timezone
import ipaddress
from uuid import UUID

import httpx
from sqlalchemy import case, func, select, text

from app.clients.customer_taste_client import CustomerTasteClient, CustomerTasteError
from app.models.business_report import CompletedPurchaseOrder
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.recommendation.taste_ranking_features import MIN_VALID_ORDERS, eligible_products
from app.recommendation.v1_model import PurchaseCount, V1ModelError, build_artifact
from app.repositories.explore_history_repository import ExploreHistoryError, ExploreHistoryRepository, receipt_products
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.schemas.explore_recommendation import ExploreProducts, ServingDetails
from app.schemas.taste_catalog import utc_timestamp
from app.service.level2_availability import AvailabilityUnavailable, Level2Availability, variants_by_product
from app.service.level2_demo_serving import DemoServing, QuerySource
from app.service.taste_catalog_sync_service import get_taste_catalog_sync


def validate_live_origins(settings):
    for origin in (settings.catalog_service_url, settings.customer_service_url, settings.level2_inventory_service_url):
        url = httpx.URL(origin or "")
        if (url.scheme not in ("http", "https") or not url.port or url.userinfo or url.path != "/"
                or url.query or url.fragment or not ipaddress.ip_address(url.host or "").is_loopback):
            raise ValueError("ISOLATED_LIVE_AUTHORITY_ORIGIN_REQUIRED")
    if not settings.level2_demo_mysql_server_uuid:
        raise ValueError("ISOLATED_LIVE_DB_UUID_REQUIRED")
    UUID(settings.level2_demo_mysql_server_uuid)


class LiveSnapshot(QuerySource):
    def __init__(self, db, cutoff, products, available, baseline, history, taste_client, projection, mode):
        self.db, self.cutoff = db, cutoff
        self.products = {p.product_id: p for p in products}
        self.available, self.projection = available, projection
        self._history, self._taste_client, self._mode = history, taste_client, mode
        self.attributes = tuple(row["attributeCode"] for row in baseline["payload"]["taxonomies"][0]["manifest"]["attributes"])
        self.taxonomy_hash = baseline["payload"]["taxonomies"][0]["manifestSha256"]
        self.categories = {row["product"]["productId"]: row["product"]["categoryCode"] for row in baseline["entries"]}
        self.tea_types = {row["product"]["productId"]: row["product"]["group"] for row in baseline["entries"]}
        self.approved_tags = {}
        if (projection.payload and projection.status == "READY" and projection.source_read_at is not None
                and projection.source_read_at <= cutoff):
            for variant in projection.rankable_variants(baseline["payload"]["taxonomies"][0]["versionCode"]):
                values = variant.profile.sensoryValues.get("TEA_TYPE", ())
                if variant.product.id in self.products:
                    self.approved_tags[variant.product.id] = tuple(v for v in values if v not in ("UNKNOWN", "NOT_APPLICABLE"))
        if not self.products.keys() <= self.categories.keys():
            raise ExploreHistoryError("ISOLATED_BASELINE_PRODUCT_DRIFT")
        self.categories.update((p.product_id, p.category_code) for p in products)
        self.popularity, self.recent, self.category_popularity = Counter(), Counter(), Counter()
        before = cutoff.replace(tzinfo=None)
        recent = before - timedelta(days=30)
        events = InteractionEvent
        source_id, source_customer = events.source_event_id, events.customer_id
        if db.get_bind().dialect.name == "mysql":
            # The legacy interaction table and durable receipts use different collations.
            source_id = source_id.collate("utf8mb4_0900_ai_ci")
            source_customer = source_customer.collate("utf8mb4_0900_ai_ci")
        # Only canonical purchases linked to the same durable completion. Exclude guests as at training.
        records = db.execute(
            select(events.customer_id, events.product_id, func.count(), func.sum(case((events.event_time > recent, 1), else_=0)))
            .join(CompletedPurchaseOrder, CompletedPurchaseOrder.source_event_id == source_id)
            .where(events.event_type == InteractionEventType.PURCHASE.value, events.customer_id.is_not(None),
                   source_customer == CompletedPurchaseOrder.customer_id, events.event_time <= before,
                   events.created_at <= before, CompletedPurchaseOrder.observed_at <= before,
                   CompletedPurchaseOrder.occurred_at <= before)
            .group_by(events.customer_id, events.product_id)
        )
        counts = []
        for customer, pid, count, recent_count in records:
            if pid not in self.categories:
                raise ExploreHistoryError("ISOLATED_BASELINE_HISTORY_DRIFT")
            counts.append(PurchaseCount(customer, pid, count))
            self.popularity[pid] += count
            self.recent[pid] += recent_count
            self.category_popularity[self.categories[pid]] += count
        self.component_model = build_artifact(products, tuple(counts)) if available else None

    def history(self, customer_id):
        return self._history

    def observed_orders(self, customer_id):
        before = self.cutoff.replace(tzinfo=None)
        table = CompletedPurchaseOrder
        rows = self.db.execute(select(table.occurred_at, table.evidence_hash, table.lines)
                               .where(table.customer_id == customer_id, table.occurred_at <= before,
                                      table.observed_at <= before)
                               .order_by(table.occurred_at, table.id))
        return tuple(ids for row in rows if (ids := tuple(
            pid for _, pid, _, _ in receipt_products(customer_id, *row)
        )))

    def effective_survey(self, customer_id):
        # The approved EXPLORE model ignores Taste, so it needs no live survey dependency.
        if self._mode == "EXPLORE":
            return None, None
        try:
            survey = self._taste_client.latest(customer_id)
        except CustomerTasteError as error:
            raise ValueError(error.status) from None
        if (self.projection.status not in {"READY", "EMPTY"} or self.projection.source_read_at is None
                or self.projection.source_read_at > self.cutoff or self.projection.payload is None
                or not any(t.manifestSha256 == self.taxonomy_hash for t in self.projection.payload.taxonomies)):
            raise ValueError("CATALOG_AUTHORITY_UNAVAILABLE")
        if survey is None:
            return None, None
        linked_at = utc_timestamp(survey.effectiveLinkedAt) if survey.effectiveLinkedAt else None
        return survey, linked_at


class LiveDemoServing:
    def __init__(self, settings, baseline, models, *, lifecycle=None):
        validate_live_origins(settings)
        self.settings, self.baseline, self.models = settings, baseline, models
        self.lifecycle = lifecycle
        self.enabled = settings.level2_demo_scoring_enabled
        self.history = ExploreHistoryRepository()
        self.catalog = V1RecommendationRepository()
        self.taste = CustomerTasteClient(settings)
        self.availability = Level2Availability(settings)

    def verify_database(self, db):
        if db is None or db.get_bind().dialect.name != "mysql":
            raise ExploreHistoryError("ISOLATED_LIVE_DB_REQUIRED")
        identity = db.execute(text("SELECT @@GLOBAL.server_uuid, DATABASE()")).one()
        if tuple(identity) != (self.settings.level2_demo_mysql_server_uuid, self.settings.mysql_database):
            raise ExploreHistoryError("ISOLATED_LIVE_DB_IDENTITY_MISMATCH")
    def _history(self, db, customer_id, cutoff):
        self.verify_database(db)
        return self.history.history(db, customer_id, cutoff.replace(tzinfo=None))

    def valid_orders(self, db, customer_id):
        return self._history(db, customer_id, datetime.now(timezone.utc)).valid_order_count

    def recommend(self, response, customer_id, mode, limit, *, db=None):
        cutoff = datetime.now(timezone.utc)
        history = self._history(db, customer_id, cutoff)
        base = dict(customer_id=customer_id, valid_order_count=history.valid_order_count)
        if history.valid_order_count < MIN_VALID_ORDERS:
            return response(status="INSUFFICIENT_VALID_ORDERS", **base)
        projection = get_taste_catalog_sync().read()
        products = self.catalog.active_catalog(db)
        product_ids = {p.product_id for p in products}
        mirror = {v: p for v, p in self.history.commercial_variants(db).items() if p in product_ids}
        # Availability covers the whole sellable pool so Taste features are scoped the same as training.
        try:
            available = variants_by_product(self.availability.available_variants(mirror))
        except AvailabilityUnavailable:
            return response(status="ELIGIBLE", **base, product=ExploreProducts(
                status="AVAILABILITY_UNAVAILABLE", serving=ServingDetails(
                    candidate_count=0, serving_provenance="SIMULATED_DEMO", cutoff=cutoff,
                    data_source="LIVE_ISOLATED", fallback_reason="INVENTORY_AUTHORITY_UNAVAILABLE",
                ),
            ))
        if not eligible_products(mode, set(available), history):
            return response(status="ELIGIBLE", **base, product=ExploreProducts(
                status="NO_CANDIDATES", serving=ServingDetails(
                    candidate_count=0, serving_provenance="SIMULATED_DEMO", cutoff=cutoff, data_source="LIVE_ISOLATED",
                ),
            ))
        try:
            snapshot = LiveSnapshot(db, cutoff, products, available, self.baseline, history, self.taste, projection, mode)
        except V1ModelError:
            raise ExploreHistoryError("ISOLATED_COMPONENT_BUILD_FAILED") from None
        return DemoServing(snapshot, self.models, self.enabled, data_source="LIVE_ISOLATED",
                           lifecycle=self.lifecycle).recommend(response, customer_id, mode, limit)
