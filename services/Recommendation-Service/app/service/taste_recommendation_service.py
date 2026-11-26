from decimal import Decimal, ROUND_HALF_UP
import logging
from typing import Callable

from app.recommendation.taste_explanations import AttributeDefinition, explain_variant
from app.recommendation.taste_rules import TasteCriteria, VariantMatch, maximum_points, rank_variants
from app.schemas.taste_catalog import Taxonomy, canonical_bytes, digest, not_applicable_attributes
from app.schemas.taste_recommendation import (
    CompatibleVariant,
    TasteCriteriaDocument,
    TastePreviewRequest,
    TastePreviewResponse,
    TasteProduct,
    TasteStatus,
)
from app.service.level2_availability import AvailabilityUnavailable
from app.service.taste_catalog_sync_service import ProjectionView

logger = logging.getLogger(__name__)


class InvalidTasteCriteria(ValueError):
    pass


def _validate_values(values: list[str]) -> None:
    if "UNKNOWN" in values:
        raise InvalidTasteCriteria("UNKNOWN không phải lựa chọn khẩu vị")
    if len(set(values)) != len(values):
        raise InvalidTasteCriteria("Giá trị khẩu vị bị trùng trong cùng nhóm")
    if "NONE" in values and len(values) > 1:
        raise InvalidTasteCriteria("NONE không thể đi cùng giá trị khác trong nhóm")


def normalize_criteria(document: TasteCriteriaDocument, taxonomy_sha256: str) -> TasteCriteria:
    hard: dict[str, tuple[str, ...]] = {}
    soft: dict[str, dict[str, int]] = {}
    for group in document.hardGroups:
        if group.attribute == "UNKNOWN" or group.attribute in hard:
            raise InvalidTasteCriteria("Nhóm bắt buộc có mã không hợp lệ hoặc bị trùng")
        _validate_values(group.allowedValues)
        hard[group.attribute] = tuple(sorted(group.allowedValues))

    for group in document.softGroups:
        if group.attribute == "UNKNOWN" or group.attribute in soft:
            raise InvalidTasteCriteria("Nhóm ưu tiên có mã không hợp lệ hoặc bị trùng")
        values = [item.value for item in group.priorities]
        _validate_values(values)
        if group.attribute in hard and not set(values).issubset(hard[group.attribute]):
            raise InvalidTasteCriteria("Giá trị ưu tiên phải nằm trong danh sách bắt buộc của cùng thuộc tính")
        soft[group.attribute] = dict(sorted((item.value, item.weight) for item in group.priorities))

    hard = dict(sorted(hard.items()))
    soft = dict(sorted(soft.items()))
    not_applicable = tuple(sorted(document.notApplicableAttributes))
    if ("UNKNOWN" in not_applicable or len(set(not_applicable)) != len(not_applicable)
            or set(not_applicable).intersection(hard.keys() | soft.keys())):
        raise InvalidTasteCriteria("Tiêu chí không áp dụng không thể bị trùng hoặc có lựa chọn")
    canonical_document = {
        "hardGroups": [{"attribute": code, "allowedValues": list(values)} for code, values in hard.items()],
        "softGroups": [
            {"attribute": code, "priorities": [{"value": value, "weight": weight} for value, weight in values.items()]}
            for code, values in soft.items()
        ],
    }
    if not_applicable:
        canonical_document["notApplicableAttributes"] = list(not_applicable)
    if len(canonical_bytes(canonical_document)) > 16 * 1024:
        raise InvalidTasteCriteria("Tiêu chí khẩu vị vượt quá 16 KiB")
    return TasteCriteria(hard, soft, digest({"criteria": canonical_document, "taxonomySha256": taxonomy_sha256}),
                         not_applicable)


def _definitions(release: Taxonomy) -> dict[str, AttributeDefinition]:
    # ProjectionView has already validated the manifest, including its selection kinds.
    return {
        item["attributeCode"]: AttributeDefinition(
            item["label"],
            item["maxSelections"] if item["selectionKind"] == "ENUM_MULTI" else 1,
            {option["valueCode"]: option["label"] for option in item["values"]},
        )
        for item in release.manifest["attributes"]
    }


def validate_criteria_taxonomy(criteria: TasteCriteria, release: Taxonomy) -> dict[str, AttributeDefinition]:
    definitions = _definitions(release)
    if not set(criteria.not_applicable).issubset(not_applicable_attributes(release)):
        raise InvalidTasteCriteria("Thuộc tính không hỗ trợ lựa chọn không áp dụng")
    for groups in (criteria.hard, criteria.soft):
        for code, values in groups.items():
            definition = definitions.get(code)
            if definition is None or not set(values).issubset(definition.value_labels):
                raise InvalidTasteCriteria("Thuộc tính hoặc giá trị không thuộc taxonomy đã chọn")
            if len(values) > definition.maximum:
                raise InvalidTasteCriteria("Số lựa chọn vượt quá giới hạn của thuộc tính")
    return definitions


