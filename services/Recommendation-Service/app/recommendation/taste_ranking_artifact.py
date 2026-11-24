"""Validated JSON artifact and operator-approved, per-mode startup activation."""
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import hmac
import logging
from pathlib import Path
from typing import Literal

import numpy as np
from pydantic import AwareDatetime, Field, FiniteFloat, model_validator
from scipy.special import expit

from app.recommendation.taste_ranking_features import (
    CATALOG_CONTRACT, FEATURE_SCHEMA, MODES, FeatureError, Mode, RankingFeatures, feature_names, vector,
)
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import CONTRACT_VERSION, fingerprint
from app.schemas.taste_catalog import CODE, Digest, StrictDocument, Version
from app.service.personalized_taste_features import TasteFeatureBatch

logger = logging.getLogger(__name__)
MODEL_CONTRACT = "hvtra-taste-logistic-v1"
MAX_DOCUMENT_BYTES = 2 * 1024 * 1024


class LogisticModel(StrictDocument):
    attributes: list[str] = Field(min_length=1, max_length=64)
    categories: list[str] = Field(max_length=1024)
    feature_names: list[str]
    centers: list[FiniteFloat]
    scales: list[FiniteFloat]
    coefficients: list[FiniteFloat]
    intercept: FiniteFloat
    regularization: FiniteFloat = Field(gt=0)

    @model_validator(mode="after")
    def validate_shape(self):
        if (self.attributes != sorted(set(self.attributes))
                or any(not CODE.fullmatch(attribute) for attribute in self.attributes)
                or self.categories != sorted(set(self.categories))
                or any(not category.strip() for category in self.categories)):
            raise ValueError("Invalid feature vocabulary")
        if self.feature_names != list(feature_names(tuple(self.attributes), tuple(self.categories))):
            raise ValueError("Incompatible feature names")
        width = len(self.feature_names)
        if not len(self.centers) == len(self.scales) == len(self.coefficients) == width:
            raise ValueError("Incompatible model dimensions")
        if any(scale <= 0 for scale in self.scales):
            raise ValueError("Invalid feature scaling")
        return self

    def score(self, rows: list[RankingFeatures]) -> list[float]:
        if not rows:
            return []
        matrix = np.asarray([vector(row, tuple(self.attributes), tuple(self.categories)) for row in rows])
        with np.errstate(over="ignore", invalid="ignore", divide="ignore"):
            logits = ((matrix - self.centers) / self.scales) @ self.coefficients + self.intercept
        if not np.isfinite(logits).all():
            raise FeatureError("NONFINITE_RERANKER_SCORE")
        return expit(logits).tolist()


class TasteRankingArtifact(StrictDocument):
    format_version: Literal[1] = 1
    model_version: Version
    model_contract: Literal[MODEL_CONTRACT] = MODEL_CONTRACT
    feature_schema: Literal[FEATURE_SCHEMA] = FEATURE_SCHEMA
    matching_policy_version: Literal[EXACT_POLICY_VERSION] = EXACT_POLICY_VERSION
    catalog_contract: Literal[CATALOG_CONTRACT] = CATALOG_CONTRACT
    hybrid_contract: Literal[CONTRACT_VERSION] = CONTRACT_VERSION
    mode: Mode
    taxonomy_version: Version
    taxonomy_sha256: Digest
    dataset_sha256: Digest
    training_cutoff: AwareDatetime
    selection_cutoff: AwareDatetime
    evaluation_cutoff: AwareDatetime
    trained_at: AwareDatetime
    evaluated_at: AwareDatetime
    evaluation_status: Literal["EVALUATED"]
    evaluation_sha256: Digest
    activation_status: Literal["INACTIVE"] = "INACTIVE"
    model_sha256: Digest
    model: LogisticModel

    @model_validator(mode="after")
    def validate_artifact(self):
        if not (self.training_cutoff < self.selection_cutoff < self.evaluation_cutoff
                <= self.trained_at <= self.evaluated_at):
            raise ValueError("Invalid training and evaluation chronology")
        if not hmac.compare_digest(self.model_sha256, fingerprint(self.model.model_dump(mode="json"))):
            raise ValueError("Model checksum mismatch")
        return self


class ActivationApproval(StrictDocument):
    mode: Mode
    model_version: Version
    artifact_path: str = Field(min_length=1)
    artifact_sha256: Digest
    evaluation_sha256: Digest
    approval_reference: str = Field(min_length=1)
    approved_at: AwareDatetime


