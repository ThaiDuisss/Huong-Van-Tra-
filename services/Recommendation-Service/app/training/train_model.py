import json
import logging

from app.core.config import get_settings
from app.database.session import SessionLocal
from app.training.dataset_builder import TrainingDatasetBuilder
from app.training.model_artifact import (
    LightFMModelArtifact,
    ModelArtifactStore,
)
from app.training.trainer import LightFMTrainer


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    settings = get_settings()

    with SessionLocal() as db:
        bundle = TrainingDatasetBuilder().build(db)

    result = LightFMTrainer().train(bundle)
    artifact = LightFMModelArtifact.from_training_result(result)
    artifact_path = ModelArtifactStore(settings.model_artifact_path).save(artifact)

    print(
        json.dumps(
            {
                "artifactPath": str(artifact_path.resolve()),
                "modelType": artifact.model_type,
                "contractVersion": artifact.contract_version,
                "trainedAt": artifact.trained_at.isoformat(),
                "dataset": artifact.dataset_metadata,
                "training": artifact.training_metadata,
            },
            default=str,
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
