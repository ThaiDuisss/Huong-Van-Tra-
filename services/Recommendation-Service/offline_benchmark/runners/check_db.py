import argparse
import json
from pathlib import Path
import sys

from offline_benchmark.common.manifest import artifact_path
from offline_benchmark.data.db_audit import read_table_counts


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify shared DB connectivity and unchanged table counts (read-only)")
    parser.add_argument("--env-file", type=Path)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--record", metavar="FILENAME", help="Record counts inside offline_benchmark/outputs")
    actions.add_argument("--compare", metavar="FILENAME", help="Compare counts with a recorded outputs file")
    args = parser.parse_args()
    try:
        counts = read_table_counts(args.env_file)
        if args.record:
            if args.record in {"split_manifest.json", "benchmark_summary.json", "evaluation_db_audit.json"} or \
                    args.record.endswith("_metrics.json"):
                raise ValueError("A row-count record cannot overwrite benchmark outputs")
            from offline_benchmark.metrics.evaluator import MODEL_NAMES
            if args.record in {f"{key}.json" for key in MODEL_NAMES}:
                raise ValueError("A row-count record cannot overwrite prediction outputs")
            artifact_path(args.record).write_text(json.dumps(counts, indent=2), encoding="utf-8")
        if args.compare:
            previous = json.loads(artifact_path(args.compare).read_text(encoding="utf-8"))
            if previous != counts:
                raise ValueError("Shared table counts changed; investigate concurrent DB activity before accepting results")
        print("DB connectivity: PASS (read-only)")
        print(json.dumps(counts, indent=2))
        if args.compare:
            print("Shared table counts unchanged: PASS")
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check DB access/configuration"
        print(f"DB check stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
