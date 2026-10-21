"""Causal offline adapters for the unmodified V1 serving mathematics.

Catalog features are retrospective, fixed for a run. No production NPZ is read.
Global orders become available only after their LAST line timestamp, strictly
before prediction time. One immutable CF state is built per distinct cutoff;
TF-IDF features are built once, using the production builder.
"""
from collections import Counter
from dataclasses import dataclass, replace
from datetime import timezone
import hashlib

import numpy as np
from scipy.sparse import csr_matrix

from app.recommendation.v1_model import (
    CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT, V1ModelError,
    build_artifact, fingerprint, hybrid_scores, normalize_scores,
)
from offline_benchmark.split.prefix_history import customer_orders, verify_cases


MODELS = ("Popularity", "Content", "CF", "Hybrid50", "Hybrid60")
MODES = ("repeat", "discovery")
CONTRACT = "exact-v1-tfidf-whole-order-event-time-v1"


@dataclass(frozen=True, slots=True)
class Ranking:
    top5: tuple[str, ...]
    source: str
    fallback_reason: str | None
    candidate_count: int
    content_flat: bool
    cf_flat: bool


def rank(scores):
    return tuple(sorted(scores, key=lambda p: (-scores[p], p))[:5])


def rankings(artifact, history, candidates):
    """Normalize AFTER exclusion. Single flat components retain fixed weights.

Content/CF-only use Popularity when their own component is flat; hybrids use
Popularity only when BOTH are flat, exactly as serving. Empty candidates never
trigger fallback. Model-unavailable is recoverable; training/cutoff errors are not.
"""
    index = dict(zip(artifact.product_ids, artifact.popularity))
    popularity = {p: float(index[p]) for p in candidates}
    output = {"Popularity": Ranking(rank(popularity), "POPULARITY", None,
                                    len(candidates), False, False)}
    try:
        content, cf = artifact.components(history)
        left = normalize_scores({p: content.get(p, 0.0) for p in candidates})
        right = normalize_scores({p: cf.get(p, 0.0) for p in candidates})
        content_flat, cf_flat = not any(left.values()), not any(right.values())
        sixty, both_flat = hybrid_scores(content, cf, candidates)
        scores = {"Content": (left, content_flat, "CONTENT_FLAT"),
                  "CF": (right, cf_flat, "CF_FLAT"),
                  "Hybrid50": ({p: .5*left[p]+.5*right[p] for p in candidates}, both_flat, "BOTH_COMPONENTS_FLAT"),
                  "Hybrid60": (sixty, both_flat, "BOTH_COMPONENTS_FLAT")}
        for model, (values, flat, reason) in scores.items():
            fallback = bool(candidates) and flat
            output[model] = Ranking(rank(popularity if fallback else values),
                "FALLBACK" if fallback else model, reason if fallback else None,
                len(candidates), content_flat, cf_flat)
        output["Popularity"] = replace(output["Popularity"], content_flat=content_flat, cf_flat=cf_flat)
    except V1ModelError:
        for model in MODELS[1:]:
            output[model] = Ranking(rank(popularity), "FALLBACK", "V1_MODEL_NOT_AVAILABLE",
                                    len(candidates), False, False)
    return output


