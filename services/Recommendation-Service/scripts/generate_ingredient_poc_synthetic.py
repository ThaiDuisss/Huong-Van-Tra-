"""Dry-run only: generate/validate/export synthetic artifacts, with no DB connection."""
import argparse
import json
from pathlib import Path
import sys

SERVICE = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20261027)
    parser.add_argument("--dry-run", action="store_true", required=True)
    parser.add_argument("--output-dir", type=Path, help="new external directory, never overwritten")
    args = parser.parse_args()
    from app.ingredient_poc.synthetic import generate, write_artifacts
    from app.ingredient_poc.synthetic_validation import validate
    bundle = generate(args.seed)
    report = validate(bundle)
    hashes = write_artifacts(bundle, args.output_dir) if args.output_dir else None
    print(json.dumps({"status": report["status"], "dataset_id": bundle["dataset"]["id"],
                      "statistics": report["statistics"], "artifact_checksums": hashes}, indent=2))
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(SERVICE))
    raise SystemExit(main())
