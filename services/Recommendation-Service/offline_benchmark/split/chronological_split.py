from collections import defaultdict
from dataclasses import replace

from offline_benchmark.common.types import CustomerSplit, PurchaseEvent, PurchaseOrder


MIN_PURCHASE_ORDERS = 5


def ordered_orders(events: tuple[PurchaseEvent, ...]) -> tuple[PurchaseOrder, ...]:
    """Real order IDs are indivisible; MIN(event_time) defines purchase time."""
    grouped: dict[str, list[PurchaseEvent]] = defaultdict(list)
    for event in events:
        if not event.event_id or not event.product_id or event.event_time is None:
            raise ValueError("Purchase history lacks a stable ID, product, or timestamp")
        if not isinstance(event.order_id, str) or not event.order_id.strip():
            raise ValueError("Purchase history lacks a real order ID; timestamp grouping is forbidden")
        if event.event_type != "PURCHASE":
            raise ValueError("The split input must contain PURCHASE events only")
        grouped[event.order_id].append(event)
    return tuple(sorted(
        (PurchaseOrder(order_id, min(e.event_time for e in rows),
                       tuple(sorted(rows, key=lambda e: (e.event_time, e.event_id))))
         for order_id, rows in grouped.items()),
        key=lambda order: (order.order_time, order.order_id),
    ))


def chronological_split(
    purchases: tuple[PurchaseEvent, ...],
) -> tuple[CustomerSplit, ...]:
    """Floor(4*n/5) oldest distinct PURCHASE orders train; remaining orders test."""
    by_customer: dict[str, list[PurchaseEvent]] = defaultdict(list)
    event_ids: set[str] = set()
    order_owners: dict[str, str] = {}
    for event in purchases:
        customer_id = (event.customer_id or "").strip()
        if not customer_id:
            continue
        ordered_orders((event,))  # Validate even ineligible customers; never invent order IDs.
        if event.event_id in event_ids:
            raise ValueError("Purchase history contains duplicate event IDs")
        event_ids.add(event.event_id)
        if order_owners.setdefault(event.order_id, customer_id) != customer_id:
            raise ValueError("A purchase order is reused across customers")
        by_customer[customer_id].append(replace(event, customer_id=customer_id))

    splits = []
    for customer_id, events in sorted(by_customer.items()):
        orders = ordered_orders(tuple(events))
        if len(orders) < MIN_PURCHASE_ORDERS:
            continue
        boundary = 4 * len(orders) // 5
        train = tuple(event for order in orders[:boundary] for event in order.events)
        test = tuple(event for order in orders[boundary:] for event in order.events)
        splits.append(CustomerSplit(customer_id, train, test))
    result = tuple(splits)
    verify_split(result)
    return result


def verify_split(splits: tuple[CustomerSplit, ...]) -> None:
    train_ids: set[str] = set()
    test_ids: set[str] = set()
    train_order_ids: set[str] = set()
    test_order_ids: set[str] = set()
    order_owners: dict[str, str] = {}
    customers: set[str] = set()
    for split in splits:
        if not split.customer_id or split.customer_id in customers:
            raise ValueError("Split customer IDs must be nonblank and unique")
        customers.add(split.customer_id)
        events = split.train + split.test
        if any(event.customer_id != split.customer_id for event in events):
            raise ValueError("Split contains another customer's events")
        train_orders = ordered_orders(split.train)
        test_orders = ordered_orders(split.test)
        train_set = {order.order_id for order in train_orders}
        test_set = {order.order_id for order in test_orders}
        if train_set & test_set:
            raise ValueError("An order crosses the TRAIN/TEST boundary")
        orders = ordered_orders(events)
        if (len(orders) < MIN_PURCHASE_ORDERS or not train_orders or not test_orders
                or len(train_orders) != 4 * len(orders) // 5):
            raise ValueError("Split must satisfy order eligibility and chronological 80/20")
        expected = tuple(event for order in orders for event in order.events)
        if events != expected or train_orders[-1].order_time > test_orders[0].order_time:
            raise ValueError("Split is not chronological by order time and order ID")
        if len({event.event_id for event in events}) != len(events):
            raise ValueError("Split repeats an event ID")
        for order in orders:
            if order_owners.setdefault(order.order_id, split.customer_id) != split.customer_id:
                raise ValueError("A purchase order is reused across customers")
        train_ids.update(event.event_id for event in split.train)
        test_ids.update(event.event_id for event in split.test)
        train_order_ids.update(train_set)
        test_order_ids.update(test_set)
    if train_order_ids & test_order_ids:
        raise ValueError("TEST order IDs leaked into TRAIN globally")
    if train_ids & test_ids:
        raise ValueError("TEST event IDs leaked into TRAIN")
    if len(train_ids) + len(test_ids) != sum(len(s.train) + len(s.test) for s in splits):
        raise ValueError("Event IDs are reused across customers")
