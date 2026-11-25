"""Isolated point-in-time Recommendation serving backed by reviewed historical exports."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
import ipaddress
import logging
from pathlib import Path

from app.core.settings import Settings
from app.recommendation.level2_demo_features import FeatureContext, Matrix, adapt
from app.recommendation.level2_demo_registry import APPROVAL, APPROVED, BASELINE_SHA256, checked_document, load_demo_models
from app.recommendation.taste_ranking_features import (
    MIN_VALID_ORDERS, Mode, RankingFeatures, eligible_products, level2_hybrid_scores, ranking_features,
)
from app.recommendation.v1_model import CatalogProduct, PurchaseCount, build_artifact
from app.repositories.explore_history_repository import build_history
from app.schemas.explore_recommendation import ExploreProducts, PersonalizedRecommendationResponse, ServingDetails
from app.schemas.recommendation import RecommendationItem
from app.schemas.taste_catalog import utc_timestamp
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch
from app.training.taste_ranking_dataset import EvidenceBundle, Window, _projection, _validate_windows, _verified_availability

logger = logging.getLogger(__name__)
FALLBACK = {"EXPLORE": "POPULARITY", "FAMILIAR": "FREQUENCY_RECENCY"}


@dataclass(frozen=True, slots=True)
class Query:
    rows: tuple[RankingFeatures, ...]
    context: FeatureContext
    taste_status: str


class QuerySource:
    def query(self, customer_id, mode: Mode, history) -> Query | None:
        candidate_ids = eligible_products(mode, set(self.available), history)
        if not candidate_ids:
            return None
        survey, linked_at = self.effective_survey(customer_id)
        if survey:
            taste = PersonalizedTasteFeatures.from_observation(
                survey, self.cutoff, set(self.available), lambda: self.projection,
            )
            if linked_at is None or linked_at > self.cutoff:
                raise ValueError("ISOLATED_SURVEY_LINK_NOT_EFFECTIVE")
            if taste.status in {"SURVEY_INVALID", "TAXONOMY_MISMATCH", "CATALOG_INVALID"}:
                raise ValueError("ISOLATED_SURVEY_CONTRACT")
            wanted = {value for group in survey.criteria.hardGroups if group.attribute == "TEA_TYPE"
                      for value in group.allowedValues}
            wanted.update(item.value for group in survey.criteria.softGroups if group.attribute == "TEA_TYPE"
                          for item in group.priorities)
            effective = max(utc_timestamp(survey.submittedAt), linked_at)
            survey_age = (self.cutoff - effective).total_seconds() / 86400
        else:
            taste, wanted, survey_age = TasteFeatureBatch("SURVEY_MISSING"), set(), None
        if taste.taxonomy_sha256 != self.taxonomy_hash:
            taste = TasteFeatureBatch("TAXONOMY_UNSUPPORTED")
        content, cf = self.component_model.components(history.product_counts)
        scores, _ = level2_hybrid_scores(content, cf, tuple(sorted(candidate_ids)))
        rows = tuple(ranking_features(
            self.products[pid], history, self.cutoff, scores[pid], content[pid], cf[pid], taste, self.available[pid],
        ) for pid in (p.product_id for p in self.component_model.products) if pid in candidate_ids)
        context = FeatureContext(
            history, self.observed_orders(customer_id),
            self.categories, self.tea_types, self.approved_tags, self.popularity, self.recent,
            self.category_popularity, frozenset(wanted), survey_age,
        )
        return Query(rows, context, "SURVEY_MISSING" if not survey else taste.status)

    def fallback(self, mode: Mode, candidate_ids, history):
        if mode == "EXPLORE":
            return {pid: float(self.popularity[pid]) for pid in candidate_ids}
        latest = history.last_retained_purchase
        freq = max(history.product_counts[pid] for pid in candidate_ids)
        days = {pid: (self.cutoff.replace(tzinfo=None) - latest[pid]).total_seconds() / 86400 for pid in candidate_ids}
        span = max(days.values())
        return {pid: (history.product_counts[pid] / freq + (1 - age / span if span else 1.)) / 2
                for pid, age in days.items()}



class PrefixSnapshot(QuerySource):
    """Build cutoff-scoped indexes once. Request scoring never reads outcome rows."""

    def __init__(self, evidence: EvidenceBundle, window: Window, baseline: dict):
        self.cutoff = window.cutoff
        self.projection = _projection(window)
        self.available = _verified_availability(window, evidence)
        if self.available is None:
            raise ValueError("ISOLATED_INVENTORY_AUTHORITY_MISSING")
        payload = self.projection.payload
        if (self.projection.status != "READY" or window.taste_catalog.payloadSha256 != baseline["payloadSha256"]):
            raise ValueError("ISOLATED_CATALOG_AUTHORITY_INVALID")
        release = next(t for t in payload.taxonomies if t.versionCode == evidence.taxonomy_version)
        if release.manifestSha256 != evidence.taxonomy_sha256:
            raise ValueError("ISOLATED_TAXONOMY_MISMATCH")
        self.attributes = tuple(row["attributeCode"] for row in release.manifest["attributes"])
        self.taxonomy_hash = release.manifestSha256
        self.catalog_hash = window.taste_catalog.payloadSha256
        self.products = {
            row.product_id: CatalogProduct(row.product_id, row.product_code, row.name, row.category_code,
                                          tuple(sorted(set(row.ingredient_codes))))
            for row in window.catalog
        }
        self.categories = {p: row.category_code for p, row in self.products.items()}
        profiles = {p.variantId: p for p in payload.profiles}
        self.approved_tags, self.tea_types = {}, {}
        for row in baseline["entries"]:
            pid = row["product"]["productId"]
            profile = profiles.get(row["variant"]["variantId"])
            self.tea_types[pid] = row["product"]["group"]
            if profile is not None:
                values = profile.sensoryValues.get("TEA_TYPE", ())
                self.approved_tags[pid] = tuple(v for v in values if v not in {"UNKNOWN", "NOT_APPLICABLE"})
        if self.tea_types.keys() != self.products.keys():
            raise ValueError("ISOLATED_CATALOG_PRODUCT_DRIFT")

        self.roster = {row.customer_id: row for row in window.customers}
        self.receipts, self.returns, self.purchases = defaultdict(list), defaultdict(list), defaultdict(set)
        for row in evidence.receipts:
            if row.observed_at <= self.cutoff:
                self.receipts[row.customer_id].append(row)
        for row in evidence.returns:
            if row.observed_at <= self.cutoff:
                self.returns[row.customer_id].append(row)
        counts, self.popularity, self.recent = Counter(), Counter(), Counter()
        for row in evidence.interaction_purchases:
            if row.observed_at <= self.cutoff:
                self.purchases[row.customer_id].add(row.product_id)
                counts[row.customer_id, row.product_id] += 1
                self.popularity[row.product_id] += 1
                if (self.cutoff - row.occurred_at).total_seconds() < 30 * 86400:
                    self.recent[row.product_id] += 1
        self.category_popularity = Counter()
        for pid, count in self.popularity.items():
            self.category_popularity[self.categories[pid]] += count
        self.component_model = build_artifact(tuple(self.products.values()), tuple(
            PurchaseCount(customer, product, count) for (customer, product), count in counts.items()
        ))

    def history(self, customer_id):
        return build_history(customer_id, (row.history_row() for row in self.receipts.get(customer_id, ())),
                             self.purchases.get(customer_id, ()),
                             (row.history_row() for row in self.returns.get(customer_id, ())))


    def effective_survey(self, customer_id):
        observation = self.roster.get(customer_id)
        return (observation.survey, observation.linked_at) if observation else (None, None)

    def observed_orders(self, customer_id):
        receipts = sorted(self.receipts.get(customer_id, ()), key=lambda r: (r.occurred_at, r.order_id))
        return tuple(tuple(line["product_id"] for line in r.lines) for r in receipts)


class DemoServing:
    def __init__(self, snapshot, models, enabled, *, data_source="HISTORICAL_SNAPSHOT", lifecycle=None):
        self.snapshot = snapshot
        self.models = models
        self.enabled = enabled
        self.data_source = data_source
        self.lifecycle = lifecycle

    def features(self, mode, query: Query, categories) -> Matrix:
        return adapt(mode, query.rows, query.context, self.snapshot.attributes, categories)

    def valid_orders(self, db, customer_id):
        return self.snapshot.history(customer_id).valid_order_count

    def recommend(self, response, customer_id, mode: Mode, limit, *, db=None) -> PersonalizedRecommendationResponse:
        history = self.snapshot.history(customer_id)
        base = dict(customer_id=customer_id, valid_order_count=history.valid_order_count)
        if history.valid_order_count < MIN_VALID_ORDERS:
            return response(status="INSUFFICIENT_VALID_ORDERS", **base)
        candidates = eligible_products(mode, set(self.snapshot.available), history)
        details = dict(candidate_count=len(candidates), serving_provenance="SIMULATED_DEMO",
                       data_source=self.data_source,
                       cutoff=self.snapshot.cutoff)
        if not candidates:
            return response(status="ELIGIBLE", **base, product=ExploreProducts(
                status="NO_CANDIDATES", serving=ServingDetails(**details),
            ))

        model, reason = self.lifecycle.resolve(mode, self.models[mode]) if self.lifecycle else self.models[mode]
        if not self.enabled:
            reason = "ISOLATED_MODEL_DISABLED"
        if reason is None:
            try:
                query = self.snapshot.query(customer_id, mode, history)
                matrix = self.features(mode, query, model.categories)
                scores = dict(zip((r.product_id for r in query.rows), model.score(matrix), strict=True))
                details.update(
                    algorithm=APPROVED[mode][3], model_version=model.version, outcome_horizon=model.horizon,
                    taste_status=query.taste_status if mode == "FAMILIAR" else "NOT_USED_BY_MODEL",
                    repeat_interval_count=sum(r.repeat_interval_days is not None for r in query.rows),
                )
            except (ArithmeticError, ValueError, KeyError):
                reason = "ISOLATED_FEATURE_OR_INFERENCE_FAILED"
                logger.error("isolated_l2 mode=%s status=%s", mode, reason)
        if reason:
            scores = self.snapshot.fallback(mode, candidates, history)
            details.update(algorithm=FALLBACK[mode], fallback_applied=True, fallback_reason=reason)
        top = sorted(scores, key=lambda pid: (-scores[pid], pid))[:min(limit, 5)]
        items = [
            RecommendationItem(rank=rank, product_id=pid, product_code=self.snapshot.products[pid].product_code,
                               name=self.snapshot.products[pid].name, score=scores[pid])
            for rank, pid in enumerate(top, 1)
        ]
        return response(status="ELIGIBLE", **base, product=ExploreProducts(
            status="PERSONALIZED", items=items, serving=ServingDetails(returned_count=len(items), **details),
        ))


def load_isolated_serving(settings: Settings):
    manifest = settings.level2_demo_manifest
    if not manifest:
        return None
    if (settings.app_environment not in {"test", "development"}
            or (settings.taste_catalog_auto_refresh and not settings.level2_demo_live_enabled)
            or settings.taste_ranking_activation_manifest
            or not settings.mysql_database.startswith("l204_isolated_")
            or not ipaddress.ip_address(settings.mysql_host).is_loopback):
        raise ValueError("ISOLATED_RUNTIME_CONFIGURATION_REQUIRED")
    config = checked_document(Path(manifest), settings.level2_demo_manifest_sha256 or "")
    if config.get("approval") != APPROVAL or config.get("provenance") != "SIMULATED_DEMO":
        raise ValueError("ISOLATED_REHEARSAL_APPROVAL_REQUIRED")
    baseline = checked_document(Path(config["baseline"]), BASELINE_SHA256)
    from app.recommendation.level2_test_lifecycle import load_test_lifecycle
    lifecycle = load_test_lifecycle(settings, baseline)
    if settings.level2_demo_live_enabled:
        from app.database.session import SessionLocal
        from app.service.level2_live_serving import LiveDemoServing
        release = baseline["payload"]["taxonomies"][0]
        attributes = tuple(row["attributeCode"] for row in release["manifest"]["attributes"])
        models = load_demo_models(Path(config["registry"]), attributes, release["manifestSha256"], baseline["payloadSha256"])
        serving = LiveDemoServing(settings, baseline, models, lifecycle=lifecycle)
        with SessionLocal() as db:
            serving.verify_database(db)
        return serving
    from app.training.train_taste_ranking import load_training_evidence
    evidence = load_training_evidence(Path(config["evidence"]), config["evidence_sha256"],
                                      Path(config["packets"]), simulated_demo=True)
    _validate_windows(evidence)
    cutoff = datetime.fromisoformat(config["cutoff"])
    windows = [w for w in evidence.windows if w.cutoff == cutoff]
    if len(windows) != 1:
        raise ValueError("ISOLATED_CUTOFF_NOT_ATTESTED")
    snapshot = PrefixSnapshot(evidence, windows[0], baseline)
    models = load_demo_models(Path(config["registry"]), snapshot.attributes, snapshot.taxonomy_hash, snapshot.catalog_hash)
    return DemoServing(snapshot, models, settings.level2_demo_scoring_enabled, lifecycle=lifecycle)
