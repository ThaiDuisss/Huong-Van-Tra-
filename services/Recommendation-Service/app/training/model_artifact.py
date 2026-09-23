from dataclasses import dataclass
from datetime import datetime
import os
from pathlib import Path
import pickle
import tempfile
from typing import Any

from lightfm import LightFM
from scipy.sparse import csr_matrix

from app.training.trainer import TrainingResult


ARTIFACT_FORMAT_VERSION = 1


class ModelArtifactError(RuntimeError):
    """Raised when a persisted LightFM artifact is absent or incompatible."""


@dataclass(frozen=True, slots=True)
class LightFMModelArtifact:
    format_version: int
    model_type: str
    contract_version: str
    trained_at: datetime
    model: LightFM
    item_features: csr_matrix
    user_id_map: dict[str, int]
    item_id_map: dict[str, int]
    training_metadata: dict[str, Any]
    dataset_metadata: dict[str, Any]

    @classmethod
    def from_training_result(
        cls,
        result: TrainingResult,
    ) -> "LightFMModelArtifact":
        from dataclasses import asdict

        return cls(
            format_version=ARTIFACT_FORMAT_VERSION,
            model_type=result.metadata.model_type,
            contract_version=result.metadata.contract_version,
            trained_at=result.metadata.completed_at,
            model=result.model,
            item_features=result.dataset_bundle.item_features.tocsr(copy=True),
            user_id_map=dict(result.dataset_bundle.user_id_map),
            item_id_map=dict(result.dataset_bundle.item_id_map),
            training_metadata=asdict(result.metadata),
            dataset_metadata=asdict(result.dataset_bundle.metadata),
        )

    def validate(self) -> None:
        if self.format_version != ARTIFACT_FORMAT_VERSION:
            raise ModelArtifactError(
                f"Unsupported artifact format version: {self.format_version}"
            )
        if not self.user_id_map:
            raise ModelArtifactError("Artifact has no user mapping")
        if not self.item_id_map:
            raise ModelArtifactError("Artifact has no item mapping")
        if sorted(self.user_id_map.values()) != list(range(len(self.user_id_map))):
            raise ModelArtifactError("Artifact user mapping is not contiguous")
        if sorted(self.item_id_map.values()) != list(range(len(self.item_id_map))):
            raise ModelArtifactError("Artifact item mapping is not contiguous")
        if self.item_features.shape[0] != len(self.item_id_map):
            raise ModelArtifactError(
                "Artifact item-feature rows do not match the item mapping"
            )
        if (
            self.model.user_embeddings is None
            or self.model.user_embeddings.shape[0] != len(self.user_id_map)
        ):
            raise ModelArtifactError(
                "Artifact model user rows do not match the user mapping"
            )
        if (
            self.model.item_embeddings is None
            or self.model.item_embeddings.shape[0] != self.item_features.shape[1]
        ):
            raise ModelArtifactError(
                "Artifact model item-feature rows do not match the feature matrix"
            )


class ModelArtifactStore:
    """Loads trusted local artifacts and caches them until the file changes."""

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self._cached: LightFMModelArtifact | None = None
        self._cached_mtime_ns: int | None = None

    def save(self, artifact: LightFMModelArtifact) -> Path:
        artifact.validate()
        destination = self.path
        destination.parent.mkdir(parents=True, exist_ok=True)

        temporary_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="wb",
                prefix=f".{destination.name}.",
                suffix=".tmp",
                dir=destination.parent,
                delete=False,
            ) as temporary_file:
                temporary_path = Path(temporary_file.name)
                pickle.dump(
                    artifact,
                    temporary_file,
                    protocol=pickle.HIGHEST_PROTOCOL,
                )
                temporary_file.flush()
                os.fsync(temporary_file.fileno())
            os.replace(temporary_path, destination)
        finally:
            if temporary_path is not None and temporary_path.exists():
                temporary_path.unlink()

        stat = destination.stat()
        self._cached = artifact
        self._cached_mtime_ns = stat.st_mtime_ns
        return destination

    def load(self) -> LightFMModelArtifact:
        if not self.path.is_file():
            raise ModelArtifactError(f"Model artifact not found: {self.path}")

        mtime_ns = self.path.stat().st_mtime_ns
        if self._cached is not None and self._cached_mtime_ns == mtime_ns:
            return self._cached

        try:
            with self.path.open("rb") as artifact_file:
                artifact = pickle.load(artifact_file)
        except Exception as exc:
            raise ModelArtifactError(
                f"Cannot load model artifact: {self.path}"
            ) from exc

        if not isinstance(artifact, LightFMModelArtifact):
            raise ModelArtifactError("Unexpected model artifact type")
        artifact.validate()
        self._cached = artifact
        self._cached_mtime_ns = mtime_ns
        return artifact