def _display_score(points: int, maximum: int) -> str:
    return format((Decimal(points) / maximum).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP), "f")


def _compatible(match: VariantMatch, score: str, definitions: dict[str, AttributeDefinition]) -> CompatibleVariant:
    candidate = match.candidate
    return CompatibleVariant(
        variantId=candidate.variant.id,
        variantName=candidate.variant.name,
        sku=candidate.variant.sku,
        preparationCode=candidate.profile.preparationCode,
        profileId=candidate.profile.id,
        profileRevision=candidate.profile.profileRevision,
        profileSha256=candidate.profile.profileSha256,
        profileTaxonomyVersion=candidate.profile.taxonomyVersion,
        profileTaxonomySha256=candidate.profile.taxonomySha256,
        compositionSha256=candidate.profile.compositionSha256,
        score=score,
        matchedHard=match.matched_hard,
        matchedSoft=match.matched_soft,
        unmatchedSoft=match.unmatched_soft,
        unknownAttributes=match.unknown_attributes,
        reason=explain_variant(match, definitions),
        sensoryValues=candidate.profile.sensoryValues,
    )


def preview_taste(
    request: TastePreviewRequest,
    read_projection: Callable[[], ProjectionView],
    available_variant_ids: Callable[[tuple[str, ...]], set[str]] | None = None,
) -> TastePreviewResponse:
    criteria = normalize_criteria(request, request.taxonomySha256)
    response = TastePreviewResponse(
        status="NO_CRITERIA",
        taxonomyVersion=request.taxonomyVersion,
        taxonomySha256=request.taxonomySha256,
        preparationCode=request.preparationCode,
        requestSequence=request.requestSequence,
        criteriaHash=criteria.digest,
        catalogRevision=None,
        catalogAsOf=None,
        requestedK=request.limit,
    )
    has_matching_criteria = bool(criteria.hard or criteria.soft)
    if not has_matching_criteria and not criteria.not_applicable:
        return response

    view = read_projection()
    response.catalogRevision = view.projection_revision
    response.catalogAsOf = view.source_read_at
    if view.status not in ("READY", "EMPTY") or view.payload is None:
        response.status = "TASTE_CATALOG_UNAVAILABLE"
        return response

    release = next((item for item in view.payload.taxonomies if item.versionCode == request.taxonomyVersion), None)
    if (release is None or request.taxonomyVersion not in view.supported_taxonomies
            or release.manifestSha256 != request.taxonomySha256):
        response.status = "TAXONOMY_MAPPING_REQUIRED"
        return response

    definitions = validate_criteria_taxonomy(criteria, release)
    if not has_matching_criteria:
        return response
    candidates = view.consultation_variants(request.taxonomyVersion, request.preparationCode)
    if not candidates:
        response.status = "TASTE_PROFILE_NOT_READY"
        return response

    if available_variant_ids is not None:
        try:
            in_stock_ids = available_variant_ids(tuple(dict.fromkeys(item.variant.id for item in candidates)))
        except AvailabilityUnavailable as failure:
            logger.warning("taste_preview_availability_unavailable reason=%s", failure)
            response.status = "TASTE_CATALOG_UNAVAILABLE"
            return response
        candidates = tuple(item for item in candidates if item.variant.id in in_stock_ids)
        if not candidates:
            response.status = "TASTE_PROFILE_NOT_READY"
            return response

    response.eligibleVariantCount = len({item.variant.id for item in candidates})
    ranked = rank_variants(candidates, criteria)
    if not ranked:
        response.status = "NO_MATCH"
        return response

    denominator = maximum_points(criteria)
    groups: dict[str, TasteProduct | None] = {}
    selected_variant_ids: set[str] = set()
    products = []
    for match in ranked:
        product = match.candidate.product
        if product.id not in groups:
            # Keep counting all matched products without serializing those beyond the limit.
            selected = None
            if len(products) < request.limit:
                selected = TasteProduct(productId=product.id, productName=product.name,
                                        score=_display_score(match.points, denominator), compatibleVariants=[])
                products.append(selected)
            groups[product.id] = selected
        selected = groups[product.id]
        variant_id = match.candidate.variant.id
        if selected is not None and variant_id not in selected_variant_ids:
            selected.compatibleVariants.append(_compatible(match, _display_score(match.points, denominator), definitions))
            selected_variant_ids.add(variant_id)

    status: TasteStatus = "OK" if len(products) == request.limit else "INSUFFICIENT_MATCHES"
    response.status = status
    response.matchedProductCount = len(groups)
    response.items = products
    response.returnedK = len(products)
    return response