class CausalTfidf:
    def __init__(self, products, purchases):
        self.template = build_artifact(products, ())
        # customer_orders validates identifiers, duplicate events and order ownership.
        grouped = customer_orders(purchases)
        self.orders = tuple(sorted(((max(e.event_time for e in o.events), customer, o)
                                    for customer, orders in grouped.items() for o in orders),
                                   key=lambda row: (row[0], row[1], row[2].order_id)))
        self.cursor = 0
        self.counts = Counter()
        self.customer_order_ids = {}
        self.chain = hashlib.sha256()
        self.cutoff = None
        self.cached = None
        self.state_key = None
        self.builds = 0

    def advance(self, cutoff):
        if self.cutoff is not None and cutoff < self.cutoff:
            raise ValueError("Prediction cutoffs must be chronological")
        if cutoff == self.cutoff:
            return self.cached
        while self.cursor < len(self.orders) and self.orders[self.cursor][0] < cutoff:
            available, customer, order = self.orders[self.cursor]
            self.customer_order_ids.setdefault(customer, set()).add(order.order_id)
            for event in order.events:
                self.counts[(customer, event.product_id)] += 1
                self.chain.update(fingerprint((event.event_id, customer, event.product_id,
                    event.order_id, event.event_time.isoformat(), event.event_type)).encode())
            self.cursor += 1
        ids = self.template.product_ids
        index = {p: i for i, p in enumerate(ids)}
        counts = tuple((u, p, n) for (u, p), n in sorted(self.counts.items()) if p in index)
        users = {u: i for i, u in enumerate(sorted({u for u, _, _ in counts}))}
        coordinates = [(users[u], index[p], float(n)) for u, p, n in counts]
        rows, cols, values = zip(*coordinates) if coordinates else ((), (), ())
        # Same sparse operations and ordering as production build_artifact.
        matrix = csr_matrix((values, (rows, cols)), shape=(len(users), len(ids)), dtype=float)
        norms = np.sqrt(np.asarray(matrix.power(2).sum(axis=0)).ravel())
        inverse = np.divide(1, norms, out=np.zeros_like(norms), where=norms != 0)
        normalized = matrix.multiply(inverse)
        similarity = (normalized.T @ normalized).tocsr()
        similarity.setdiag(0)
        similarity.eliminate_zeros()
        artifact = replace(self.template, similarity=similarity,
            popularity=np.asarray(matrix.sum(axis=0)).ravel(), history_sha256=fingerprint(counts),
            created_at=cutoff.replace(tzinfo=timezone.utc) if cutoff.tzinfo is None else cutoff)
        artifact.validate()
        for sparse in (artifact.content, artifact.similarity):
            for array in (sparse.data, sparse.indices, sparse.indptr):
                array.flags.writeable = False
        artifact.idf.flags.writeable = artifact.popularity.flags.writeable = False
        self.state_key = fingerprint((CONTRACT, CONTRACT_VERSION, FEATURE_CONTRACT,
            NORMALIZATION_CONTRACT, artifact.catalog_sha256, artifact.history_sha256,
            self.chain.hexdigest(), cutoff.isoformat(), self.cursor))
        self.cutoff, self.cached = cutoff, artifact
        self.builds += 1
        return artifact


def predict(products, purchases, cases, progress=None):
    verify_cases(cases, purchases)
    state = CausalTfidf(products, purchases)
    results = {mode: {model: [] for model in MODELS} for mode in MODES}
    evidence = []
    for i, case in enumerate(cases):
        artifact = state.advance(case.prediction_time)
        expected = {o.order_id for o in case.history}
        if state.customer_order_ids.get(case.customer_id, set()) != expected:
            raise ValueError("Available whole orders differ from exact local prefix")
        history = Counter(e.product_id for e in case.events)
        for p in set(history) | {p for u, p in state.counts if u == case.customer_id}:
            if state.counts[(case.customer_id, p)] != history[p]:
                raise ValueError("Local counts differ from causal global counts")
        if any(e.event_time >= case.prediction_time for e in case.events):
            raise ValueError("Incomplete local order at prediction cutoff")
        relevant = tuple(sorted({e.product_id for e in case.target.events}))
        for mode in MODES:
            candidates = tuple(p for p in artifact.product_ids if mode == "repeat" or p not in history)
            scores = rankings(artifact, history, candidates)
            for model, result in scores.items():
                # Relevant labels are accessed only after the entire ranking is fixed.
                results[mode][model].append((case, relevant, result))
        evidence.append({"case_id": case.case_id, "cutoff": case.prediction_time.isoformat(),
            "history_orders": case.history_order_count, "available_global_orders": state.cursor,
            "latest_global_order_available_at": state.orders[state.cursor-1][0].isoformat() if state.cursor else None,
            "state_sha256": state.state_key, "history_counts_sha256": artifact.history_sha256})
        if progress and (i % 500 == 0 or i == len(cases)-1):
            progress(i+1, len(cases), state.builds)
    return results, evidence, state.builds
