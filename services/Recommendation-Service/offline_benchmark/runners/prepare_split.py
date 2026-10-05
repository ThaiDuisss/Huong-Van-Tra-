import json
import sys

from offline_benchmark.common.manifest import artifact_path, build_manifest, MANIFEST_PATH, OUTPUTS
from offline_benchmark.data.db_loader import load_snapshot
from offline_benchmark.runners.common import arguments
from offline_benchmark.split.chronological_split import chronological_split


def invalidate_outputs() -> list[str]:
    """Remove generated results and custom predictions, retaining the historical baseline."""
    from offline_benchmark.runners.common import MODELS

    names = {f"{key}{suffix}.json" for key in MODELS for suffix in ("", "_metrics")}
    names.update({"benchmark_summary.json", "BENCHMARK_SUMMARY.md", "RUNTIME_PROOF.md",
                  "evaluation_db_audit.json", "svd_determinism.json", "svd_fixture_determinism.json",
                  "svd_output_regeneration_audit.json", "verification_summary.json",
                  "order_split_verification.json", "ORDER_SPLIT_PROOF.md", "order_comparison.json",
                  "OLD_VS_NEW.md"})
    # --output may name a custom report. Recognize its saved prediction contract.
    for path in OUTPUTS.glob("*.json"):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, UnicodeError):
            continue
        if isinstance(payload, dict) and "predictions" in payload and "split" in payload:
            names.add(path.name)
    removed = []
    for name in sorted(names):
        path = artifact_path(name)
        if path.is_file():
            path.unlink()
            removed.append(name)
    return removed


def main() -> None:
    args = arguments("Prepare one common chronological PURCHASE split", prepare=True)
    try:
        snapshot = load_snapshot(args.env_file)
        splits = chronological_split(snapshot.purchases)
        if not splits or not snapshot.products:
            raise ValueError("Shared DB has no eligible customers or active products")
        manifest = build_manifest(snapshot, splits)
        if MANIFEST_PATH.is_file():
            existing = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
            if existing != manifest and not args.refresh:
                raise ValueError("Manifest differs; use --refresh to invalidate previous benchmark results")
            if existing != manifest:
                removed = invalidate_outputs()
                artifact_path("split_refresh_audit.json").write_text(json.dumps({
                    "previous_split_sha256": existing["split_sha256"],
                    "current_split_sha256": manifest["split_sha256"],
                    "invalidated_files": removed,
                }, indent=2), encoding="utf-8")
        artifact_path("split_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
        print(json.dumps(manifest, indent=2))
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check shared DB access/schema and dependencies"
        print(f"Benchmark stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
