from dataclasses import asdict
from datetime import datetime
import json
import os
from pathlib import Path
import tempfile
from threading import RLock
from zipfile import BadZipFile

import numpy as np
from scipy.sparse import csr_matrix

from app.recommendation.v1_model import (
    CatalogProduct, FEATURE_CONTRACT, NORMALIZATION_CONTRACT, V1Artifact, V1ModelError,
)


FORMAT_VERSION = 1


class V1ArtifactStore:
    """Non-pickle NPZ; atomic publish, one cached artifact per API worker."""

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self._lock = RLock()
        self._cached: V1Artifact | None = None
        self._signature: tuple[int, int, int] | None = None

    def save(self, artifact: V1Artifact) -> Path:
        artifact.validate()
        metadata = {
            "format_version": FORMAT_VERSION, "contract_version": artifact.contract_version,
            "feature_contract": FEATURE_CONTRACT, "normalization_contract": NORMALIZATION_CONTRACT,
            "created_at": artifact.created_at.isoformat(),
            "products": [asdict(p) for p in artifact.products],
            "vocabulary": list(artifact.vocabulary), "catalog_sha256": artifact.catalog_sha256,
            "history_sha256": artifact.history_sha256,
            "missing_purchase_order_events": artifact.missing_purchase_order_events,
        }
        payload = {"metadata": np.asarray(json.dumps(metadata, sort_keys=True, ensure_ascii=False)),
                   "idf": artifact.idf, "popularity": artifact.popularity}
        for name, matrix in (("content", artifact.content), ("similarity", artifact.similarity)):
            payload.update({f"{name}_data": matrix.data, f"{name}_indices": matrix.indices,
                            f"{name}_indptr": matrix.indptr, f"{name}_shape": np.asarray(matrix.shape)})
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="wb", dir=self.path.parent,
                                             prefix=f".{self.path.name}.", suffix=".tmp", delete=False) as stream:
                temporary = Path(stream.name)
                np.savez_compressed(stream, **payload)
                stream.flush()
                os.fsync(stream.fileno())
            with self._lock:
                os.replace(temporary, self.path)
                self._cached = None
                self._signature = None
        finally:
            if temporary is not None and temporary.exists():
                temporary.unlink()
        return self.path

    def load(self) -> V1Artifact:
        with self._lock:
            try:
                stat = self.path.stat()
                signature = (stat.st_mtime_ns, stat.st_size, stat.st_ino)
                if self._cached is not None and self._signature == signature:
                    return self._cached
                with np.load(self.path, allow_pickle=False) as payload:
                    meta = json.loads(str(payload["metadata"].item()))
                    if (meta["format_version"] != FORMAT_VERSION
                            or meta["feature_contract"] != FEATURE_CONTRACT
                            or meta["normalization_contract"] != NORMALIZATION_CONTRACT):
                        raise V1ModelError("Incompatible V1 serialization/feature contract")
                    matrices = {}
                    for name in ("content", "similarity"):
                        matrices[name] = csr_matrix(
                            (payload[f"{name}_data"].copy(), payload[f"{name}_indices"].copy(),
                             payload[f"{name}_indptr"].copy()),
                            shape=tuple(payload[f"{name}_shape"].tolist()))
                        matrices[name].check_format(full_check=True)
                    products = tuple(CatalogProduct(
                        p["product_id"], p["product_code"], p["name"], p["category_code"],
                        tuple(p["ingredient_codes"])) for p in meta["products"])
                    artifact = V1Artifact(
                        meta["contract_version"], datetime.fromisoformat(meta["created_at"]),
                        products, tuple(meta["vocabulary"]), payload["idf"].copy(),
                        matrices["content"], matrices["similarity"], payload["popularity"].copy(),
                        meta["catalog_sha256"], meta["history_sha256"], meta["missing_purchase_order_events"],
                    )
                artifact.validate()
            except (OSError, ValueError, KeyError, TypeError, IndexError, AttributeError,
                    EOFError, OverflowError, BadZipFile, V1ModelError) as exc:
                self._cached = None
                self._signature = None
                if isinstance(exc, V1ModelError):
                    raise
                raise V1ModelError("V1 artifact is missing, corrupt or incompatible") from exc
            for matrix in (artifact.content, artifact.similarity):
                for array in (matrix.data, matrix.indices, matrix.indptr):
                    array.flags.writeable = False
            artifact.idf.flags.writeable = False
            artifact.popularity.flags.writeable = False
            self._cached, self._signature = artifact, signature
            return artifact
