from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from typing import TYPE_CHECKING, Callable, Literal

from app.clients.customer_taste_client import CustomerTasteClient, CustomerTasteError, SubmittedTasteSurvey
from app.recommendation.taste_rules import EXACT_POLICY_VERSION, TasteCriteria, observed_taste_values
from app.schemas.taste_catalog import NOT_APPLICABLE, SnapshotError, utc_timestamp
from app.service.taste_recommendation_service import (
    InvalidTasteCriteria, normalize_criteria, validate_criteria_taxonomy,
)

if TYPE_CHECKING:
    from app.service.taste_catalog_sync_service import ProjectionView, RankableVariant

ProductTasteStatus = Literal["AVAILABLE", "SENSORY_MISSING", "PROFILE_MISSING", "PROFILE_TAXONOMY_UNSUPPORTED"]
TasteFeatureStatus = Literal[
    "TASTE_FEATURE_READY", "NOT_CONFIGURED", "SURVEY_MISSING", "SURVEY_ACCESS_BLOCKED", "SURVEY_UNAVAILABLE",
    "SURVEY_INVALID", "CATALOG_UNAVAILABLE", "CATALOG_STALE", "CATALOG_INVALID", "TAXONOMY_UNSUPPORTED",
    "TAXONOMY_MISMATCH", "PROFILES_UNAVAILABLE", "SENSORY_MISSING",
]


@dataclass(frozen=True)
class VariantTasteFeatures:
    variant_id: str
    profile_id: str
    profile_revision: int
    composition_revision: int
    composition_sha256: str
    preparation_code: str
    preparation_sha256: str
    hard_matches: dict[str, bool | None]
    soft_compatibility: dict[str, float | None]

    @property
    def has_observation(self) -> bool:
        return (any(value is not None for value in self.hard_matches.values())
                or any(value is not None for value in self.soft_compatibility.values()))


@dataclass(frozen=True)
class ProductTasteFeatures:
    status: ProductTasteStatus
    variants: tuple[VariantTasteFeatures, ...] = ()


@dataclass(frozen=True)
class TasteFeatureBatch:
    status: TasteFeatureStatus
    products: dict[str, ProductTasteFeatures] | None = None
    survey_row_version: int | None = None
    survey_submitted_at: datetime | None = None
    taxonomy_version: str | None = None
    taxonomy_sha256: str | None = None
    catalog_revision: int | None = None
    catalog_as_of: datetime | None = None
    sellable_variant_counts: dict[str, int] | None = None
    matching_policy_version: str = EXACT_POLICY_VERSION


def _variant_features(row: RankableVariant, criteria: TasteCriteria) -> VariantTasteFeatures:
    profile = row.profile
    observed = observed_taste_values(profile)
    for attribute in row.not_applicable:
        observed.get(attribute, set()).discard(NOT_APPLICABLE)

    def assess(attribute: str, requested: set[str]) -> tuple[set[str], set[str]]:
        values = observed.get(attribute)
        if not values:
            return set(), requested
        assessed = requested if row.assessed_options is None else row.assessed_options.get(attribute, frozenset())
        return requested & values, requested - assessed

    hard: dict[str, bool | None] = {}
    for attribute, allowed in criteria.hard.items():
        matches, unknown = assess(attribute, set(allowed))
        hard[attribute] = True if matches else None if unknown else False

    soft: dict[str, float | None] = {}
    for attribute, priorities in criteria.soft.items():
        matches, unknown = assess(attribute, set(priorities))
        best_match = max((priorities[value] for value in matches), default=0)
        possible_match = max((priorities[value] for value in unknown), default=0)
        soft[attribute] = None if possible_match > best_match else best_match / max(priorities.values())
    return VariantTasteFeatures(
        row.variant.id, profile.id, profile.profileRevision, profile.compositionRevision, profile.compositionSha256,
        profile.preparationCode, profile.preparationSha256, hard, soft,
    )


