"""Causal canonical-TRAIN projections; reuse V1 without DB, NPZ or shared cache."""
from collections import Counter
from dataclasses import asdict, dataclass
from types import MappingProxyType

from app.recommendation.v1_model import PurchaseCount, build_artifact, catalog_fingerprint
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory
from app.service.recommendation_service import RecommendationService, MIN_HYBRID_PURCHASE_ORDERS
from offline_benchmark.evaluation.contract import (
    CATALOG_POLICY, K, Opportunity, Prediction, PROTOCOL, RankedItem, digest,
)
from offline_benchmark.split.chronological_split import ordered_orders


def build_opportunities(splits):
    opportunities = []
    for split in splits:
        orders = ordered_orders(split.train)
        if not orders:
            raise ValueError("No canonical TRAIN history for opportunity")
        cutoff = max(e.event_time for e in orders[-1].events)
        if any(e.event_time > cutoff for e in split.train):
            raise ValueError("Earlier TRAIN order spans the last TRAIN completion")
        if split.test and cutoff >= min(e.event_time for e in split.test):
            raise ValueError("Ambiguous TRAIN/TEST timestamp boundary; no prediction generated")
        opportunities.append(Opportunity(split.customer_id, cutoff, split.train))
    if len({o.customer for o in opportunities}) != len(opportunities):
        raise ValueError("Duplicate or colliding customer pseudonyms")
    return tuple(opportunities)


@dataclass(frozen=True, slots=True)
class CanonicalTrain:
    orders: tuple
    event_ids: frozenset[str]

    @classmethod
    def from_opportunities(cls, opportunities):
        events = tuple(e for o in opportunities for e in o.train)
        ids = frozenset(e.event_id for e in events)
        if len(ids) != len(events):
            raise ValueError("Canonical TRAIN repeats an event")
        return cls(ordered_orders(events), ids)

    def at(self, cutoff):
        included, future, spanning = [], 0, 0
        for order in self.orders:
            completed = max(e.event_time for e in order.events)
            if completed <= cutoff:
                included.append(order)
            else:
                future += 1
                spanning += order.order_time <= cutoff
        return tuple(included), future, spanning


class MemoryRepository:
    def __init__(self, products, orders):
        self.products = tuple(products)
        counts, orders_by_user = {}, Counter()
        popularity = Counter()
        for order in orders:
            owner = order.events[0].customer_id
            if any(e.customer_id != owner for e in order.events):
                raise ValueError("TRAIN order has multiple customers")
            orders_by_user[owner] += 1
            for event in order.events:
                counts.setdefault(owner, Counter())[event.product_id] += 1
                popularity[event.product_id] += 1
        self.histories = MappingProxyType({
            user: (orders_by_user[user], MappingProxyType(dict(values)))
            for user, values in counts.items()
        })
        self.counts = MappingProxyType(dict(popularity))

    def customer_history(self, db, customer_id):
        self._no_database(db)
        count, values = self.histories.get(customer_id, (0, {}))
        return CustomerPurchaseHistory(count, dict(values))

    def active_catalog(self, db):
        self._no_database(db)
        return self.products

    def popularity(self, db, purchased, limit, eligible_ids=None):
        self._no_database(db)
        candidates = (p for p in self.products if p.product_id not in purchased
                      and (eligible_ids is None or p.product_id in eligible_ids))
        ranked = sorted(candidates, key=lambda p: (-self.counts.get(p.product_id, 0), p.product_id))
        return [(p.product_id, p.product_code, p.name, self.counts.get(p.product_id, 0))
                for p in ranked[:limit]]

    @staticmethod
    def _no_database(db):
        if db is not None:
            raise ValueError("Offline inference does not accept a database session")


class MemoryStore:
    def __init__(self, artifact):
        self.artifact = artifact

    def load(self):
        return self.artifact


@dataclass(frozen=True, slots=True)
class TrainState:
    repository: MemoryRepository
    store: MemoryStore
    sha256: str
    events_sha256: str
    event_count: int
    order_count: int
    future_orders: int
    spanning_orders: int

    def service(self):
        return RecommendationService(self.store, self.repository)


def build_state(training: CanonicalTrain, products, cutoff):
    products = tuple(sorted(products, key=lambda p: p.product_id))
    if not products:
        raise ValueError("Offline state requires a frozen catalog")
    orders, future, spanning = training.at(cutoff)
    events = tuple(e for order in orders for e in order.events)
    if (not {e.event_id for e in events} <= training.event_ids
            or any(e.event_time > cutoff for e in events)):
        raise ValueError("State contains non-TRAIN or future events")
    pair_counts = Counter((e.customer_id, e.product_id) for e in events)
    artifact = build_artifact(products, tuple(
        PurchaseCount(u, p, n) for (u, p), n in sorted(pair_counts.items())))
    # Never replace created_at with a historical cutoff. It is the actual build clock.
    for matrix in (artifact.content, artifact.similarity):
        for array in (matrix.data, matrix.indices, matrix.indptr):
            array.flags.writeable = False
    artifact.idf.flags.writeable = False
    artifact.popularity.flags.writeable = False
    event_hash = digest([asdict(e) | {"event_time": e.event_time.isoformat()}
                         for e in sorted(events, key=lambda e: e.event_id)])
    state_hash = digest({"protocol": PROTOCOL, "cutoff": cutoff.isoformat(),
                         "events": event_hash, "catalog": catalog_fingerprint(products),
                         "catalog_policy": CATALOG_POLICY,
                         "model_history": artifact.history_sha256,
                         "routing_threshold": MIN_HYBRID_PURCHASE_ORDERS})
    return TrainState(MemoryRepository(products, orders), MemoryStore(artifact), state_hash,
                      event_hash, len(events), len(orders), future, spanning)


def predict(opportunity, state):
    response = state.service().recommend(None, opportunity.customer_id, K,
                                         exclude_purchased=False, mode=None)
    own_ids = {e.event_id for e in opportunity.train}
    if state.repository.customer_history(None, opportunity.customer_id).distinct_order_count != len(ordered_orders(opportunity.train)):
        raise ValueError("Cutoff state lost part of the customer's TRAIN orders")
    if not own_ids:
        raise ValueError("Opportunity lacks TRAIN membership")
    items = tuple(RankedItem(item.rank, item.product_id, item.score) for item in response.items)
    if len(items) > K or len({i.product_id for i in items}) != len(items):
        raise ValueError("V1 returned invalid Top-5")
    return Prediction(opportunity.reference, opportunity.customer, opportunity.cutoff.isoformat(),
                      items, response.source, response.fallback_reason, state.sha256,
                      state.event_count, state.order_count, state.events_sha256,
                      tuple(p.product_id for p in state.repository.products),
                      state.future_orders, state.spanning_orders)
