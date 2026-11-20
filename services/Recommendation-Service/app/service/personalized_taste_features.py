from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from typing import Callable, Literal

from app.clients.customer_taste_client import CustomerTasteClient, CustomerTasteError
from app.recommendation.taste_rules import TasteCriteria
from app.schemas.taste_catalog import SnapshotError, utc_timestamp
from app.service.taste_catalog_sync_service import ProjectionView, RankableVariant
from app.service.taste_recommendation_service import (
    InvalidTasteCriteria, normalize_criteria, validate_criteria_taxonomy,
)

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


def _variant_features(row: RankableVariant, criteria: TasteCriteria) -> VariantTasteFeatures:
    profile = row.profile
    observed = {attribute: set(values) - {"UNKNOWN"} for attribute, values in profile.sensoryValues.items()}
    hard = {
        attribute: bool(observed[attribute].intersection(allowed)) if observed.get(attribute) else None
        for attribute, allowed in criteria.hard.items()
    }
    soft = {
        attribute: (max((weight for value, weight in priorities.items() if value in observed[attribute]), default=0)
                    / max(priorities.values())) if observed.get(attribute) else None
        for attribute, priorities in criteria.soft.items()
    }
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
        try:
            submitted_at = utc_timestamp(survey.submittedAt)
            criteria = normalize_criteria(survey.criteria, survey.taxonomySha256)
        except (InvalidTasteCriteria, SnapshotError):
            return TasteFeatureBatch("SURVEY_INVALID")
        if (submitted_at > as_of or criteria.digest != survey.criteriaSha256
                or not (criteria.hard or criteria.soft)):
            return TasteFeatureBatch("SURVEY_INVALID")

        view = self.read_projection()
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

        features = self._products(view, criteria, survey.taxonomyVersion, product_ids)
        status: TasteFeatureStatus = "PROFILES_UNAVAILABLE"
        if any(feature.status == "AVAILABLE" for feature in features.values()):
            status = "TASTE_FEATURE_READY"
        elif any(feature.status == "SENSORY_MISSING" for feature in features.values()):
            status = "SENSORY_MISSING"
        return TasteFeatureBatch(status, products=features, **metadata)

    @staticmethod
    def _products(view: ProjectionView, criteria: TasteCriteria, taxonomy_version: str,
                  product_ids: set[str]) -> dict[str, ProductTasteFeatures]:
        assert view.payload is not None
        product_by_variant = {variant.id: variant.productId for variant in view.payload.variants}
        profiled_products = {product_by_variant[profile.variantId] for profile in view.payload.profiles}
        grouped: dict[str, list[VariantTasteFeatures]] = defaultdict(list)
        for row in view.rankable_variants(taxonomy_version):
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
