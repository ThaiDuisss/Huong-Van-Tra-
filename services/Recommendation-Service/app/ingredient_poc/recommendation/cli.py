"""Explicit offline build/infer/demo/sanity-run commands; no DB or serving imports."""
import argparse
import json
from pathlib import Path

from app.ingredient_poc.synthetic import canonical_json
from app.ingredient_poc.recommendation.data import ResearchDataset, external, sha256
from app.ingredient_poc.recommendation.evaluation import demos, evaluate
from app.ingredient_poc.recommendation.model import IngredientRecommender


def write_json(path, value):
    path = external(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as output:
        output.write(canonical_json(value) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("build", "infer", "demo", "evaluate", "run"))
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--dataset-sha256", required=True)
    parser.add_argument("--model", type=Path)
    parser.add_argument("--model-sha256")
    parser.add_argument("--customer")
    parser.add_argument("--mode", choices=("PRODUCT", "CUSTOM_BOM", "UNIFIED"), default="UNIFIED")
    parser.add_argument("--policy", choices=("DISCOVERY", "REPEAT"), default="DISCOVERY")
    parser.add_argument("--as-of")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--split", choices=("VALIDATION", "TEST"), default="VALIDATION")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args(argv)
    data = ResearchDataset.load(args.dataset, args.dataset_sha256)
    if args.command in ("build", "run"):
        if not args.output_dir:
            parser.error("build/run requires a new external --output-dir")
        output = external(args.output_dir)
        output.mkdir(parents=True, exist_ok=False)
        model = IngredientRecommender.fit(data)
        model_sha = model.save(output / "ingredient-research-v1.json")
        reloaded = IngredientRecommender.load(output / "ingredient-research-v1.json", model_sha, data)
        manifest = {"dataset_id": data.id, "dataset_sha256": data.file_sha256,
                    "source_snapshot_hash": data.dataset["source_snapshot_hash"],
                    "model_sha256": model_sha, "model_fingerprint": model.payload()["model_fingerprint"],
                    "metadata": model.metadata, "test_used_for_tuning": False,
                    "latent_sidecar_opened": False, "network_or_database_used": False}
        if args.command == "run":
            demo = demos(reloaded)
            if canonical_json(demo) != canonical_json(demos(model)):
                raise ValueError("artifact reload changed inference")
            write_json(output / "demos.json", demo)
            # Fixed rules are frozen before validation and never changed from metrics.
            validation, validation_predictions = evaluate(reloaded, "VALIDATION")
            write_json(output / "validation-metrics.json", validation)
            write_json(output / "validation-predictions.json", validation_predictions)
            test, test_predictions = evaluate(reloaded, "TEST")
            write_json(output / "test-metrics.json", test)
            write_json(output / "test-predictions.json", test_predictions)
            manifest["sanity_summary"] = {"validation": validation["overall"], "test": test["overall"]}
        write_json(output / "run-manifest.json", manifest)
        write_json(output / "artifact-checksums.json", {p.name: sha256(p) for p in sorted(output.iterdir()) if p.is_file()})
        print(json.dumps({"status": "PASS", "output_dir": str(output), **manifest}, ensure_ascii=True, indent=2))
        return 0
    if not args.model or not args.model_sha256:
        parser.error("inference/demo/evaluation requires --model and --model-sha256")
    model = IngredientRecommender.load(args.model, args.model_sha256, data)
    if args.command == "infer":
        if not args.customer or not args.as_of:
            parser.error("infer requires --customer and --as-of with timezone")
        result = model.recommend(args.customer, as_of=args.as_of, mode=args.mode,
                                 top_k=args.top_k, policy=args.policy)
    elif args.command == "demo":
        result = demos(model)
    else:
        metrics, predictions = evaluate(model, args.split)
        result = {"metrics": metrics, "predictions": predictions}
    if args.output:
        write_json(args.output, result)
    else:
        print(json.dumps(result, ensure_ascii=True, indent=2))
    return 0
