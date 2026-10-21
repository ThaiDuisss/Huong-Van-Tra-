"""Export causal Train-only V1 results. No serving APIs, model files or writes to services."""
import argparse
import json
import os
from pathlib import Path
import sys

for key, value in {"SPRING_FLYWAY_ENABLED":"false", "SPRING_SQL_INIT_MODE":"never",
                   "STORAGE_INITIALIZATION_ENABLED":"false", "PYTHONDONTWRITEBYTECODE":"1"}.items():
    os.environ[key] = value
SERVICE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE))


def main(argv=None):
    from offline_benchmark.evaluation.output import BACKUP_ROOT, export_run
    from offline_benchmark.evaluation.quantity import probe_quantity
    from offline_benchmark.evaluation.runner import verify_archive
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--snapshot-sha256", required=True, help="Trusted archive hash, not an automatically accepted input digest")
    parser.add_argument("--manifest", type=Path, default=SERVICE / "offline_benchmark/outputs/split_manifest.json")
    parser.add_argument("--output-root", type=Path, default=BACKUP_ROOT)
    parser.add_argument("--quantity-env-file", type=Path, help="Optional explicit SELECT-only quantity identity probe; UOM remains unverified")
    args = parser.parse_args(argv)
    try:
        dataset = verify_archive(args.snapshot, args.manifest, args.snapshot_sha256)
        quantity = probe_quantity(dataset, args.quantity_env_file) if args.quantity_env_file else None
        # Archive inference/reporting has no permission to contact any service.
        def no_network(event, arguments):
            if event == "socket.connect":
                raise RuntimeError("Network is disabled after the optional read-only quantity probe")
        sys.addaudithook(no_network)
        result = export_run(dataset, args.output_root, quantity)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, RuntimeError) as exc:
        # No driver connection strings or credentials are rendered here.
        print("Offline export stopped: " + (str(exc) if isinstance(exc,(OSError,ValueError)) else type(exc).__name__), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
