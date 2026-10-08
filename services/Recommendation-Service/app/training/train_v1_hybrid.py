"""Build the V1 serving artifact from a read-only DB snapshot, never legacy models."""
import argparse
import json
from pathlib import Path
import sys

from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_data import readonly_session
from app.recommendation.v1_model import build_artifact
from app.repositories.v1_recommendation_repository import V1RecommendationRepository


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        repository = V1RecommendationRepository()
        with readonly_session(args.env_file) as (db, settings):
            products = repository.active_catalog(db)
            purchases = repository.build_purchase_counts(db)
            missing = repository.missing_purchase_order_events(db)
        artifact = build_artifact(products, purchases, missing)
        output = args.output or Path(settings.v1_artifact_path)
        if output.resolve() == Path(settings.model_artifact_path).resolve() or output.suffix != ".npz":
            raise ValueError("V1 output must be a separate .npz artifact")
        V1ArtifactStore(output).save(artifact)
        print(json.dumps({"artifactPath": str(output.resolve()), "contract": artifact.contract_version,
                          "createdAt": artifact.created_at.isoformat(), "products": len(products),
                          "features": len(artifact.vocabulary), "catalogSha256": artifact.catalog_sha256,
                          "historySha256": artifact.history_sha256, "missingPurchaseOrderEvents": missing}, indent=2))
    except Exception as exc:
        print(f"V1 refresh failed: {type(exc).__name__}. Check DB/config/artifact inputs.", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
