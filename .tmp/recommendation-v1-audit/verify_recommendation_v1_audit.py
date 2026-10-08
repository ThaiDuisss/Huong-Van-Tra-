"""Read-only verification of existing V1 audit evidence; never rewrites outputs.

Run with the Recommendation-Service virtual environment. Optional --env-file
performs SELECTs inside MySQL READ ONLY transactions. No training or DB writes.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import pickletools
import sys


def sha256(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def protected_files(service):
    return {
        str(path.relative_to(service)).replace("\\", "/"): sha256(path)
        for folder in ("app", "offline_benchmark", "artifacts")
        for path in sorted((service / folder).rglob("*"))
        if path.is_file() and "__pycache__" not in path.parts
        and path.suffix in {".py", ".json", ".md", ".pkl"}
    }


def verify(service, env_file):
    sys.path.insert(0, str(service))
    from offline_benchmark.common.filtering import matching_hits, top_five
    from offline_benchmark.common.manifest import digest
    from offline_benchmark.common.types import PredictionReport
    from offline_benchmark.metrics.evaluator import evaluate_reports
    from offline_benchmark.metrics.reporting import build_summary
    from offline_benchmark.models.base import blend, normalize_scores

    before = protected_files(service)
    output = service / "offline_benchmark" / "outputs"

    def read(name):
        return json.loads((output / name).read_text(encoding="utf-8"))

    manifest = read("split_manifest.json")
    summary = read("benchmark_summary.json")
    expected = {
        "total_customer_count": 980, "eligible_customer_count": 583,
        "eligible_order_count": 8615, "train_order_count": 6668,
        "test_order_count": 1947, "train_event_count": 13046,
        "test_event_count": 3859, "active_product_count": 64,
        "minimum_purchase_orders": 5,
    }
    for key, value in expected.items():
        assert manifest[key] == value, key
    source = read("order_source_audit.json")
    assert source["distinct_orders"] == 9780
    assert source["customers"] == 980
    results = []
    model_checks = []
    for row in summary["models"]:
        key = row["model_key"]
        payload, saved = read(f"{key}.json"), read(f"{key}_metrics.json")
        reports = tuple(
            PredictionReport(r["model"], r["customer_id"], tuple(r["top5"]),
                             tuple(r["test"]), tuple(r["hits"]))
            for r in payload["predictions"]
        )
        computed = evaluate_reports(reports, tuple(summary["catalog_product_ids"]))
        assert payload["split"] == manifest == saved["split"], key
        for field in computed:
            assert computed[field] == saved[field], (key, field)
        assert digest(payload) == saved["prediction_sha256"], key
        results.append(saved)
        model_checks.append({"model": key, "customers": len(reports),
                             "recomputed_metrics_exact": True,
                             "aggregate": computed["aggregate"]})
    assert build_summary(tuple(results)) == summary

    components = read("routing_component_scores.json")
    assert components["split"] == manifest
    customers = components["components"]["customers"]
    predictions = read("personalized_hybrid.json")["predictions"]
    changed = 0
    alternatives = []
    for row in predictions:
        user, values = row["customer_id"], customers[row["customer_id"]]
        ranking = top_five(blend(values["content"], values["cf"]))
        assert ranking == tuple(row["top5"]), user
        content, cf = normalize_scores(values["content"]), normalize_scores(values["cf"])
        candidate = top_five({p: 0.6 * content[p] + 0.4 * cf[p] for p in content})
        changed += candidate != ranking
        alternatives.append(PredictionReport(
            "60/40 audit probe", user, candidate, tuple(row["test"]),
            matching_hits(candidate, tuple(row["test"]))))

    research = read("prefix_history_benchmark.json")
    missing, stale = [], []
    for filename, expected_hash in research["protected_file_sha256"].items():
        path = service / filename
        if not path.is_file():
            missing.append(filename)
        elif sha256(path) != expected_hash:
            stale.append(filename)

    api_path = service / "app/api/recommendations.py"
    api_tree = ast.parse(api_path.read_text(encoding="utf-8"))
    endpoint = next(node for node in ast.walk(api_tree)
                    if isinstance(node, ast.FunctionDef) and node.name == "recommend_products")
    api_arguments = [arg.arg for arg in endpoint.args.args]
    assert "exclude_purchased" not in api_arguments
    serving = service / "app/service/recommendation_service.py"
    tree = ast.parse(serving.read_text(encoding="utf-8"))
    production_offline_imports = []
    for path in sorted((service / "app").rglob("*.py")):
        source_tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(source_tree):
            if isinstance(node, ast.ImportFrom) and (node.module or "").startswith("offline_benchmark"):
                production_offline_imports.append(str(path.relative_to(service)))
            elif isinstance(node, ast.Import) and any(
                    alias.name.startswith("offline_benchmark") for alias in node.names):
                production_offline_imports.append(str(path.relative_to(service)))
    assert not production_offline_imports
    for path in list((service / "app").rglob("*.py")) + list((service / "offline_benchmark").rglob("*.py")):
        compile(path.read_text(encoding="utf-8"), str(path), "exec")

    artifact = service / "artifacts/lightfm_model.pkl"
    artifact_metadata = {"present": artifact.is_file(), "inspection": "static pickle opcodes only; not loaded"}
    if artifact.is_file():
        ops = list(pickletools.genops(artifact.read_bytes()))
        wanted = {"user_count", "item_count", "active_product_count", "raw_event_count", "epochs", "random_seed", "num_threads", "interaction_count", "no_components", "learning_rate"}
        for i, (_, arg, _) in enumerate(ops):
            if isinstance(arg, str) and arg in wanted:
                for op, value, _ in ops[i + 1:i + 5]:
                    if op.name in {"BININT", "BININT1", "BININT2", "BINFLOAT"}:
                        artifact_metadata[arg] = value
                        break
        artifact_metadata["sha256"] = sha256(artifact)

    database = {"attempted": False, "current_db_counts_verified": False}
    if env_file is not None:
        from offline_benchmark.data.db_loader import load_snapshot
        from offline_benchmark.data.db_audit import read_table_counts
        from offline_benchmark.common.manifest import build_manifest
        from offline_benchmark.split.chronological_split import chronological_split
        database["attempted"] = True
        database["environment_filename"] = env_file.name
        try:
            counts_before = read_table_counts(env_file)
            snapshot = load_snapshot(env_file)
            live_manifest = build_manifest(snapshot, chronological_split(snapshot.purchases))
            counts_after = read_table_counts(env_file)
            assert counts_before == counts_after
            assert live_manifest == manifest
            database.update({"current_db_counts_verified": True,
                             "manifest_matches_saved": True,
                             "counts_before_after_equal": True,
                             "counts": counts_before})
        except Exception as exc:
            original = getattr(exc, "orig", None)
            args = getattr(original, "args", ())
            database.update({"exception": type(exc).__name__,
                             "driver_code": args[0] if args and isinstance(args[0], int) else None})
    assert protected_files(service) == before
    return {
        "evidence_kind": "saved-artifact verification; live DB status reported separately",
        "split_manifest": manifest, "saved_source_audit": source,
        "saved_model_metric_checks": model_checks,
        "saved_summary_exact": True,
        "canonical_cached_50_50_matches_customers": len(predictions),
        "noncanonical_60_40_probe": {
            "written_to_canonical_outputs": False,
            "changed_ordered_top5_customers": changed,
            "aggregate": evaluate_reports(tuple(alternatives), tuple(summary["catalog_product_ids"]))["aggregate"]},
        "existing_research_proof": {"protected_paths": len(research["protected_file_sha256"]),
                                   "missing_paths": missing, "changed_existing_paths": stale},
        "production_endpoint_parameters": api_arguments,
        "production_imports_offline_models": production_offline_imports,
        "python_sources_compile": True,
        "local_artifact_static_metadata": artifact_metadata,
        "database": database,
        "source_and_artifact_hashes_unchanged_during_verification": True,
        "protected_file_sha256": before,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service-path", type=Path, default=Path.cwd())
    parser.add_argument("--env-file", type=Path)
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    result = verify(args.service_path.resolve(), args.env_file.resolve() if args.env_file else None)
    print(json.dumps(result, indent=2, ensure_ascii=False, allow_nan=False))
    if args.env_file and not result["database"]["current_db_counts_verified"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
