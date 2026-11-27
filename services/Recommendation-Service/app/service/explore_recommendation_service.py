from collections import defaultdict
from contextvars import copy_context
from dataclasses import dataclass
from datetime import datetime, timezone
import logging
from math import log1p
from statistics import mean
from typing import Callable, TypeVar

import httpx
from sqlalchemy import func, select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.clients.catalog_client import CatalogClient
from app.models.customer_interaction import CustomerInteraction
from app.recommendation.taste_ranking_artifact import MODEL_CONTRACT, TasteReranker
from app.recommendation.taste_ranking_features import (
    MIN_VALID_ORDERS, Mode, RankingFeatures, eligible_products, has_behavior_signal,
    level2_hybrid_scores, ranking_features, taste_groups,
)
from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_model import (
    CONTRACT_VERSION, CatalogProduct, V1ModelError, catalog_fingerprint, feature_tokens, normalize_scores,
)
from app.repositories.explore_history_repository import ExploreHistory, ExploreHistoryRepository
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.schemas.explore_recommendation import (
    ExploreProducts, ExploreRecommendationResponse, FamiliarRecommendationResponse,
    PersonalizedRecommendationResponse,
)
from app.schemas.recommendation import RecommendationItem
from app.service.level2_availability import (
    AvailabilityUnavailable, Level2Availability, fetch_available_variant_ids, variants_by_product,
)
from app.service.level2_serving_inputs import Level2ServingInputs
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch

MIN_EXPLORE_VALID_ORDERS = MIN_VALID_ORDERS
BASELINE_MODEL_CONTRACT = "hvtra-personalized-baseline-l2-v1"
OPERATIONAL_RETURN_REASONS = frozenset({"DEFECTIVE", "DAMAGED", "WRONG_ITEM", "QUALITY_ISSUE", "EXPIRED"})
ResponseT = TypeVar("ResponseT", bound=PersonalizedRecommendationResponse)
logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class BehavioralCandidate:
    product_id: str
    score: float
    has_signal: bool


@dataclass(frozen=True)
class CandidateFeatures:
    behavioral: tuple[BehavioralCandidate, ...]
    taste: TasteFeatureBatch


def rank_by_behavior(features: CandidateFeatures, limit: int) -> list[BehavioralCandidate]:
    return sorted((candidate for candidate in features.behavioral if candidate.has_signal),
                  key=lambda candidate: (-candidate.score, candidate.product_id))[:limit]


def _taste_compatibility_score(row: RankingFeatures, taste_ready: bool) -> float:
    """Return observed taste score in [0, 1], using neutral 0.50 prior when profile/taste is missing."""
    if not taste_ready:
        return 0.5
    hard, soft = taste_groups(row)
    hard_avg = mean(hard.values()) if hard else None
    soft_avg = mean(soft.values()) if soft else None
    if hard_avg is not None and soft_avg is not None:
        return 0.65 * hard_avg + 0.35 * soft_avg
    if hard_avg is not None:
        return float(hard_avg)
    if soft_avg is not None:
        return float(soft_avg)
    return 0.5


def _product_tokens(product: CatalogProduct) -> set[str]:
    tokens = {
        token for token in feature_tokens(product)
        if token != "name:trà"
    }
    if product.category_code and product.category_code.strip():
        tokens.add(f"category:{product.category_code.strip().casefold()}")
    return tokens


def _popularity_map(db: Session | None, product_ids: set[str]) -> dict[str, float]:
    if db is None or not product_ids:
        return {}
    try:
        rows = db.execute(
            select(
                CustomerInteraction.product_id,
                func.coalesce(func.sum(CustomerInteraction.total_purchase_count), 0),
            )
            .where(CustomerInteraction.product_id.in_(product_ids))
            .group_by(CustomerInteraction.product_id)
        )
        return normalize_scores({product_id: float(total) for product_id, total in rows})
    except SQLAlchemyError:
        return {}


