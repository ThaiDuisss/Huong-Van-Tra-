"""Build separate ranking objectives from verified, point-in-time source exports."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import AwareDatetime, Field, JsonValue, StringConstraints, model_validator

from app.clients.customer_taste_client import SubmittedTasteSurvey
from app.recommendation.taste_ranking_features import (
    FEATURE_SCHEMA, MIN_VALID_ORDERS, MODES, Mode, RankingFeatures, eligible_products, ranking_features,
)
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import CatalogProduct, PurchaseCount, build_artifact, fingerprint, hybrid_scores
from app.repositories.explore_history_repository import build_history, receipt_products
from app.schemas.taste_catalog import Digest, Envelope, StrictDocument, Version, utc_timestamp, verify_payload
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch
from app.service.taste_catalog_sync_service import ProjectionView

Split = Literal["TRAIN", "VALIDATION", "TEST"]
SPLITS: tuple[Split, ...] = ("TRAIN", "VALIDATION", "TEST")
OUTCOME_LABEL_POLICY = "retained-product-v1"
Nonblank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class DatasetBlocked(ValueError):
    """The evidence cannot support the requested retrospective evaluation."""


class TimedEvent(StrictDocument):
    occurred_at: AwareDatetime
    observed_at: AwareDatetime

    @model_validator(mode="after")
    def check_observation(self):
        if self.observed_at < self.occurred_at:
            raise DatasetBlocked("EVENT_OBSERVED_BEFORE_OCCURRENCE")
        return self


class Receipt(TimedEvent):
    order_id: Nonblank
    customer_id: Nonblank
    evidence_hash: Digest
    lines: list[dict[str, JsonValue]] = Field(min_length=1)

    def history_row(self) -> tuple:
        utc_time = self.occurred_at.astimezone(timezone.utc).replace(tzinfo=None)
        return self.order_id, utc_time, self.evidence_hash, self.lines


class ReturnLine(TimedEvent):
    source: Literal["SIGNED_PRODUCT_RETURN", "LEGACY_PREFERENCE_RETURN"]
    return_item_id: Nonblank
    customer_id: Nonblank
    order_id: Nonblank
    order_item_id: Nonblank
    variant_id: Nonblank
    quantity: Annotated[str, StringConstraints(pattern=r"^[0-9]+(?:\.[0-9]+)?$")]

    def history_row(self) -> tuple:
        return self.return_item_id, self.order_id, self.order_item_id, self.variant_id, self.quantity


class InteractionPurchase(TimedEvent):
    event_id: Nonblank
    customer_id: Nonblank
    product_id: Nonblank
    order_id: Nonblank | None = None


class CatalogRow(StrictDocument):
    product_id: Nonblank
    product_code: Nonblank
    name: Nonblank
    category_code: str | None
    ingredient_codes: list[Nonblank]
    commercial: bool


class SurveyObservation(StrictDocument):
    customer_id: Nonblank
    survey_as_of: AwareDatetime
    observed_at: AwareDatetime
    survey: SubmittedTasteSurvey | None = Field(
        description="Latest submitted survey as of the cutoff, or null only when absence is verified."
    )


class Window(StrictDocument):
    split: Split
    cutoff: AwareDatetime
    outcome_end: AwareDatetime
    outcome_freeze: AwareDatetime
    catalog_as_of: AwareDatetime
    catalog_observed_at: AwareDatetime
    catalog_source: Nonblank
    catalog: list[CatalogRow] = Field(
        min_length=1, description="Active V1 catalog and commercial variant eligibility as of cutoff."
    )
    taste_catalog: Envelope
    taste_catalog_as_of: AwareDatetime
    taste_catalog_observed_at: AwareDatetime
    taste_catalog_ttl_seconds: int = Field(ge=10, le=86400)
    taste_catalog_source: Nonblank
    customer_roster_as_of: AwareDatetime
    survey_source: Nonblank
    customers: list[SurveyObservation] = Field(min_length=1)


class EvidenceBundle(StrictDocument):
    schema_version: Literal[1]
    source_kind: Literal["VERIFIED_SOURCE_EXPORT"]
    # An operator must verify lifetime coverage, including receipt ingestion gaps,
    # signed and legacy returns, and the global PURCHASE interaction population.
    purchase_history_complete_from_inception: Literal[True] = Field(
        description="True only after verifying lifetime source coverage and reconciling backfill or migration gaps."
    )
    receipt_source: Nonblank
    return_source: Nonblank
    interaction_source: Nonblank
    receipts_complete_through: AwareDatetime
    returns_complete_through: AwareDatetime
    interactions_complete_through: AwareDatetime
    exported_at: AwareDatetime
    taxonomy_version: Version
    taxonomy_sha256: Digest
    receipts: list[Receipt]
    returns: list[ReturnLine]
    interaction_purchases: list[InteractionPurchase]
    windows: list[Window] = Field(min_length=3)


@dataclass(frozen=True, slots=True)
class Sample:
    features: RankingFeatures
    label: bool
    popularity: int
    candidate_eligible: Literal[True] = True


@dataclass(frozen=True, slots=True)
class RankingQuery:
    customer_id: str
    mode: Mode
    split: Split
    cutoff: datetime
    outcome_freeze: datetime
    eligible: bool
    hybrid_has_signal: bool
    taxonomy_compatible: bool
    samples: tuple[Sample, ...]

    @property
    def has_taste(self) -> bool:
        return self.taxonomy_compatible and any(sample.features.has_taste for sample in self.samples)

    @property
    def can_rerank(self) -> bool:
        scored = [sample.features for sample in self.samples if sample.features.has_signal]
        return (self.hybrid_has_signal and self.taxonomy_compatible and any(row.has_taste for row in scored)
                and not any(row.needs_aggregation for row in scored))


@dataclass(frozen=True, slots=True)
class Dataset:
    taxonomy_version: str
    taxonomy_sha256: str
    attributes: tuple[str, ...]
    fingerprint: str
    queries: tuple[RankingQuery, ...]
    matching_policy_version: str = EXACT_POLICY_VERSION


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise DatasetBlocked(code)


def _unique(keys, code: str) -> None:
    values = list(keys)
    _require(len(values) == len(set(values)), code)


def _validate_windows(evidence: EvidenceBundle) -> None:
    _require(tuple(dict.fromkeys(w.split for w in evidence.windows)) == SPLITS, "CHRONOLOGICAL_SPLITS_REQUIRED")
    _require(all(left.outcome_freeze <= right.cutoff and SPLITS.index(left.split) <= SPLITS.index(right.split)
                 for left, right in zip(evidence.windows, evidence.windows[1:])), "OVERLAPPING_WINDOWS")
    watermarks = (
        evidence.receipts_complete_through, evidence.returns_complete_through, evidence.interactions_complete_through,
    )
    _require(max(watermarks) <= evidence.exported_at, "COMPLETENESS_AFTER_EXPORT")
    complete_through = min(watermarks)
    for window in evidence.windows:
        _require(window.cutoff < window.outcome_end <= window.outcome_freeze <= complete_through,
                 "INCOMPLETE_OBSERVATION_WINDOW")
        _require(window.catalog_as_of == window.customer_roster_as_of == window.taste_catalog_as_of == window.cutoff
                 and window.catalog_observed_at <= window.cutoff, "CATALOG_OR_ROSTER_NOT_POINT_IN_TIME")
        _unique((c.product_id for c in window.catalog), "DUPLICATE_CATALOG_PRODUCT")
        _unique((c.customer_id for c in window.customers), "DUPLICATE_CUTOFF_CUSTOMER")
        for customer in window.customers:
            _require(customer.survey_as_of == window.cutoff and customer.observed_at <= window.cutoff,
                     "SURVEY_NOT_POINT_IN_TIME")
            if customer.survey:
                _require(customer.survey.customerId == customer.customer_id
                         and utc_timestamp(customer.survey.submittedAt) <= customer.observed_at,
                         "FUTURE_OR_WRONG_CUSTOMER_SURVEY")


def _projection(window: Window) -> ProjectionView:
    envelope = window.taste_catalog
    read_at = utc_timestamp(envelope.sourceReadAt)
    _require(read_at <= window.taste_catalog_observed_at <= window.cutoff, "FUTURE_TASTE_CATALOG")
    _require(envelope.snapshotId == "taste-v1-" + envelope.payloadSha256, "INVALID_TASTE_SNAPSHOT_ID")
    payload = verify_payload(envelope.payload, envelope.payloadSha256, 16 * 1024 * 1024, 10000)
    _require(all(utc_timestamp(profile.approvedAt) <= read_at for profile in payload.profiles),
             "FUTURE_PROFILE_APPROVAL")
    status = "READY" if payload.profiles else "EMPTY"
    if payload.catalogState == "NO_TAXONOMY":
        status = "UNAVAILABLE"
    if (window.cutoff - read_at).total_seconds() >= window.taste_catalog_ttl_seconds:
        status = "STALE"
    return ProjectionView(
        status, None, None,
        catalog_snapshot_id=envelope.snapshotId, source_read_at=read_at, payload=payload,
    )


def _retained_outcomes(
    window: Window, customer_id: str, receipts: list[Receipt], returns: list[ReturnLine],
) -> set[str]:
    suffix = {
        row.order_id: row for row in receipts if window.cutoff < row.occurred_at <= window.outcome_end
    }
    _require(all(row.observed_at <= window.outcome_freeze for row in suffix.values()), "LATE_OUTCOME_RECEIPT")
    applicable_returns = [
        row for row in returns if row.order_id in suffix and row.occurred_at <= window.outcome_end
    ]
    _require(all(row.observed_at <= window.outcome_freeze for row in applicable_returns), "LATE_OUTCOME_RETURN")
    retained = build_history(customer_id, (row.history_row() for row in suffix.values()), (),
                             (row.history_row() for row in applicable_returns))
    return set(retained.product_counts)


def build_dataset(evidence: EvidenceBundle) -> Dataset:
    _validate_windows(evidence)
    _unique((r.order_id for r in evidence.receipts), "DUPLICATE_RECEIPT")
    _unique(((r.source, r.return_item_id) for r in evidence.returns), "DUPLICATE_RETURN")
    _unique((p.event_id for p in evidence.interaction_purchases), "DUPLICATE_PURCHASE_INTERACTION")
    _require(all(row.observed_at <= evidence.exported_at
                 for stream in (evidence.receipts, evidence.returns, evidence.interaction_purchases) for row in stream),
             "FUTURE_EVENT_OBSERVATION")

    receipts, returns, purchases = defaultdict(list), defaultdict(list), defaultdict(list)
    purchased_by_order = {}
    receipt_lines = {}
    order_receipts = {}
    for receipt in evidence.receipts:
        products = list(receipt_products(receipt.customer_id, *receipt.history_row()[1:]))
        purchased_by_order[receipt.order_id] = {product_id for _, product_id, _, _ in products}
        receipt_lines[receipt.order_id] = {line_id: (variant_id, quantity) for line_id, _, variant_id, quantity in products}
        order_receipts[receipt.order_id] = receipt
        receipts[receipt.customer_id].append(receipt)
    for row in evidence.returns:
        receipt = order_receipts.get(row.order_id)
        line = receipt_lines.get(row.order_id, {}).get(row.order_item_id)
        _require(receipt is not None and receipt.customer_id == row.customer_id and receipt.occurred_at <= row.occurred_at
                 and line is not None and line[0] == row.variant_id and 0 < Decimal(row.quantity) <= line[1],
                 "RETURN_WITHOUT_MATCHING_RECEIPT_LINE")
        returns[row.customer_id].append(row)
    for row in evidence.interaction_purchases:
        purchases[row.customer_id].append(row)

    attributes = None
    queries = []
    for window in evidence.windows:
        view = _projection(window)
        for taxonomy in view.payload.taxonomies:
            if taxonomy.versionCode == evidence.taxonomy_version:
                _require(taxonomy.manifestSha256 == evidence.taxonomy_sha256, "TAXONOMY_HASH_DRIFT")
                attributes = tuple(item["attributeCode"] for item in taxonomy.manifest["attributes"])

        active_catalog = tuple(CatalogProduct(
            row.product_id, row.product_code, row.name, row.category_code, tuple(sorted(set(row.ingredient_codes)))
        ) for row in window.catalog)
        commercial = {row.product_id for row in window.catalog if row.commercial}
        counts = Counter((row.customer_id, row.product_id) for rows in purchases.values() for row in rows
                         if row.observed_at <= window.cutoff)
        _require(bool(counts), "NO_PREFIX_PURCHASE_INTERACTIONS")
        # V1's historical fit uses only the prefix interactions and that cutoff's active catalog.
        artifact = build_artifact(active_catalog, tuple(PurchaseCount(u, p, n) for (u, p), n in counts.items()))
        popularity = Counter()
        for (_, product_id), count in counts.items():
            popularity[product_id] += count

        for customer in window.customers:
            customer_id = customer.customer_id
            customer_receipts = receipts[customer_id]
            prefix = [row for row in customer_receipts if row.observed_at <= window.cutoff]
            prefix_purchases = {row.product_id for row in purchases[customer_id] if row.observed_at <= window.cutoff}
            history = build_history(customer_id, (row.history_row() for row in prefix), prefix_purchases,
                                    (row.history_row() for row in returns[customer_id]
                                     if row.observed_at <= window.cutoff))
            _require(not any(row.occurred_at <= window.cutoff < row.observed_at and
                             purchased_by_order[row.order_id] - history.purchased_ids
                             for row in customer_receipts), "LATE_PREFIX_RECEIPT_CHANGES_NOVELTY")
            _require(not any(row.occurred_at <= window.cutoff < row.observed_at and
                             row.product_id not in history.purchased_ids for row in purchases[customer_id]),
                     "LATE_PREFIX_INTERACTION_CHANGES_NOVELTY")
            outcome = _retained_outcomes(window, customer_id, customer_receipts, returns[customer_id])
            for row in purchases[customer_id]:
                if window.cutoff < row.occurred_at <= window.outcome_end:
                    receipt = order_receipts.get(row.order_id)
                    _require(row.observed_at <= window.outcome_freeze, "LATE_OUTCOME_INTERACTION")
                    _require(receipt is not None and receipt.customer_id == customer_id
                             and window.cutoff < receipt.occurred_at <= window.outcome_end
                             and row.product_id in purchased_by_order[receipt.order_id],
                             "OUTCOME_INTERACTION_WITHOUT_MATCHING_RECEIPT")

            eligible = history.valid_order_count >= MIN_VALID_ORDERS
            taste = (PersonalizedTasteFeatures.from_observation(customer.survey, window.cutoff, commercial, lambda: view)
                     if eligible and customer.survey else TasteFeatureBatch("SURVEY_MISSING"))
            if eligible and customer.survey:
                _require(taste.status not in ("SURVEY_INVALID", "TAXONOMY_MISMATCH"),
                         "SURVEY_CONTRACT_FAILED")
            taxonomy_compatible = (taste.taxonomy_version, taste.taxonomy_sha256) == (
                evidence.taxonomy_version, evidence.taxonomy_sha256,
            )
            if taxonomy_compatible:
                _require(taste.matching_policy_version == EXACT_POLICY_VERSION, "MATCHING_POLICY_INCOMPATIBLE")
            else:
                taste = TasteFeatureBatch("TAXONOMY_UNSUPPORTED")
            content, cf = artifact.components(history.product_counts) if eligible else ({}, {})
            for mode in MODES:
                ids = eligible_products(mode, commercial, history) if eligible else set()
                scores, flat = hybrid_scores(content, cf, tuple(sorted(ids)))
                samples = tuple(Sample(
                    ranking_features(product, history, window.cutoff, scores[product.product_id],
                                     content[product.product_id], cf[product.product_id], taste),
                    product.product_id in outcome, popularity[product.product_id],
                ) for product in artifact.products if product.product_id in ids)
                queries.append(RankingQuery(customer_id, mode, window.split, window.cutoff, window.outcome_freeze,
                                            eligible, bool(ids) and not flat, taxonomy_compatible, samples))

    _require(bool(attributes), "VERIFIED_MODEL_TAXONOMY_MISSING")
    dataset_sha = fingerprint({
        "feature_schema": FEATURE_SCHEMA, "matching_policy_version": EXACT_POLICY_VERSION,
        "outcome_label_policy": OUTCOME_LABEL_POLICY, "evidence": evidence.model_dump(mode="json"),
    })
    return Dataset(evidence.taxonomy_version, evidence.taxonomy_sha256, attributes, dataset_sha, tuple(queries))
