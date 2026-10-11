"""Causal event sweep using the existing offline cosine/count scoring conventions."""
from collections import Counter, defaultdict
from dataclasses import replace
from math import isclose, isfinite

import numpy as np
from scipy.sparse import csr_matrix

from offline_benchmark.common.filtering import top_five
from offline_benchmark.common.manifest import digest
from offline_benchmark.common.types import TrainingInput
from offline_benchmark.models.base import normalize_scores
from offline_benchmark.models.content_based import ContentBased
from offline_benchmark.models.item_item_cf import ItemItemCF
from offline_benchmark.models.popularity import Popularity


CONFIGURATIONS = (
    ("Popularity", "Popularity", (1.0, 0.0, 0.0)),
    ("Content", "Content", (0.0, 1.0, 0.0)),
    ("CF", "CF", (0.0, 0.0, 1.0)),
    ("A_25_75", "A", (0.0, 0.25, 0.75)),
    ("A_50_50", "A", (0.0, 0.50, 0.50)),
    ("A_75_25", "A", (0.0, 0.75, 0.25)),
    ("B_10_45_45", "B", (0.10, 0.45, 0.45)),
    ("B_20_40_40", "B", (0.20, 0.40, 0.40)),
    ("B_20_50_30", "B", (0.20, 0.50, 0.30)),
    ("B_30_40_30", "B", (0.30, 0.40, 0.30)),
    ("B_40_40_20", "B", (0.40, 0.40, 0.20)),
)
MODES = ("exclude_purchased_false", "exclude_purchased_true")


class CausalState:
    """Consumes observed events only. No target or future row is stored by the model."""

    def __init__(self, products, customers):
        self.products = products
        self.content = ContentBased(TrainingInput((), products, ()))
        self.ids = self.content.product_ids
        self.index = self.content.product_index
        self.customers = tuple(sorted(customers))
        self.users = {customer: i for i, customer in enumerate(self.customers)}
        self.matrix = np.zeros((len(self.users), len(self.ids)), dtype=float)
        self.counts = Counter()
        self.orders = defaultdict(set)
        self.event_count = 0
        self.latest_time = None

    def observe(self, event):
        if event.event_type != "PURCHASE":
            raise ValueError("Causal model accepts PURCHASE events only")
        self.counts[event.product_id] += 1
        self.orders[event.customer_id].add(event.order_id)
        if event.product_id in self.index:
            self.matrix[self.users[event.customer_id], self.index[event.product_id]] += 1
        self.event_count += 1
        self.latest_time = event.event_time

    def similarity(self):
        # Exact same sparse arithmetic as ItemItemCF.__init__, without rebuilding
        # Python event Counters for every cutoff. Refit once per distinct timestamp.
        matrix = csr_matrix(self.matrix)
        norm = np.sqrt(np.asarray(matrix.power(2).sum(axis=0)).ravel())
        inverse = np.divide(1.0, norm, out=np.zeros_like(norm), where=norm != 0)
        normalized = matrix.multiply(inverse)
        similarity = (normalized.T @ normalized).tocsr()
        similarity.setdiag(0)
        similarity.eliminate_zeros()
        return similarity

    def components(self, case, similarity):
        if self.latest_time is not None and self.latest_time >= case.prediction_time:
            raise ValueError("Global training event reaches target timestamp")
        if self.orders[case.customer_id] != {order.order_id for order in case.history}:
            raise ValueError("Global customer orders differ from exact prefix")
        counts = Counter(event.product_id for event in case.events)
        history = np.array([counts.get(product, 0) for product in self.ids], dtype=float)
        if not np.array_equal(history, self.matrix[self.users[case.customer_id]]):
            raise ValueError("Local prefix differs from causally observed customer history")
        return (
            {product: float(self.counts[product]) for product in self.ids},
            self.content.profile_scores(history),
            self.content.score_map(np.asarray(history @ similarity).ravel()),
        )


def score_configuration(weights, components):
    if (any(not isfinite(w) or w < 0 for w in weights)
            or not isclose(sum(weights), 1.0, abs_tol=1e-12)):
        raise ValueError("Invalid component weights")
    pop, content, cf = components
    wp, wc, wf = weights
    if wp == 0:
        return {p: wc * content[p] + wf * cf[p] for p in pop}
    return {p: wp * pop[p] + wc * content[p] + wf * cf[p] for p in pop}


def component_signal(scores, candidates):
    values = [scores[product] for product in candidates]
    positive = sum(value > 0 for value in values)
    return {"positive_candidates": positive, "positive_signal": positive > 0,
            "flat_scores": not values or max(values) == min(values)}


def predict_cases(snapshot, cases, progress=None, verify_reference=False):
    events = tuple(sorted((replace(e, customer_id=e.customer_id.strip()) for e in snapshot.purchases
                           if e.customer_id and e.customer_id.strip()),
                          key=lambda e: (e.event_time, e.event_id)))
    customers = {event.customer_id for event in events}
    state = CausalState(snapshot.products, customers)
    predictions = {mode: {key: {} for key, _, _ in CONFIGURATIONS} for mode in MODES}
    diagnostics = {}
    cursor, cutoff, similarity = 0, None, None
    checked_groups = set()
    references = []
    for i, case in enumerate(cases):
        if cutoff is not None and case.prediction_time < cutoff:
            raise ValueError("Cases must be chronological")
        if case.prediction_time != cutoff:
            while cursor < len(events) and events[cursor].event_time < case.prediction_time:
                state.observe(events[cursor])
                cursor += 1
            similarity = state.similarity()
            cutoff = case.prediction_time
        raw = state.components(case, similarity)
        normalized = tuple(normalize_scores(scores) for scores in raw)
        purchased = {event.product_id for event in case.events}
        eligible = {mode: tuple(p for p in state.ids if mode == MODES[0] or p not in purchased)
                    for mode in MODES}
        for key, _, weights in CONFIGURATIONS:
            scores = score_configuration(weights, normalized)
            for mode in MODES:
                predictions[mode][key][case.case_id] = top_five({p: scores[p] for p in eligible[mode]})
        diagnostics[case.case_id] = {
            "global_training_events": cursor,
            "latest_global_training_event_time": state.latest_time.isoformat() if state.latest_time else None,
            "local_history_purchase_events": len(case.events),
            "target_order_excluded_from_training": True,
            "raw_components_sha256": digest(raw),
            "modes": {mode: {"candidate_count": len(eligible[mode]),
                             "cf": component_signal(raw[2], eligible[mode]),
                             "content": component_signal(raw[1], eligible[mode])}
                      for mode in MODES},
        }
        if verify_reference and (case.group not in checked_groups or i in (0, len(cases)-1)):
            training = TrainingInput(state.customers, snapshot.products, events[:cursor])
            reference = (Popularity(training).scores(case.customer_id),
                         ContentBased(training).scores(case.customer_id),
                         ItemItemCF(training).scores(case.customer_id))
            if raw != reference:
                raise ValueError("Causal sweep differs from existing offline component scores")
            references.append({"case_id": case.case_id, "history_group": case.group,
                               "training_events": cursor, "exact_scores_equal": True})
            checked_groups.add(case.group)
        if progress and (i % 500 == 0 or i == len(cases)-1):
            progress(i+1, len(cases), cursor)
    return predictions, diagnostics, references