def _baseline_scores(
    *,
    mode: Mode,
    catalog_by_id: dict[str, CatalogProduct],
    candidates: dict[str, CatalogProduct],
    history: ExploreHistory,
    rows: dict[str, RankingFeatures],
    taste: TasteFeatureBatch,
    popularity: dict[str, float],
) -> dict[str, float]:
    """Compute return-aware behavioral + taste + popularity baseline across live Catalog candidates."""
    taste_ready = taste.status == "TASTE_FEATURE_READY"
    if mode == "FAMILIAR":
        freq_raw = {
            pid: log1p(max(0, (rows[pid].retained_order_frequency or history.product_counts.get(pid, 0))))
            for pid in candidates
        }
        qty_raw = {
            pid: log1p(max(0.0, float(rows[pid].net_quantity or 0.0)))
            for pid in candidates
        }
        freq_norm = normalize_scores(freq_raw)
        qty_norm = normalize_scores(qty_raw)
        scores: dict[str, float] = {}
        for pid, row in rows.items():
            recency = 1.0 / (1.0 + max(0.0, float(row.recency_days or 0.0)) / 30.0)
            stats = history.product_history.get(pid)
            purchased_lines = max(1, stats.purchased_lines) if stats else 1
            change_mind = (row.return_reasons or {}).get("CHANGE_OF_MIND", 0)
            operational = sum(
                count for reason, count in (row.return_reasons or {}).items()
                if reason in OPERATIONAL_RETURN_REASONS
            )
            return_factor = max(
                0.0,
                1.0 - 0.35 * (change_mind / purchased_lines) - 0.05 * (operational / purchased_lines),
            )
            behavioral = (0.45 * freq_norm.get(pid, 0.0) + 0.30 * qty_norm.get(pid, 0.0) + 0.25 * recency) * return_factor
            taste_score = _taste_compatibility_score(row, taste_ready)
            scores[pid] = 0.65 * behavioral + 0.35 * taste_score if taste_ready else behavioral
        return scores

    token_affinity: dict[str, float] = defaultdict(float)
    for pid, ph in history.product_history.items():
        purchased = catalog_by_id.get(pid)
        if purchased is None:
            continue
        change_mind = ph.return_reasons.get("CHANGE_OF_MIND", 0)
        operational = sum(
            count for reason, count in ph.return_reasons.items()
            if reason in OPERATIONAL_RETURN_REASONS
        )
        effective_weight = float(ph.net_quantity) + 0.25 * operational - 0.75 * change_mind
        for token in _product_tokens(purchased):
            weight = 2.0 * effective_weight if token.startswith("category:") else effective_weight
            token_affinity[token] += weight

    raw_affinity = {}
    for pid, candidate in candidates.items():
        token_sum = sum(token_affinity.get(token, 0.0) for token in _product_tokens(candidate))
        raw_affinity[pid] = max(0.0, token_sum) + 0.35 * rows[pid].hybrid
    affinity_norm = normalize_scores(raw_affinity)

    scores = {}
    for pid, row in rows.items():
        taste_score = _taste_compatibility_score(row, taste_ready)
        behavior = affinity_norm.get(pid, 0.0)
        pop = popularity.get(pid, 0.0)
        if taste_ready:
            scores[pid] = 0.45 * taste_score + 0.40 * behavior + 0.15 * pop
        else:
            scores[pid] = 0.75 * behavior + 0.25 * pop
    return scores


