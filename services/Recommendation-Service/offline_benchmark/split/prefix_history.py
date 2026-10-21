"""Independent, whole-order next-order cases; no canonical eligibility filter."""
from collections import defaultdict
from dataclasses import dataclass, replace

from offline_benchmark.common.types import PurchaseEvent, PurchaseOrder
from offline_benchmark.split.chronological_split import ordered_orders


GROUPS = ("1", "2", "3", "4", ">=5")


@dataclass(frozen=True, slots=True)
class PrefixCase:
    customer_id: str
    history: tuple[PurchaseOrder, ...]
    target: PurchaseOrder

    @property
    def case_id(self):
        return f"{self.customer_id}/{self.target.order_id}"

    @property
    def history_order_count(self):
        return len(self.history)

    @property
    def group(self):
        return str(len(self.history)) if len(self.history) < 5 else ">=5"

    @property
    def prediction_time(self):
        # Strict event_time < this timestamp represents immediately before target.
        return self.target.order_time

    @property
    def events(self):
        return tuple(event for order in self.history for event in order.events)


def customer_orders(purchases):
    users = defaultdict(list)
    event_ids, owners = set(), {}
    for event in purchases:
        customer = (event.customer_id or "").strip()
        if not customer:
            continue
        ordered_orders((event,))
        if event.event_id in event_ids:
            raise ValueError("Duplicate PURCHASE event ID")
        event_ids.add(event.event_id)
        if owners.setdefault(event.order_id, customer) != customer:
            raise ValueError("Order ID reused across customers")
        users[customer].append(replace(event, customer_id=customer))
    return {customer: ordered_orders(tuple(events)) for customer, events in sorted(users.items())}


def build_prefix_cases(purchases):
    orders = customer_orders(purchases)
    cases = tuple(PrefixCase(customer, rows[:k], rows[k])
                  for customer, rows in orders.items() for k in range(1, len(rows)))
    return tuple(sorted(cases, key=lambda c: (c.prediction_time, c.customer_id,
                                            c.history_order_count, c.target.order_id)))


def verify_cases(cases, purchases):
    orders = customer_orders(purchases)
    seen = set()
    for case in cases:
        rows = orders.get(case.customer_id, ())
        k = case.history_order_count
        if (k < 1 or k >= len(rows) or case.history != rows[:k]
                or case.target != rows[k] or case.case_id in seen):
            raise ValueError("Case must contain exact O1..Ok and only O(k+1), once")
        seen.add(case.case_id)
        if {order.order_id for order in case.history} & {case.target.order_id}:
            raise ValueError("Order crosses history/target boundary")


def causal_cases(cases):
    """Do not silently include timestamp ties/overlapping orders in a causal fit."""
    accepted, rejected = [], []
    for case in cases:
        if max(event.event_time for event in case.events) >= case.prediction_time:
            rejected.append({"case_id": case.case_id, "group": case.group,
                             "reason": "History order not wholly before target MIN(event_time)"})
        else:
            accepted.append(case)
    return tuple(accepted), rejected
