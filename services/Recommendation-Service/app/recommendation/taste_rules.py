from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from app.schemas.taste_catalog import ADDED_SWEETNESS, NOT_APPLICABLE, SPLIT_SWEETNESS_VERSIONS, Profile
from app.schemas.taste_recommendation import HardMatch, SoftResult

if TYPE_CHECKING:
    from app.service.taste_catalog_sync_service import RankableVariant


EXACT_POLICY_VERSION = "hvtra-taste-exact-v1"


@dataclass(frozen=True)
class TasteCriteria:
    hard: dict[str, tuple[str, ...]]
    soft: dict[str, dict[str, int]]
    digest: str
    not_applicable: tuple[str, ...] = ()


@dataclass(frozen=True)
class VariantMatch:
    candidate: RankableVariant
    points: int
    matched_hard: list[HardMatch]
    matched_soft: list[SoftResult]
    unmatched_soft: list[SoftResult]
    unknown_attributes: list[str]


def maximum_points(criteria: TasteCriteria) -> int:
    return sum(max(priorities.values()) for priorities in criteria.soft.values()) or 1


def rank_variants(
    candidates: tuple[RankableVariant, ...], criteria: TasteCriteria
) -> list[VariantMatch]:
    matches = []
    for candidate in candidates:
        match = _match_variant(candidate, criteria)
        if match is not None:
            matches.append(match)

    # All variants share the same denominator for a request, so integer points
    # preserve the exact rational order, even when display scores tie.
    return sorted(matches, key=lambda item: (
        -item.points, item.candidate.product.id, item.candidate.variant.id,
    ))


def observed_taste_values(profile: Profile) -> dict[str, set[str]]:
    observed = {
        attribute: set(values) - {"UNKNOWN"}
        for attribute, values in profile.sensoryValues.items()
    }
    if profile.taxonomyVersion in SPLIT_SWEETNESS_VERSIONS and ADDED_SWEETNESS in observed:
        observed[ADDED_SWEETNESS].discard(NOT_APPLICABLE)
    return observed


def _match_variant(candidate: RankableVariant, criteria: TasteCriteria) -> VariantMatch | None:
    sensory = observed_taste_values(candidate.profile)
    matched_hard = []
    for attribute, allowed in criteria.hard.items():
        overlap = sorted(sensory.get(attribute, set()).intersection(allowed))
        if not overlap:
            return None
        matched_hard.append(HardMatch(attribute=attribute, matchedValues=overlap))

    points = 0 if criteria.soft else 1
    matched_soft = []
    unmatched_soft = []
    unknown_attributes = []
    for attribute, priorities in criteria.soft.items():
        observed = sensory.get(attribute, set())
        if not observed:
            unknown_attributes.append(attribute)
        best_weight = 0
        for value, weight in priorities.items():
            if value in observed:
                best_weight = max(best_weight, weight)
                matched_soft.append(SoftResult(
                    attribute=attribute, value=value, weight=weight, status="MATCHED",
                ))
            else:
                unmatched_soft.append(SoftResult(
                    attribute=attribute, value=value, weight=weight,
                    status="NOT_MATCHED" if observed else "UNKNOWN",
                ))
        points += best_weight

    if not criteria.hard and points == 0:
        return None
    return VariantMatch(candidate, points, matched_hard, matched_soft, unmatched_soft, unknown_attributes)