class PersonalizedTasteFeatures:
    def __init__(self, customer_taste: CustomerTasteClient, read_projection: Callable[[], ProjectionView]):
        self.customer_taste = customer_taste
        self.read_projection = read_projection

    def prepare(self, customer_id: str, as_of: datetime, product_ids: set[str]) -> TasteFeatureBatch:
        try:
            survey = self.customer_taste.latest(customer_id)
        except CustomerTasteError as failure:
            return TasteFeatureBatch(failure.status)
        if survey is None:
            return TasteFeatureBatch("SURVEY_MISSING")
        return self.from_observation(survey, as_of, product_ids, self.read_projection)

    @staticmethod
    def from_observation(survey: SubmittedTasteSurvey, as_of: datetime, product_ids: set[str],
                         read_projection: Callable[[], ProjectionView]) -> TasteFeatureBatch:
        try:
            submitted_at = utc_timestamp(survey.submittedAt)
            criteria = normalize_criteria(survey.criteria, survey.taxonomySha256)
        except (InvalidTasteCriteria, SnapshotError):
            return TasteFeatureBatch("SURVEY_INVALID")
        if (submitted_at > as_of or criteria.digest != survey.criteriaSha256
                or not (criteria.hard or criteria.soft)):
            return TasteFeatureBatch("SURVEY_INVALID")

        view = read_projection()
        metadata = dict(survey_row_version=survey.rowVersion, survey_submitted_at=submitted_at,
                        taxonomy_version=survey.taxonomyVersion,
                        taxonomy_sha256=survey.taxonomySha256, catalog_revision=view.projection_revision,
                        catalog_as_of=view.source_read_at)
        if view.status == "STALE":
            return TasteFeatureBatch("CATALOG_STALE", **metadata)
        if view.reason == "INVALID_SNAPSHOT":
            return TasteFeatureBatch("CATALOG_INVALID", **metadata)
        if view.status not in ("READY", "EMPTY") or view.payload is None:
            return TasteFeatureBatch("CATALOG_UNAVAILABLE", **metadata)
        if (view.source_read_at is not None
                and (view.source_read_at > as_of
                     or any(utc_timestamp(profile.approvedAt) > view.source_read_at
                            for profile in view.payload.profiles))):
            return TasteFeatureBatch("CATALOG_INVALID", **metadata)
        release = next((taxonomy for taxonomy in view.payload.taxonomies
                        if taxonomy.versionCode == survey.taxonomyVersion), None)
        if release is None or survey.taxonomyVersion not in view.supported_taxonomies:
            return TasteFeatureBatch("TAXONOMY_UNSUPPORTED", **metadata)
        if release.manifestSha256 != survey.taxonomySha256:
            return TasteFeatureBatch("TAXONOMY_MISMATCH", **metadata)
        try:
            validate_criteria_taxonomy(criteria, release)
        except InvalidTasteCriteria:
            return TasteFeatureBatch("SURVEY_INVALID", **metadata)

        features = PersonalizedTasteFeatures._products(view, criteria, survey.taxonomyVersion, product_ids)
        status: TasteFeatureStatus = "PROFILES_UNAVAILABLE"
        if any(feature.status == "AVAILABLE" for feature in features.values()):
            status = "TASTE_FEATURE_READY"
        elif any(feature.status == "SENSORY_MISSING" for feature in features.values()):
            status = "SENSORY_MISSING"
        counts = {product.id: 0 for product in view.payload.products if product.id in product_ids}
        for variant in view.payload.variants:
            if variant.sellable and variant.productId in product_ids:
                counts[variant.productId] += 1
        return TasteFeatureBatch(status, products=features, sellable_variant_counts=dict(counts), **metadata)

    @staticmethod
    def _products(view: ProjectionView, criteria: TasteCriteria, taxonomy_version: str,
                  product_ids: set[str]) -> dict[str, ProductTasteFeatures]:
        assert view.payload is not None
        product_by_variant = {variant.id: variant.productId for variant in view.payload.variants}
        profiled_products = {product_by_variant[profile.variantId] for profile in view.payload.profiles}
        grouped: dict[str, list[VariantTasteFeatures]] = defaultdict(list)
        for row in view.consultation_variants(taxonomy_version, None):
            if row.product.id in product_ids:
                grouped[row.product.id].append(_variant_features(row, criteria))

        products = {}
        for product_id in product_ids:
            variants = tuple(sorted(grouped.get(product_id, ()), key=lambda variant: (
                variant.variant_id, variant.preparation_code, variant.profile_revision,
            )))
            if variants:
                status: ProductTasteStatus = ("AVAILABLE" if any(v.has_observation for v in variants)
                                               else "SENSORY_MISSING")
            else:
                status = "PROFILE_TAXONOMY_UNSUPPORTED" if product_id in profiled_products else "PROFILE_MISSING"
            products[product_id] = ProductTasteFeatures(status, variants)
        return products
