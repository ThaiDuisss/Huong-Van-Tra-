from datetime import datetime
from typing import Annotated, Literal

from pydantic import Field, StringConstraints

from app.schemas.taste_catalog import CODE, Digest, Identifier, Preparation, StrictDocument, Version

TasteCode = Annotated[str, StringConstraints(pattern=f"^{CODE.pattern}$")]
Weight = Annotated[int, Field(ge=1, le=100)]
Score = Annotated[str, StringConstraints(pattern=r"^(?:0\.[0-9]{4}|1\.0000)$")]
TasteStatus = Literal[
    "OK",
    "INSUFFICIENT_MATCHES",
    "NO_MATCH",
    "NO_CRITERIA",
    "TASTE_PROFILE_NOT_READY",
    "TASTE_CATALOG_UNAVAILABLE",
    "TAXONOMY_MAPPING_REQUIRED",
]


class HardGroup(StrictDocument):
    attribute: TasteCode
    allowedValues: list[TasteCode] = Field(min_length=1, max_length=32)


class Priority(StrictDocument):
    value: TasteCode
    weight: Weight


class SoftGroup(StrictDocument):
    attribute: TasteCode
    priorities: list[Priority] = Field(min_length=1, max_length=32)


class TasteCriteriaDocument(StrictDocument):
    hardGroups: list[HardGroup] = Field(default_factory=list, max_length=32)
    softGroups: list[SoftGroup] = Field(default_factory=list, max_length=32)
    notApplicableAttributes: list[TasteCode] = Field(default_factory=list, max_length=32)


class TastePreviewRequest(TasteCriteriaDocument):
    taxonomyVersion: Version
    taxonomySha256: Digest
    preparationCode: Preparation
    requestSequence: int = Field(ge=0, le=9007199254740991)
    limit: int = Field(default=5, ge=1, le=5)


class HardMatch(StrictDocument):
    attribute: TasteCode
    matchedValues: list[TasteCode]


class SoftResult(StrictDocument):
    attribute: TasteCode
    value: TasteCode
    weight: Weight
    status: Literal["MATCHED", "NOT_MATCHED", "UNKNOWN"]


class CompatibleVariant(StrictDocument):
    variantId: Identifier
    variantName: str
    sku: str
    profileId: Identifier
    profileRevision: int
    profileSha256: Digest
    compositionSha256: Digest
    score: Score
    matchedHard: list[HardMatch]
    matchedSoft: list[SoftResult]
    unmatchedSoft: list[SoftResult]
    unknownAttributes: list[TasteCode]
    reason: str
    sensoryValues: dict[TasteCode, list[TasteCode]] = Field(
        default_factory=dict, description="Values recorded in the approved profile for this preparation."
    )


class TasteProduct(StrictDocument):
    productId: Identifier
    productName: str
    score: Score
    compatibleVariants: list[CompatibleVariant]


class TastePreviewResponse(StrictDocument):
    status: TasteStatus
    rankingSource: Literal["TASTE_RULES_V1"] = "TASTE_RULES_V1"
    taxonomyVersion: Version
    taxonomySha256: Digest
    preparationCode: Preparation
    requestSequence: int
    criteriaHash: Digest
    catalogRevision: int | None
    catalogAsOf: datetime | None
    requestedK: int
    returnedK: int = 0
    eligibleVariantCount: int = 0
    matchedProductCount: int = 0
    items: list[TasteProduct] = Field(default_factory=list)