class ActivationManifest(StrictDocument):
    schema_version: Literal[1]
    approvals: list[ActivationApproval] = Field(min_length=1, max_length=2)

    @model_validator(mode="after")
    def validate_approvals(self):
        if len({approval.mode for approval in self.approvals}) != len(self.approvals):
            raise ValueError("Duplicate activation mode")
        return self


def _read_document(path: Path) -> bytes:
    with path.open("rb") as stream:
        document = stream.read(MAX_DOCUMENT_BYTES + 1)
    if len(document) > MAX_DOCUMENT_BYTES:
        raise ValueError("Activation or artifact exceeds size limit")
    return document


@dataclass(frozen=True, slots=True)
class RerankingResult:
    status: str
    scores: dict[str, float] | None = None


@dataclass(frozen=True, slots=True)
class TasteReranker:
    status: str = "TASTE_RANKING_NOT_ACTIVATED"
    artifact: TasteRankingArtifact | None = None

    def score(self, rows: tuple[RankingFeatures, ...], taste: TasteFeatureBatch) -> RerankingResult:
        artifact = self.artifact
        if artifact is None:
            return RerankingResult(self.status)
        if taste.status != "TASTE_FEATURE_READY":
            return RerankingResult(taste.status)
        if (taste.taxonomy_version, taste.taxonomy_sha256) != (artifact.taxonomy_version, artifact.taxonomy_sha256):
            return RerankingResult("TAXONOMY_INCOMPATIBLE")
        if taste.matching_policy_version != artifact.matching_policy_version:
            return RerankingResult("MATCHING_POLICY_INCOMPATIBLE")
        scored = [row for row in rows if row.has_signal]
        if not any(row.has_taste for row in scored):
            return RerankingResult("NO_OBSERVED_TASTE_FOR_RANKED_CANDIDATES")
        if any(row.needs_aggregation for row in scored):
            return RerankingResult("VARIANT_PREPARATION_AGGREGATION_UNAVAILABLE")
        try:
            scores = artifact.model.score(scored)
        except (FeatureError, ArithmeticError, ValueError):
            return RerankingResult("RERANKER_INFERENCE_FAILED")
        return RerankingResult("TASTE_RANKING_ACTIVE", dict(zip((row.product_id for row in scored), scores)))


def load_approved_rerankers(manifest_path: str | None) -> dict[Mode, TasteReranker]:
    rerankers = {mode: TasteReranker() for mode in MODES}
    if not manifest_path:
        logger.info("taste_ranking status=TASTE_RANKING_NOT_ACTIVATED")
        return rerankers
    try:
        manifest = Path(manifest_path)
        if not manifest.is_absolute():
            raise ValueError("Activation manifest path must be absolute")
        approvals = ActivationManifest.model_validate_json(_read_document(manifest)).approvals
    except (OSError, ValueError):
        logger.warning("taste_ranking status=ACTIVATION_MANIFEST_UNAVAILABLE")
        return {mode: TasteReranker("ACTIVATION_MANIFEST_UNAVAILABLE") for mode in MODES}

    for approval in approvals:
        try:
            path = Path(approval.artifact_path)
            if not path.is_absolute():
                raise ValueError("Artifact path must be absolute")
            raw = _read_document(path)
            if not hmac.compare_digest(approval.artifact_sha256, hashlib.sha256(raw).hexdigest()):
                raise ValueError("Artifact file checksum mismatch")
            artifact = TasteRankingArtifact.model_validate_json(raw)
            if (artifact.mode != approval.mode or artifact.model_version != approval.model_version
                    or artifact.evaluation_sha256 != approval.evaluation_sha256
                    or not approval.approval_reference.strip()
                    or not artifact.evaluated_at <= approval.approved_at <= datetime.now(timezone.utc)):
                raise ValueError("Approval does not match evaluated artifact")
            rerankers[approval.mode] = TasteReranker("APPROVED_ARTIFACT_LOADED", artifact)
            logger.info("taste_ranking mode=%s status=APPROVED_ARTIFACT_LOADED version=%s",
                        approval.mode, artifact.model_version)
        except (OSError, ValueError):
            rerankers[approval.mode] = TasteReranker("APPROVED_ARTIFACT_UNAVAILABLE")
            logger.warning("taste_ranking mode=%s status=APPROVED_ARTIFACT_UNAVAILABLE", approval.mode)
    return rerankers
