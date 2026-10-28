"""Internal deployment packaging tool; no public Excel endpoint and no model fitting."""
import argparse
from datetime import datetime
from pathlib import Path
import sys

SERVICE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE))


def main():
    from offline_benchmark.evaluation.bundle import prepare_bundle, sha256
    from offline_benchmark.evaluation.output import LOCAL, report_helper
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--run-directory", type=Path, required=True)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--snapshot-sha256", required=True)
    parser.add_argument("--run-manifest-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    raw = prepare_bundle(args.run_directory, args.snapshot, args.snapshot_sha256, args.run_manifest_sha256)
    path = report_helper().verify_destination(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(raw)
    print("Bundle: " + str(path.resolve()))
    print("SHA-256: " + sha256(raw))
    print("Created: " + datetime.now(LOCAL).isoformat())


if __name__ == "__main__":
    raise SystemExit(main())