class PersonalizedRecommendationService:
    def __init__(self, artifact_store: V1ArtifactStore,
                 history_repository: ExploreHistoryRepository | None = None,
                 catalog_repository: V1RecommendationRepository | None = None, *,
                 taste_features: PersonalizedTasteFeatures | None = None,
                 taste_reranker: TasteReranker | None = None,
                 availability: Level2Availability | None = None,
                 serving_inputs: Level2ServingInputs | None = None,
                 ranker: Callable[[CandidateFeatures, int], list[BehavioralCandidate]] = rank_by_behavior) -> None:
        self.artifact_store = artifact_store
        self.history_repository = history_repository or ExploreHistoryRepository()
        self.catalog_repository = catalog_repository or V1RecommendationRepository()
        self.taste_features = taste_features
        self.taste_reranker = taste_reranker or TasteReranker()
        self.availability = availability
        self.serving_inputs = serving_inputs
        self.ranker = ranker

    def _recommend(self, db: Session, customer_id: str, limit: int,
                   response_model: type[ResponseT], *, familiar: bool) -> ResponseT:
        as_of = datetime.now(timezone.utc)
        cutoff = as_of.replace(tzinfo=None)
        history = (self.serving_inputs.product_history(self.history_repository, db, customer_id, cutoff)
                   if self.serving_inputs else self.history_repository.history(db, customer_id, cutoff))
        if history.valid_order_count < MIN_EXPLORE_VALID_ORDERS:
            return response_model(
                customer_id=customer_id, status="INSUFFICIENT_VALID_ORDERS",
                valid_order_count=history.valid_order_count,
            )
        result = self._products(db, history, limit, customer_id=customer_id, as_of=as_of, familiar=familiar)
        return response_model(
            customer_id=customer_id, status="ELIGIBLE",
            valid_order_count=history.valid_order_count, product=result,
        )

    def _load_catalog_and_mirror(
        self, db: Session
    ) -> tuple[tuple[CatalogProduct, ...], dict[str, str], bool]:
        db_products = (self.serving_inputs.product_mirror(db, self.catalog_repository)
                       if self.serving_inputs else self.catalog_repository.active_catalog(db))
        ingredients_by_id = {p.product_id: p.ingredient_codes for p in db_products if p.ingredient_codes}
        if (type(self.catalog_repository) is V1RecommendationRepository
                and type(self.availability) is Level2Availability):
            try:
                settings = self.availability.settings
                if self.serving_inputs:
                    live_products, live_variants = self.serving_inputs.product_market()
                else:
                    with CatalogClient(settings.catalog_service_url,
                                       timeout=settings.level2_stock_timeout_seconds) as client:
                        live_products = client.get_products()
                        live_variants = client.get_product_variants(sellable_only=True)
                products = tuple(
                    CatalogProduct(
                        product_id=p.id,
                        product_code=p.productCode,
                        name=p.name,
                        category_code=p.category.categoryCode,
                        ingredient_codes=ingredients_by_id.get(p.id, ()),
                    )
                    for p in live_products
                    if p.status == "ACTIVE"
                    and (p.category.categoryCode or "").upper() != "CUSTOMER-BUNDLE"
                    and not (p.variants and all((v.productType or "").upper() == "CUSTOMER" for v in p.variants))
                )
                active_ids = {p.product_id for p in products}
                mirror = {
                    v.id: v.productId
                    for v in live_variants
                    if (v.productId in active_ids and v.status == "ACTIVE"
                        and v.sellable is True and v.sku != "CUSTOM-BUNDLE"
                        and (v.productType or "").upper() != "CUSTOMER"
                        and v.sellingPrice.is_finite() and v.sellingPrice >= 0
                        and (v.productStatus is None or v.productStatus == "ACTIVE"))
                }
                return products, mirror, True
            except (httpx.HTTPError, ValueError, RuntimeError) as exc:
                logger.warning("Live Catalog lookup failed, falling back to local mirror: %s", exc)
        return db_products, self.history_repository.commercial_variants(db), False

    def _products(self, db: Session, history: ExploreHistory, limit: int, *,
                  customer_id: str, as_of: datetime, familiar: bool) -> ExploreProducts:
        products, mirror, live_catalog = self._load_catalog_and_mirror(db)
        catalog_by_id = {p.product_id: p for p in products}
        mode: Mode = "FAMILIAR" if familiar else "EXPLORE"
        metadata_ids = eligible_products(mode, set(mirror.values()), history) & catalog_by_id.keys()
        if not metadata_ids:
            return ExploreProducts(status="NO_CANDIDATES")
        taste_pending = None
        if self.serving_inputs and self.taste_features:
            taste_pending = self.serving_inputs.io.submit(
                copy_context().run, self.taste_features.prepare, customer_id, as_of, metadata_ids,
            )
        try:
            if self.availability is None:
                raise AvailabilityUnavailable("INVENTORY_NOT_CONFIGURED")
            scoped_variants = {
                variant: product for variant, product in mirror.items() if product in metadata_ids
            }
            if live_catalog and self.serving_inputs and type(self.availability) is Level2Availability:
                active = (
                    self.serving_inputs.available_variant_ids(mirror.keys(), self.availability.settings)
                    & scoped_variants.keys()
                )
                available = variants_by_product({variant: scoped_variants[variant] for variant in active})
            else:
                available = variants_by_product(self.availability.available_variants(scoped_variants))
        except AvailabilityUnavailable:
            if taste_pending is not None:
                taste_pending.cancel()
            return ExploreProducts(status="AVAILABILITY_UNAVAILABLE")
        candidate_ids = eligible_products(mode, set(available), history)
        candidates = {pid: catalog_by_id[pid] for pid in candidate_ids if pid in catalog_by_id}
        if not candidates:
            if taste_pending is not None:
                taste_pending.cancel()
            return ExploreProducts(status="NO_CANDIDATES")

        artifact = None
        artifact_matches_catalog = False
        try:
            artifact = self.artifact_store.load()
            artifact_matches_catalog = artifact.catalog_sha256 == catalog_fingerprint(products)
            content, cf = artifact.components(history.product_counts)
            scores, has_signal = level2_hybrid_scores(
                {pid: content.get(pid, 0.0) for pid in candidates},
                {pid: cf.get(pid, 0.0) for pid in candidates},
                tuple(candidates),
            )
        except V1ModelError:
            content = {pid: 0.0 for pid in candidates}
            cf = {pid: 0.0 for pid in candidates}
            scores = {pid: 0.0 for pid in candidates}
            has_signal = False

        try:
            if taste_pending is not None:
                taste = taste_pending.result().for_products(set(candidates))
            else:
                taste = (self.taste_features.prepare(customer_id, as_of, set(candidates))
                         if self.taste_features else TasteFeatureBatch("NOT_CONFIGURED"))
        except (SQLAlchemyError, RuntimeError, ValueError):
            taste = TasteFeatureBatch("CATALOG_UNAVAILABLE")

        rows_by_id = {
            pid: ranking_features(
                candidates[pid], history, as_of, scores[pid],
                content.get(pid, 0.0), cf.get(pid, 0.0), taste, available[pid],
            )
            for pid in candidates
        }
        top_k = min(limit, 5)
        model_contract = CONTRACT_VERSION if artifact_matches_catalog and artifact else BASELINE_MODEL_CONTRACT
        trained_at = artifact.created_at if artifact_matches_catalog and artifact else None

        learned = self.taste_reranker.artifact
        if learned and learned.mode == mode and artifact_matches_catalog and has_signal:
            result = self.taste_reranker.score(tuple(rows_by_id.values()), taste)
            logger.debug("taste_ranking mode=%s status=%s", mode, result.status)
            if result.scores is not None:
                ranked = [BehavioralCandidate(p, score, True)
                          for p, score in sorted(result.scores.items(), key=lambda row: (-row[1], row[0]))[:top_k]]
                model_contract, trained_at = MODEL_CONTRACT, learned.trained_at
                return ExploreProducts(
                    status="PERSONALIZED", model_contract_version=model_contract, trained_at=trained_at,
                    items=[RecommendationItem(rank=rank, product_id=row.product_id,
                                              product_code=candidates[row.product_id].product_code,
                                              name=candidates[row.product_id].name, score=row.score)
                           for rank, row in enumerate(ranked, 1)],
                )

        if artifact_matches_catalog and has_signal and not live_catalog and self.ranker is not rank_by_behavior:
            features = CandidateFeatures(
                tuple(BehavioralCandidate(p, score, has_behavior_signal(content.get(p, 0.0), cf.get(p, 0.0)))
                      for p, score in scores.items()),
                taste,
            )
            ranked = self.ranker(features, top_k)
        elif artifact_matches_catalog and has_signal and not live_catalog and taste.status != "TASTE_FEATURE_READY" and history.product_history == {}:
            features = CandidateFeatures(
                tuple(BehavioralCandidate(p, score, has_behavior_signal(content.get(p, 0.0), cf.get(p, 0.0)))
                      for p, score in scores.items()),
                taste,
            )
            ranked = self.ranker(features, top_k)
        else:
            if self.serving_inputs is not None:
                try:
                    totals = self.serving_inputs.popularity_totals(db)
                    popularity = normalize_scores({pid: totals[pid] for pid in candidates if pid in totals})
                except SQLAlchemyError:
                    popularity = {}
            else:
                popularity = _popularity_map(db, set(candidates))
            baseline = _baseline_scores(
                mode=mode, catalog_by_id=catalog_by_id, candidates=candidates,
                history=history, rows=rows_by_id, taste=taste, popularity=popularity,
            )
            ranked = [
                BehavioralCandidate(pid, score, True)
                for pid, score in sorted(baseline.items(), key=lambda item: (-item[1], item[0]))[:top_k]
            ]
            model_contract = BASELINE_MODEL_CONTRACT if live_catalog or not artifact_matches_catalog else CONTRACT_VERSION

        return ExploreProducts(
            status="PERSONALIZED", model_contract_version=model_contract, trained_at=trained_at,
            items=[RecommendationItem(rank=rank, product_id=row.product_id,
                                      product_code=candidates[row.product_id].product_code,
                                      name=candidates[row.product_id].name, score=row.score)
                   for rank, row in enumerate(ranked, 1)],
        )


class ExploreRecommendationService(PersonalizedRecommendationService):
    def recommend(self, db: Session, customer_id: str, limit: int) -> ExploreRecommendationResponse:
        return self._recommend(db, customer_id, limit, ExploreRecommendationResponse, familiar=False)


class FamiliarRecommendationService(PersonalizedRecommendationService):
    def recommend(self, db: Session, customer_id: str, limit: int) -> FamiliarRecommendationResponse:
        return self._recommend(db, customer_id, limit, FamiliarRecommendationResponse, familiar=True)
