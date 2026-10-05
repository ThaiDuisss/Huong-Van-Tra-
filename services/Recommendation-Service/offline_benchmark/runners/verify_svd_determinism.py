import argparse
import json
from pathlib import Path
import sys

from offline_benchmark.common.filtering import top_five
from offline_benchmark.common.manifest import artifact_path, build_manifest, digest, require_manifest
from offline_benchmark.common.types import TrainingInput
from offline_benchmark.data.db_loader import load_snapshot
from offline_benchmark.models.svd import SVD
from offline_benchmark.split.chronological_split import chronological_split


def main() -> None:
    parser = argparse.ArgumentParser(description="Repeated identical TRAIN SVD fits; compare exact ordered Top-5")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--fixture", action="store_true", help="Verify the previously failing deterministic test fixture")
    parser.add_argument("--repeats", type=int, default=100)
    parser.add_argument("--customer-id", help="Printed example; all eligible customers are verified on every fit")
    args = parser.parse_args()
    try:
        if args.repeats < 100:
            raise ValueError("Verification requires at least 100 repeated fits")
        if args.fixture:
            from offline_benchmark.tests.test_benchmark import fixture
            snapshot = fixture()
        else:
            snapshot = load_snapshot(args.env_file)
        splits = chronological_split(snapshot.purchases)
        manifest = build_manifest(snapshot, splits)
        if not args.fixture:
            require_manifest(manifest)
        training = TrainingInput(tuple(s.customer_id for s in splits), snapshot.products,
                                 tuple(e for s in splits for e in s.train))
        customer = args.customer_id or training.customer_ids[-1]
        if customer not in training.customer_ids:
            raise ValueError("Selected customer is outside the eligible cohort")
        fingerprints = set()
        sample = None
        for iteration in range(args.repeats):
            model = SVD(training)
            rankings = {user: top_five(model.scores(user)) for user in training.customer_ids}
            fingerprints.add(digest(rankings))
            if len(fingerprints) > 1:
                raise ValueError("Repeated SVD fits produced different ordered Top-5 results")
            sample = rankings[customer]
            if not args.fixture and (iteration + 1) % 10 == 0:
                print(f"Verified SVD fits: {iteration + 1}/{args.repeats}", flush=True)
        if not args.fixture:
            final = load_snapshot(args.env_file)
            if build_manifest(final, chronological_split(final.purchases)) != manifest:
                raise ValueError("Shared DB snapshot changed during SVD verification")
        proof = {"repeated_svd_fits": args.repeats, "verified_customers": len(training.customer_ids),
                 "unique_ordered_top5_results_per_customer": 1, "customer_id": customer,
                 "top5": list(sample), "all_customer_rankings_sha256": next(iter(fingerprints)),
                 "split": manifest, "source": "test fixture" if args.fixture else "shared DB"}
        name = "svd_fixture_determinism.json" if args.fixture else "svd_determinism.json"
        artifact_path(name).write_text(json.dumps(proof, indent=2), encoding="utf-8")
        print(f"Repeated SVD fits: {args.repeats}")
        print("Unique ordered Top-5 results per customer: 1")
        print(f"Verified customers on every fit: {len(training.customer_ids)}")
        print(f"Customer: {customer}\nTop-5:\n{json.dumps(sample)}")
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check shared DB access/schema and dependencies"
        print(f"SVD verification stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
