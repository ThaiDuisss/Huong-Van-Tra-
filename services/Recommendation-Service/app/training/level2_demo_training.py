"""Operator-triggered jobs for the isolated Level 2 feature and model contract."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
from hashlib import sha256
import json
import os
from pathlib import Path
import sqlite3
import sys
from uuid import UUID, uuid4

import numpy as np

from app.recommendation.level2_demo_features import adapt
from app.recommendation.level2_demo_registry import APPROVAL, APPROVED, BASELINE_SHA256, checked_document, validate_model
from app.recommendation.v1_artifact import V1ArtifactStore
from app.service.level2_demo_serving import PrefixSnapshot
from app.repositories.explore_history_repository import ExploreHistoryError
from app.training.logistic_fit import fit_logistic_parameters
from app.training.taste_ranking_dataset import DatasetBlocked, build_dataset
from app.training.train_taste_ranking import load_training_evidence
from offline_benchmark.metrics.ranking_metrics import ranking_metrics

EXPLORE_WEIGHTS = ((.70, .30, 0., 0.), (.45, .45, .10, 0.), (.35, .35, .30, 0.), (.25, .35, .40, 0.))


def now():
    return datetime.now(timezone.utc).isoformat()


class Jobs:
    """An isolated local status ledger. Its unique index serializes submissions across workers."""

    def __init__(self, root: Path):
        if not root.is_absolute() or not root.is_dir() or root.resolve().name != "l204-isolated-training":
            raise ValueError("ISOLATED_TRAINING_DIRECTORY_REQUIRED")
        self.root = root.resolve()
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("""CREATE TABLE IF NOT EXISTS jobs
                (id TEXT PRIMARY KEY, mode TEXT NOT NULL, status TEXT NOT NULL, started_at TEXT NOT NULL,
                 finished_at TEXT, model_version TEXT, error TEXT)""")
            db.execute("""CREATE UNIQUE INDEX IF NOT EXISTS one_running_job
                ON jobs(status) WHERE status = 'RUNNING'""")
            columns = {c["name"] for c in db.execute("PRAGMA table_info(jobs)")}
            if "worker_pid" not in columns:
                db.execute("ALTER TABLE jobs ADD COLUMN worker_pid INTEGER")

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.root / "jobs.sqlite3", timeout=5)
        connection.row_factory = sqlite3.Row
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    @contextmanager
    def worker_lock(self):
        lock = sqlite3.connect(self.root / "worker-owner.sqlite3", timeout=0, isolation_level=None)
        try:
            try:
                lock.execute("BEGIN IMMEDIATE")
            except sqlite3.OperationalError as failure:
                if failure.sqlite_errorcode != sqlite3.SQLITE_BUSY:
                    raise
                yield False
            else:
                yield True
        finally:
            # The OS releases the SQLite lock even if the owning worker crashes.
            lock.close()

    def recover(self):
        with self.worker_lock() as acquired:
            if not acquired:
                return
            with self.connect() as db:
                db.execute("BEGIN IMMEDIATE")
                current = db.execute("SELECT id, worker_pid, started_at FROM jobs WHERE status='RUNNING'").fetchone()
                if not current:
                    return
                elapsed = (datetime.now(timezone.utc) - datetime.fromisoformat(current["started_at"])).total_seconds()
                reason = None
                if current["worker_pid"] is not None:
                    reason = "ISOLATED_TRAINING_WORKER_LOST"
                elif elapsed >= 120:
                    reason = "ISOLATED_TRAINING_WORKER_NOT_STARTED"
                if reason:
                    db.execute("UPDATE jobs SET status='FAILED', finished_at=?, error=? WHERE id=?",
                               (now(), reason, current["id"]))

    def reserve(self, mode):
        self.recover()
        job = str(uuid4())
        try:
            with self.connect() as db:
                db.execute("INSERT INTO jobs (id, mode, status, started_at) VALUES (?, ?, 'RUNNING', ?)",
                           (job, mode, now()))
        except sqlite3.IntegrityError:
            raise ValueError("ISOLATED_TRAINING_ALREADY_RUNNING") from None
        return job

    def state(self, job):
        with self.connect() as db:
            row = db.execute("SELECT id, mode, status, started_at, finished_at, model_version, error "
                             "FROM jobs WHERE id = ?", (str(UUID(job)),)).fetchone()
        return dict(row) if row else None

    def latest(self, mode):
        with self.connect() as db:
            row = db.execute("SELECT id FROM jobs WHERE mode=? ORDER BY started_at DESC LIMIT 1", (mode,)).fetchone()
        return self.state(row["id"]) if row else None

    def claim(self, job):
        with self.connect() as db:
            result = db.execute("UPDATE jobs SET worker_pid=? WHERE id=? AND status='RUNNING' AND worker_pid IS NULL",
                                (os.getpid(), job))
            return result.rowcount == 1

    def complete(self, job, *, version=None, error=None):
        with self.connect() as db:
            db.execute("""UPDATE jobs SET status=?, finished_at=?, model_version=?, error=?
                          WHERE id=? AND status='RUNNING'""",
                       ("FAILED" if error else "SUCCEEDED", now(), version, error, job))


def train(mode, config, root, job):
    if config.get("approval") != APPROVAL or config.get("provenance") != "SIMULATED_DEMO":
        raise DatasetBlocked("ISOLATED_TRAINING_APPROVAL_REQUIRED")
    source = config["training_datasets"][mode]
    stem, digest, family, _, horizon = APPROVED[mode]
    if source["horizon"] != horizon:
        raise DatasetBlocked("ISOLATED_TRAINING_HORIZON_MISMATCH")
    evidence = load_training_evidence(Path(source["evidence"]), source["sha256"], Path(source["packets"]),
                                      simulated_demo=True)
    if any((w.outcome_end - w.cutoff).total_seconds() != horizon * 86400 for w in evidence.windows):
        raise DatasetBlocked("ISOLATED_TRAINING_HORIZON_MISMATCH")
    dataset = build_dataset(evidence, splits=("TRAIN", "VALIDATION"))
    if dataset.fingerprint != source["fingerprint"]:
        raise DatasetBlocked("ISOLATED_TRAINING_FINGERPRINT_MISMATCH")

    reference = checked_document(Path(config["registry"]) / f"{stem}.json", digest)
    baseline = checked_document(Path(config["baseline"]), BASELINE_SHA256)
    queries = [q for q in dataset.queries if q.mode == mode and q.eligible and q.samples]
    train_queries = [q for q in queries if q.split == "TRAIN"]
    positives = [sum(row.label for row in q.samples) for q in train_queries]
    evaluable = sum(n > 0 for n in positives)
    informative = sum(0 < n < len(q.samples) for n, q in zip(positives, train_queries, strict=True))
    if evaluable < 150 or informative < 100:
        raise DatasetBlocked("INSUFFICIENT_TRAINING_OUTCOMES")

    validation, components = [], []
    matrices, labels = [], []
    for index, window in enumerate(evidence.windows):
        if window.split == "TEST":
            continue
        snapshot = PrefixSnapshot(evidence, window, baseline)
        model = validate_model(reference, mode, snapshot.attributes, snapshot.taxonomy_hash, snapshot.catalog_hash)
        if family == "HYBRID" and window.split == "TRAIN":
            file = root / f"{job}-component-{index}.npz"
            rebuilt = snapshot.component_model
            V1ArtifactStore(file).save(rebuilt)
            components.append({
                "file": file.name, "sha256": sha256(file.read_bytes()).hexdigest(),
                "cutoff": window.cutoff.isoformat(), "purchase_fingerprint": rebuilt.history_sha256,
            })
        for q in (q for q in queries if q.cutoff == window.cutoff):
            if family == "HYBRID" and q.split == "TRAIN":
                continue
            online = snapshot.query(q.customer_id, mode, snapshot.history(q.customer_id))
            if tuple(r.product_id for r in online.rows) != tuple(s.features.product_id for s in q.samples):
                raise DatasetBlocked("TRAINING_SERVING_CANDIDATE_MISMATCH")
            matrix = adapt(mode, online.rows, online.context, snapshot.attributes, model.categories)
            if matrix.names != model.schema_names:
                raise DatasetBlocked("TRAINING_SERVING_SCHEMA_MISMATCH")
            if q.split == "VALIDATION":
                relevant = tuple(s.features.product_id for s in q.samples if s.label)
                if relevant:
                    validation.append((tuple(r.product_id for r in online.rows), matrix, relevant))
            else:
                matrices.append(matrix.values)
                labels.extend(s.label for s in q.samples)
    if len(validation) < 120:
        raise DatasetBlocked("INSUFFICIENT_VALIDATION_OUTCOMES")

    def ndcg(scores):
        values = []
        for ids, matrix, relevant in validation:
            output = scores(matrix)
            if not np.isfinite(output).all():
                raise DatasetBlocked("NONFINITE_VALIDATION_SCORES")
            order = sorted(range(len(ids)), key=lambda i: (-output[i], ids[i]))
            values.append(ranking_metrics(tuple(ids[i] for i in order[:5]), relevant).ndcg_at_5)
        return sum(values) / len(values)

    if family == "LOGISTIC":
        values = np.concatenate(matrices).astype(np.float64)
        compat = model.schema_names.index("mean_compatibility")
        if len(set(values[:, compat])) < 2:
            raise DatasetBlocked("INSUFFICIENT_TRAINING_TASTE_VARIATION")
        centers, scales, fit = fit_logistic_parameters(
            values, np.asarray(labels, dtype=np.float64), reference["fit"]["lambda"], max_iterations=300,
        )
        if not fit.success or not np.isfinite(fit.x).all():
            raise DatasetBlocked("LOGISTIC_FIT_DID_NOT_CONVERGE")
        parameters = dict(centers=centers.tolist(), scales=scales.tolist(),
                          coefficients=fit.x[:-1].tolist(), intercept=float(fit.x[-1]))
        validation_score = ndcg(lambda m: ((m.values - centers) / scales) @ fit.x[:-1] + fit.x[-1])
        fit_metadata = {"lambda": reference["fit"]["lambda"], "iterations": int(fit.nit), "training_rows": len(labels)}
        policy = "FIXED_L2_PENALTY"
    else:
        leader, validation_score = None, float("-inf")
        for weights in EXPLORE_WEIGHTS:
            score = ndcg(lambda m: m.components @ np.asarray(weights))
            if score > validation_score + 1e-12:
                leader, validation_score = weights, score
        parameters = {"weights": leader}
        fit_metadata = {"weights": leader, "components": components, "weight_candidates": len(EXPLORE_WEIGHTS)}
        policy = "TRAIN_COMPONENTS_VALIDATION_BLEND"
    version = f"l204-{stem}-{job}"
    # Staging is separate from the approved registry. Never create an activation entry from this job.
    document = dict(reference, model_version=version, dataset_fingerprint=dataset.fingerprint,
                    parameters=parameters,
                    fit=fit_metadata, job_id=job, created_at=now(), fit_policy=policy)
    data = json.dumps(document, indent=2, allow_nan=False).encode("utf-8")
    output = root / f"{job}.json"
    with output.open("xb") as file:
        file.write(data)
    with (root / f"{job}.sha256").open("x", encoding="ascii") as file:
        file.write(sha256(data).hexdigest() + "\n")
    evaluation = {"job_id": job, "model_version": version, "mode": mode, "horizon_days": horizon,
                  "artifact_sha256": sha256(data).hexdigest(), "dataset_fingerprint": dataset.fingerprint,
                  "provenance": "SIMULATED_DEMO", "scope": "VALIDATION_ONLY",
                  "validation_queries": len(validation), "ndcg_at_5": validation_score,
                  "quality_promotion": "BLOCKED"}
    with (root / f"{job}.evaluation.json").open("x", encoding="utf-8") as file:
        json.dump(evaluation, file, allow_nan=False, indent=2)
    return version


def run_reserved(jobs, job, config):
    with jobs.worker_lock() as acquired:
        if not acquired or not jobs.claim(job):
            return False
        return _run_claimed(jobs, job, config)


def _run_claimed(jobs, job, config):
    state = jobs.state(job)
    if not state or state["status"] != "RUNNING":
        return False
    try:
        version = train(state["mode"], config, jobs.root, job)
    except DatasetBlocked as exc:
        reason = str(exc)
        jobs.complete(job, error=reason if reason.isascii() and reason.replace("_", "").isalnum()
                      else "TRAINING_DATASET_INVALID")
        return False
    except (OSError, ValueError, KeyError, ArithmeticError, ExploreHistoryError):
        jobs.complete(job, error="ISOLATED_TRAINING_FAILED")
        return False
    jobs.complete(job, version=version)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jobs", type=Path, required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    status = commands.add_parser("status")
    status.add_argument("job")
    worker = commands.add_parser("worker")
    worker.add_argument("job")
    worker.add_argument("--manifest", type=Path, required=True)
    worker.add_argument("--sha256", required=True)
    run = commands.add_parser("run")
    run.add_argument("--manifest", type=Path, required=True)
    run.add_argument("--sha256", required=True)
    run.add_argument("--mode", choices=APPROVED, required=True)
    args = parser.parse_args()
    jobs = Jobs(args.jobs)
    if args.command == "status":
        jobs.recover()
        print(json.dumps(jobs.state(args.job)))
        return 0
    if args.command == "worker":
        try:
            config = checked_document(args.manifest, args.sha256)
        except (OSError, ValueError):
            jobs.complete(args.job, error="ISOLATED_TRAINING_MANIFEST_INVALID")
            return 1
        return 0 if run_reserved(jobs, args.job, config) else 1
    config = checked_document(args.manifest, args.sha256)
    job = jobs.reserve(args.mode)
    print(json.dumps(jobs.state(job)), flush=True)
    result = run_reserved(jobs, job, config)
    print(json.dumps(jobs.state(job)), flush=True)
    return 0 if result else 1


if __name__ == "__main__":
    sys.exit(main())
