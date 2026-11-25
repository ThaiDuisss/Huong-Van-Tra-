"""Checksum-pinned model loader used only by the isolated rehearsal."""
from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path
import json
import logging

import numpy as np

from app.recommendation.level2_demo_features import COMPONENTS, SCHEMA, Matrix, behavioral_mask, names_for

logger = logging.getLogger(__name__)
BASELINE_SHA256 = "08ea6509f9bf4e5ca912f27e3569d10e507f636d2e9423ca32e3aeb72be3baed"
APPROVAL = "HVTRA-AI-L2-04"
APPROVED = {
    "EXPLORE": ("explore-h18-hybrid_b", "d6113e6b239c9ea0a845bd6791b069187a17f10ea049252dec06e5883b419e10",
                "HYBRID", "HYBRID_B", 18),
    "FAMILIAR": ("familiar-h60-logistic_t", "161cc2c12661175a25d235eb4ac59e924a8ba9574abb1bd49d9effaa5c43d9f5",
                 "LOGISTIC", "LOGISTIC_T", 60),
}


def checked_document(path: Path, checksum: str, limit: int = 2_000_000):
    if not path.is_absolute() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("ISOLATED_DOCUMENT_PATH")
    raw = path.read_bytes()
    if sha256(raw).hexdigest() != checksum:
        raise ValueError("ISOLATED_CHECKSUM_MISMATCH")
    def unique_object(pairs):
        value = dict(pairs)
        if len(value) != len(pairs):
            raise ValueError("ISOLATED_DUPLICATE_JSON_KEY")
        return value

    return json.loads(raw, object_pairs_hook=unique_object)


@dataclass(frozen=True, slots=True)
class DemoModel:
    version: str
    family: str
    horizon: int
    schema_names: tuple[str, ...]
    categories: tuple[str, ...]
    weights: np.ndarray | None
    centers: np.ndarray | None
    scales: np.ndarray | None
    coefficients: np.ndarray | None
    intercept: float | None

    def score(self, matrix: Matrix) -> np.ndarray:
        if matrix.names != self.schema_names or matrix.values.shape[1] != len(self.schema_names):
            raise ValueError("ISOLATED_FEATURE_SCHEMA_MISMATCH")
        if self.family == "HYBRID":
            scores = matrix.components @ self.weights
        else:
            scores = ((matrix.values - self.centers) / self.scales) @ self.coefficients + self.intercept
        if not np.isfinite(scores).all():
            raise ValueError("ISOLATED_INFERENCE_INVALID")
        return scores


def _vector(parameters, key, size):
    data = np.asarray(parameters[key], dtype=np.float64)
    if data.shape != (size,) or not np.isfinite(data).all():
        raise ValueError("ISOLATED_PREPROCESSING_INVALID")
    data.setflags(write=False)
    return data


def validate_model(doc, mode, attributes, taxonomy_hash, catalog_hash, *, version=None) -> DemoModel:
    stem, _, family, method, horizon = APPROVED[mode]
    required = {
        "format_version": 1, "training_provenance": "SIMULATED_DEMO", "activation_status": "INACTIVE",
        "online_integration": False, "feature_schema": SCHEMA, "mode": mode,
        "model_version": version or f"l203d-{stem}", "algorithm": family, "method": method, "horizon_days": horizon,
        "taxonomy_sha256": taxonomy_hash, "catalog_payload_sha256": catalog_hash,
        "feature_dtype": "float32", "component_dtype": "float64", "tie_break": "product_uuid_ascending",
        "missingness": "existing schema flags and zero imputation; see pinned prefix contract",
    }
    if any(doc.get(key) != value for key, value in required.items()):
        raise ValueError("ISOLATED_ARTIFACT_CONTRACT")
    categories = tuple(doc["categories"])
    names = names_for(mode, attributes, categories)
    if (tuple(sorted(set(categories))) != categories or tuple(doc["taxonomy_attributes"]) != attributes
            or tuple(doc["feature_names"]) != names):
        raise ValueError("ISOLATED_FEATURE_ORDER")
    parameters = doc["parameters"]
    if family == "HYBRID":
        if (tuple(doc["component_names"]) != COMPONENTS
                or tuple(doc["zero_indices"]) != behavioral_mask(mode, attributes, categories)):
            raise ValueError("ISOLATED_COMPONENT_CONTRACT")
        weights = _vector(parameters, "weights", 4)
        if weights[3] != 0 or min(weights) < 0 or not np.isclose(weights.sum(), 1.):
            raise ValueError("ISOLATED_HYBRID_WEIGHT_INVALID")
        return DemoModel(doc["model_version"], family, horizon, names, categories, weights, None, None, None, None)
    if doc["zero_indices"] or doc["component_names"]:
        raise ValueError("ISOLATED_LOGISTIC_MASK_INVALID")
    centers, scales, coefficients = (
        _vector(parameters, key, len(names)) for key in ("centers", "scales", "coefficients")
    )
    intercept = float(parameters["intercept"])
    if min(scales) <= 0 or not np.isfinite(intercept):
        raise ValueError("ISOLATED_SCALER_INVALID")
    return DemoModel(doc["model_version"], family, horizon, names, categories, None,
                     centers, scales, coefficients, intercept)


def load_demo_models(registry: Path, attributes, taxonomy_hash, catalog_hash):
    if not registry.is_absolute() or not registry.is_dir():
        raise ValueError("ISOLATED_REGISTRY_PATH")
    models = {}
    for mode, (stem, checksum, *_) in APPROVED.items():
        try:
            path = (registry / f"{stem}.json").resolve(strict=True)
            if path.parent != registry.resolve():
                raise ValueError("ISOLATED_REGISTRY_ESCAPE")
            models[mode] = (validate_model(checked_document(path, checksum), mode, attributes,
                                           taxonomy_hash, catalog_hash), None)
        except (OSError, ValueError, TypeError, KeyError, OverflowError):
            logger.error("isolated_l2 mode=%s status=ARTIFACT_VALIDATION_FAILED", mode)
            models[mode] = (None, "ARTIFACT_VALIDATION_FAILED")
    return models
