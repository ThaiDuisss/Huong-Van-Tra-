"""Build separate ranking objectives from verified, point-in-time source exports."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated, Literal
from uuid import UUID

from pydantic import AwareDatetime, Field, JsonValue, StringConstraints, model_validator

from app.clients.customer_taste_client import SubmittedTasteSurvey
from app.recommendation.taste_ranking_features import (
    FEATURE_SCHEMA, MIN_VALID_ORDERS, MODES, Mode, RankingFeatures, eligible_products,
    level2_hybrid_scores, ranking_features,
)
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import CatalogProduct, PurchaseCount, build_artifact, fingerprint
from app.repositories.explore_history_repository import build_history, receipt_products
from app.schemas.taste_catalog import Digest, Envelope, StrictDocument, Version, utc_timestamp, verify_payload
from app.service.level2_availability import variants_by_product
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch
from app.service.taste_catalog_sync_service import ProjectionView

Split = Literal["TRAIN", "VALIDATION", "TEST"]
SPLITS: tuple[Split, ...] = ("TRAIN", "VALIDATION", "TEST")
OUTCOME_LABEL_POLICY = "retained-product-v2"
Nonblank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
Provenance = Literal["VERIFIED_COMMERCIAL", "UNVERIFIED_SOURCE", "SYNTHETIC", "SIMULATED_DEMO", "TEST_FIXTURE"]
SOURCE_STREAMS = ("customer", "order", "returns", "interactions", "catalog", "taste_catalog", "inventory")


class DatasetBlocked(ValueError):
    """The evidence cannot support the requested retrospective evaluation."""


class TimedEvent(StrictDocument):
    occurred_at: AwareDatetime
    observed_at: AwareDatetime
    provenance: Provenance = "UNVERIFIED_SOURCE"

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
    source_event_id: Nonblank | None = None

    def history_row(self) -> tuple:
        utc_time = self.occurred_at.astimezone(timezone.utc).replace(tzinfo=None)
        return self.order_id, utc_time, self.evidence_hash, self.lines


class ReturnLine(TimedEvent):
    source: Literal["SIGNED_PRODUCT_RETURN", "LEGACY_PREFERENCE_RETURN", "SIMULATED_PRODUCT_RETURN"]
    return_item_id: Nonblank
    customer_id: Nonblank
    order_id: Nonblank
    order_item_id: Nonblank
    variant_id: Nonblank
    quantity: Annotated[str, StringConstraints(pattern=r"^[0-9]+(?:\.[0-9]+)?$")]
    reason: Nonblank | None = None

    def history_row(self) -> tuple:
        return self.return_item_id, self.order_id, self.order_item_id, self.variant_id, self.quantity, self.reason


class InteractionPurchase(TimedEvent):
    event_id: Nonblank
    customer_id: Nonblank
    product_id: Nonblank
    order_id: Nonblank | None = None
    order_item_id: Nonblank | None = None
    variant_id: Nonblank | None = None


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
    linked_at: AwareDatetime | None = None
    survey: SubmittedTasteSurvey | None = Field(
        description="Latest submitted survey as of the cutoff, or null only when absence is verified."
    )


class AvailabilityRow(StrictDocument):
    variant_id: Nonblank
    product_id: Nonblank
    catalog_sellable: bool
    available_quantity: Decimal = Field(ge=0, allow_inf_nan=False)


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
    catalog_variants: dict[Nonblank, Nonblank] | None = None
    taste_catalog: Envelope
    taste_catalog_as_of: AwareDatetime
    taste_catalog_observed_at: AwareDatetime
    taste_catalog_ttl_seconds: int = Field(ge=10, le=86400)
    taste_catalog_source: Nonblank
    customer_roster_as_of: AwareDatetime
    survey_source: Nonblank
    customers: list[SurveyObservation] = Field(min_length=1)
    availability_as_of: AwareDatetime | None = None
    availability_observed_at: AwareDatetime | None = None
    availability_source: Nonblank | None = None
    # Complete active non-custom mirror Variant universe, including no stock.
    availability: list[AvailabilityRow] | None = None


class StreamCoverage(StrictDocument):
    provenance: Provenance
    source_reference: Nonblank
    source_sha256: Digest
    observed_from: AwareDatetime
    complete_through: AwareDatetime
    unresolved_gaps: int = Field(ge=0)


class EvidenceBundle(StrictDocument):
    schema_version: Literal[1, 2]
    source_kind: Literal["VERIFIED_SOURCE_EXPORT", "TEST_FIXTURE", "SIMULATED_DEMO"]
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
    coverage: dict[str, StreamCoverage] = Field(default_factory=dict)
    receipt_variant_mapping: dict[Nonblank, Nonblank] = Field(default_factory=dict)
    history_start: AwareDatetime | None = None

    @property
    def provenance(self) -> Provenance:
        if self.source_kind in ("TEST_FIXTURE", "SIMULATED_DEMO"):
            return self.source_kind
        return "VERIFIED_COMMERCIAL" if self.schema_version == 2 else "UNVERIFIED_SOURCE"


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
    survey_present: bool = False

    @property
    def has_taste(self) -> bool:
        return self.taxonomy_compatible and any(sample.features.has_taste for sample in self.samples)

    @property
    def can_rerank(self) -> bool:
        scored = [sample.features for sample in self.samples if sample.features.has_signal]
        return self.hybrid_has_signal and self.taxonomy_compatible and any(row.has_taste for row in scored)

    @property
    def informative(self) -> bool:
        return self.can_rerank and len({row.label for row in self.samples if row.features.has_signal}) == 2


@dataclass(frozen=True, slots=True)
class Dataset:
    taxonomy_version: str
    taxonomy_sha256: str
    attributes: tuple[str, ...]
    fingerprint: str
    queries: tuple[RankingQuery, ...]
    matching_policy_version: str = EXACT_POLICY_VERSION
    provenance: Provenance = "UNVERIFIED_SOURCE"


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise DatasetBlocked(code)


def _unique(keys, code: str) -> None:
    values = list(keys)
    _require(len(values) == len(set(values)), code)


def _canonical_ids(values, code: str) -> None:
    for value in values:
        try:
            _require(isinstance(value, str) and str(UUID(value)) == value, code)
        except (ValueError, TypeError):
            raise DatasetBlocked(code) from None


def _validate_source_ledger(evidence: EvidenceBundle) -> None:
    provenance = evidence.provenance
    if provenance not in ("VERIFIED_COMMERCIAL", "SIMULATED_DEMO"):
        return
    _require(evidence.schema_version == 2, "SIMULATION_REQUIRES_EVIDENCE_V2")
    _require(set(evidence.coverage) == set(SOURCE_STREAMS) and evidence.history_start is not None,
             "INCOMPLETE_SOURCE_LEDGER")
    first_cutoff, last_cutoff = evidence.windows[0].cutoff, evidence.windows[-1].cutoff
    _require(evidence.history_start <= first_cutoff, "INVALID_HISTORY_START")
    event_watermarks = {
        "order": evidence.receipts_complete_through, "returns": evidence.returns_complete_through,
        "interactions": evidence.interactions_complete_through,
    }
    for name, ledger in evidence.coverage.items():
        start = evidence.history_start if name in event_watermarks else first_cutoff
        end = event_watermarks.get(name, last_cutoff)
        _require(ledger.provenance == provenance and ledger.unresolved_gaps == 0
                 and ledger.observed_from <= start <= end <= ledger.complete_through <= evidence.exported_at,
                 "UNVERIFIED_SOURCE_OR_COVERAGE_GAP")
    for events in (evidence.receipts, evidence.returns, evidence.interaction_purchases):
        _require(all(event.provenance == provenance and event.occurred_at >= evidence.history_start
                     for event in events), "NON_COMMERCIAL_OR_OUTSIDE_SOURCE_COVERAGE")
    _canonical_ids((row.source_event_id for row in evidence.receipts), "RECEIPT_SOURCE_ID_MISSING")
    _unique((row.source_event_id for row in evidence.receipts), "DUPLICATE_RECEIPT_SOURCE_EVENT")
    return_source = "SIGNED_PRODUCT_RETURN" if provenance == "VERIFIED_COMMERCIAL" else "SIMULATED_PRODUCT_RETURN"
    _require(all(row.source == return_source and row.reason for row in evidence.returns),
             "UNVERIFIED_COMMERCIAL_RETURN")
    _canonical_ids(evidence.receipt_variant_mapping.keys(), "INVALID_HISTORICAL_VARIANT")
    _canonical_ids(evidence.receipt_variant_mapping.values(), "INVALID_HISTORICAL_PRODUCT")


def _verified_availability(window: Window, evidence: EvidenceBundle) -> dict[str, set[str]] | None:
    if window.availability is None:
        _require(evidence.schema_version < 2, "AVAILABILITY_EVIDENCE_MISSING")
        return None
    _require(window.availability_as_of == window.cutoff and window.availability_observed_at is not None
             and window.availability_observed_at <= window.cutoff
             and bool(window.availability_source), "AVAILABILITY_NOT_POINT_IN_TIME")
    _unique((row.variant_id for row in window.availability), "DUPLICATE_AVAILABILITY_VARIANT")
    catalog_ids = {row.product_id for row in window.catalog}
    _require(all(row.product_id in catalog_ids for row in window.availability), "UNKNOWN_AVAILABILITY_PRODUCT")
    if evidence.schema_version == 2:
        _require(window.catalog_variants is not None and window.catalog_variants == {
            row.variant_id: row.product_id for row in window.availability
        }, "INCOMPLETE_AVAILABILITY_VARIANT_COVERAGE")
    if evidence.provenance in ("VERIFIED_COMMERCIAL", "SIMULATED_DEMO"):
        for row in window.availability:
            _canonical_ids((row.variant_id, row.product_id), "INVALID_AVAILABILITY_IDENTITY")
            historical = evidence.receipt_variant_mapping.get(row.variant_id)
            _require(historical is None or historical == row.product_id, "AVAILABILITY_PARENT_CONFLICT")
    active = variants_by_product({row.variant_id: row.product_id for row in window.availability
                                  if row.catalog_sellable and row.available_quantity > 0})
    _require(set(active) == {row.product_id for row in window.catalog if row.commercial},
             "COMMERCIAL_AVAILABILITY_MISMATCH")
    return active


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
                if evidence.schema_version == 2:
                    _require(customer.linked_at is not None and customer.linked_at <= customer.observed_at,
                             "SURVEY_LINK_NOT_POINT_IN_TIME")
            if evidence.provenance in ("VERIFIED_COMMERCIAL", "SIMULATED_DEMO"):
                _canonical_ids((customer.customer_id,), "INVALID_CUSTOMER_ROSTER_ID")


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
) -> tuple[set[str], set[str]]:
    suffix = {
        row.order_id: row for row in receipts if window.cutoff < row.occurred_at <= window.outcome_end
    }
    _require(all(row.observed_at <= window.outcome_freeze for row in suffix.values()), "LATE_OUTCOME_RECEIPT")
    applicable_returns = [
        row for row in returns if row.order_id in suffix and row.occurred_at <= window.outcome_end
    ]
    _require(all(row.observed_at <= window.outcome_freeze for row in applicable_returns), "LATE_OUTCOME_RETURN")
    by_order = defaultdict(list)
    for row in applicable_returns:
        by_order[row.order_id].append(row.history_row())
    first_purchase = {}
    first_retained = set()
    retained = set()
    for receipt in sorted(suffix.values(), key=lambda row: (row.occurred_at, row.order_id)):
        history = build_history(customer_id, (receipt.history_row(),), (), by_order[receipt.order_id])
        for product in history.purchased_ids:
            first_time = first_purchase.setdefault(product, receipt.occurred_at)
            if product in history.product_counts and first_time == receipt.occurred_at:
                first_retained.add(product)
        retained.update(history.product_counts)
    return first_retained, retained


def build_dataset(evidence: EvidenceBundle, *, splits: tuple[Split, ...] = SPLITS) -> Dataset:
    """Validate the complete evidence and materialize only the requested ranking splits."""
    _require(bool(splits) and set(splits).issubset(SPLITS), "INVALID_DATASET_SPLITS")
    _validate_windows(evidence)
    _validate_source_ledger(evidence)
    checked_lineage = evidence.provenance in ("VERIFIED_COMMERCIAL", "SIMULATED_DEMO")
    _unique((r.order_id for r in evidence.receipts), "DUPLICATE_RECEIPT")
    _unique(((r.source, r.return_item_id) for r in evidence.returns), "DUPLICATE_RETURN")
    _unique((p.event_id for p in evidence.interaction_purchases), "DUPLICATE_PURCHASE_INTERACTION")
    _require(all(row.observed_at <= evidence.exported_at
                 for stream in (evidence.receipts, evidence.returns, evidence.interaction_purchases) for row in stream),
             "FUTURE_EVENT_OBSERVATION")

    receipts, returns, purchases = defaultdict(list), defaultdict(list), defaultdict(list)
    purchased_by_order = {}
    receipt_lines = {}
    interaction_line_keys = set()
    order_receipts = {}
    for receipt in evidence.receipts:
        products = list(receipt_products(receipt.customer_id, *receipt.history_row()[1:]))
        if checked_lineage:
            _canonical_ids((receipt.order_id, receipt.customer_id), "INVALID_RECEIPT_IDENTITY")
            _require(len(products) == sum(line.get("purchase_type", "PRODUCT") == "PRODUCT"
                                         for line in receipt.lines), "UNRESOLVED_COMMERCIAL_PRODUCT_LINE")
            for line_id, product_id, variant_id, _ in products:
                _canonical_ids((line_id, product_id, variant_id), "INVALID_RECEIPT_LINE_IDENTITY")
                _require(evidence.receipt_variant_mapping.get(variant_id) == product_id,
                         "COMMERCIAL_VARIANT_PARENT_MISMATCH")
                interaction_line_keys.add((receipt.customer_id, receipt.order_id, line_id, variant_id, product_id))
        purchased_by_order[receipt.order_id] = {product_id for _, product_id, _, _ in products}
        receipt_lines[receipt.order_id] = {line_id: (variant_id, quantity) for line_id, _, variant_id, quantity in products}
        order_receipts[receipt.order_id] = receipt
        receipts[receipt.customer_id].append(receipt)
    return_identities = {}
    simulated_return_quantities = Counter()
    for row in evidence.returns:
        if checked_lineage:
            _canonical_ids((row.return_item_id,), "INVALID_RETURN_ITEM_IDENTITY")
        receipt = order_receipts.get(row.order_id)
        line = receipt_lines.get(row.order_id, {}).get(row.order_item_id)
        _require(receipt is not None and receipt.customer_id == row.customer_id and receipt.occurred_at <= row.occurred_at
                 and line is not None and line[0] == row.variant_id and 0 < Decimal(row.quantity) <= line[1],
                 "RETURN_WITHOUT_MATCHING_RECEIPT_LINE")
        identity = (row.customer_id, row.order_id, row.order_item_id, row.variant_id, Decimal(row.quantity))
        _require(return_identities.setdefault(row.return_item_id, identity) == identity,
                 "DUPLICATE_RETURN_IDENTITY_CONFLICT")
        if evidence.provenance == "SIMULATED_DEMO":
            line_key = (row.order_id, row.order_item_id)
            simulated_return_quantities[line_key] += Decimal(row.quantity)
            _require(simulated_return_quantities[line_key] <= line[1], "SIMULATED_RETURN_EXCEEDS_PURCHASE")
        returns[row.customer_id].append(row)
    for row in evidence.interaction_purchases:
        purchases[row.customer_id].append(row)
    if checked_lineage:
        _canonical_ids((row.event_id for row in evidence.interaction_purchases), "INVALID_INTERACTION_EVENT_ID")
        linked = [(row.customer_id, row.order_id, row.order_item_id, row.variant_id, row.product_id)
                  for row in evidence.interaction_purchases]
        _require(len(linked) == len(interaction_line_keys) and set(linked) == interaction_line_keys,
                 "COMMERCIAL_INTERACTION_LINEAGE_MISMATCH")
        _require(all(order_receipts[row.order_id].occurred_at == row.occurred_at
                     for row in evidence.interaction_purchases), "COMMERCIAL_INTERACTION_TIME_MISMATCH")

    attributes = None
    queries = []
    for window in evidence.windows:
        if window.split not in splits:
            continue
        if checked_lineage:
            _canonical_ids((row.product_id for row in window.catalog), "INVALID_CUTOFF_PRODUCT_ID")
        available = _verified_availability(window, evidence)
        view = _projection(window)
        for taxonomy in view.payload.taxonomies:
            if taxonomy.versionCode == evidence.taxonomy_version:
                _require(taxonomy.manifestSha256 == evidence.taxonomy_sha256, "TAXONOMY_HASH_DRIFT")
                attributes = tuple(item["attributeCode"] for item in taxonomy.manifest["attributes"])

        active_catalog = tuple(CatalogProduct(
            row.product_id, row.product_code, row.name, row.category_code, tuple(sorted(set(row.ingredient_codes)))
        ) for row in window.catalog)
        commercial = set(available) if available is not None else {row.product_id for row in window.catalog if row.commercial}
        if available is not None:
            projected = {variant.id: variant.productId for variant in view.payload.variants}
            _require(all(projected.get(variant, product) == product
                         for product, variants in available.items() for variant in variants),
                     "TASTE_AVAILABILITY_PARENT_CONFLICT")
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
            first_outcome, repeat_outcome = _retained_outcomes(window, customer_id, customer_receipts,
                                                               returns[customer_id])
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
                scores, has_signal = level2_hybrid_scores(content, cf, tuple(sorted(ids)))
                outcome = first_outcome if mode == "EXPLORE" else repeat_outcome
                samples = tuple(Sample(
                    ranking_features(product, history, window.cutoff, scores[product.product_id],
                                     content[product.product_id], cf[product.product_id], taste,
                                     available.get(product.product_id) if available is not None else None),
                    product.product_id in outcome, popularity[product.product_id],
                ) for product in artifact.products if product.product_id in ids)
                queries.append(RankingQuery(customer_id, mode, window.split, window.cutoff, window.outcome_freeze,
                                            eligible, has_signal, taxonomy_compatible, samples,
                                            customer.survey is not None))

    _require(bool(attributes), "VERIFIED_MODEL_TAXONOMY_MISSING")
    dataset_sha = fingerprint({
        "feature_schema": FEATURE_SCHEMA, "matching_policy_version": EXACT_POLICY_VERSION,
        "outcome_label_policy": OUTCOME_LABEL_POLICY, "evidence": evidence.model_dump(mode="json"),
    })
    return Dataset(evidence.taxonomy_version, evidence.taxonomy_sha256, attributes, dataset_sha, tuple(queries),
                   provenance=evidence.provenance)
