from dataclasses import replace
from datetime import datetime, timedelta
from importlib import import_module
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from offline_benchmark.common.manifest import build_manifest, WORKSPACE
from offline_benchmark.common.reporting import serialize_reports
from offline_benchmark.common.types import CustomerSplit, DatabaseSnapshot, PurchaseEvent
from offline_benchmark.metrics.evaluator import validate_predictions
from offline_benchmark.runners.common import evaluate, MODELS
from offline_benchmark.runners.prepare_split import invalidate_outputs
from offline_benchmark.split.chronological_split import chronological_split, ordered_orders, verify_split
from offline_benchmark.tests.test_benchmark import fixture


def basket_events(sizes=(2, 1, 3, 2, 3)):
    return tuple(
        PurchaseEvent(f"event-{order}-{line}", "basket", f"p{(order + line) % 8}",
                      datetime(2026, 1, 1) + timedelta(days=order),
                      f"order-{order}", "PURCHASE")
        for order, size in enumerate(sizes) for line in range(size)
    )


class OrderSplitTests(unittest.TestCase):
    def test_five_distinct_orders_are_eligible(self):
        split, = chronological_split(basket_events())
        self.assertEqual(len(ordered_orders(split.train)), 4)
        self.assertEqual(len(ordered_orders(split.test)), 1)

    def test_four_orders_with_ten_item_events_are_ineligible(self):
        events = basket_events((3, 3, 2, 2))
        self.assertEqual(len(events), 10)
        self.assertEqual(chronological_split(events), ())

    def test_whole_multi_item_orders_in_train_and_test(self):
        events = basket_events()
        split, = chronological_split(tuple(reversed(events)))
        self.assertEqual(len(split.train), 8)
        self.assertEqual(len(split.test), 3)
        self.assertFalse({e.order_id for e in split.train} & {e.order_id for e in split.test})
        for order in ordered_orders(events):
            expected = {e.event_id for e in order.events}
            partition = split.train if order.order_id != "order-4" else split.test
            self.assertEqual({e.event_id for e in partition if e.order_id == order.order_id}, expected)
        verify_split((split,))

    def test_same_order_cannot_cross_boundary_even_without_shared_events(self):
        split, = chronological_split(basket_events())
        corrupt = replace(split, train=split.train + split.test[:1], test=split.test[1:])
        with self.assertRaisesRegex(ValueError, "order crosses"):
            verify_split((corrupt,))

    def test_order_timestamp_ties_sort_by_order_id_not_event_id(self):
        events = tuple(replace(e, event_time=datetime(2026, 1, 1),
                               event_id=f"reverse-{100-i:03}")
                       for i, e in enumerate(basket_events()))
        split, = chronological_split(events)
        self.assertEqual(chronological_split(tuple(reversed(events))), (split,))
        self.assertEqual({e.order_id for e in split.test}, {"order-4"})
        self.assertEqual(ordered_orders(split.train)[-1].order_time,
                         ordered_orders(split.test)[0].order_time)

    def test_inconsistent_line_times_use_min_without_splitting_order(self):
        events = basket_events()
        events = (replace(events[0], event_time=datetime(2026, 2, 1)),) + events[1:]
        split, = chronological_split(events)
        self.assertEqual(ordered_orders(split.train)[0].order_time, datetime(2026, 1, 1))
        self.assertIn(events[0], split.train)
        self.assertEqual(len([e for e in split.train if e.order_id == "order-0"]), 2)

    def test_missing_order_id_and_nonpurchase_rows_stop(self):
        for changes in ({"order_id": None}, {"order_id": " "}, {"event_type": "RETURN"}):
            events = basket_events()
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                chronological_split((replace(events[0], **changes),) + events[1:])

    def test_order_reuse_across_customers_rejected_in_source_and_split(self):
        splits = chronological_split(fixture().purchases)
        other = replace(splits[1], test=tuple(replace(e, order_id=splits[0].train[0].order_id)
                                              if i == 0 else e for i, e in enumerate(splits[1].test)))
        with self.assertRaises(ValueError):
            verify_split((splits[0], other))
        events = tuple(e for s in (splits[0], other) for e in s.train + s.test)
        with self.assertRaises(ValueError):
            chronological_split(events)

    def test_later_order_cannot_be_moved_to_train(self):
        split, = chronological_split(basket_events())
        orders = ordered_orders(split.train + split.test)
        corrupt = CustomerSplit(split.customer_id,
                                tuple(e for o in orders[1:] for e in o.events), orders[0].events)
        with self.assertRaisesRegex(ValueError, "chronological"):
            verify_split((corrupt,))

    def test_all_six_models_receive_exact_train_events_only(self):
        snapshot = DatabaseSnapshot(basket_events(), fixture().products)
        splits = chronological_split(snapshot.purchases)
        for key, class_name in MODELS.items():
            module = import_module(f"offline_benchmark.models.{key}")
            constructor = getattr(module, class_name)
            with self.subTest(model=key), patch.object(module, class_name, wraps=constructor) as fitting:
                evaluate(key, snapshot.products, splits)
                training = fitting.call_args.args[0]
                self.assertEqual(training.purchases, splits[0].train)
                self.assertFalse({e.order_id for e in training.purchases} &
                                 {e.order_id for e in splits[0].test})

    def test_manifest_records_customer_order_event_counts_and_ratios(self):
        snapshot = DatabaseSnapshot(basket_events() + tuple(
            replace(e, customer_id="excluded", event_id="excluded-" + e.event_id,
                    order_id="excluded-" + e.order_id) for e in basket_events((3, 3, 2, 2))),
            fixture().products)
        m = build_manifest(snapshot, chronological_split(snapshot.purchases))
        self.assertEqual((m["total_customer_count"], m["eligible_customer_count"],
                          m["excluded_customer_count"]), (2, 1, 1))
        self.assertEqual((m["eligible_order_count"], m["train_order_count"], m["test_order_count"]), (5, 4, 1))
        self.assertEqual((m["eligible_event_count"], m["train_event_count"], m["test_event_count"]), (11, 8, 3))
        self.assertEqual(m["train_order_ratio"], 0.8)
        self.assertEqual(m["train_event_ratio"], 8 / 11)

    def test_old_contract_and_order_id_drift_reject_predictions(self):
        snapshot = DatabaseSnapshot(basket_events(), fixture().products)
        splits = chronological_split(snapshot.purchases)
        payload = json.loads(serialize_reports(evaluate("popularity", snapshot.products, splits),
                                               build_manifest(snapshot, splits)))
        old = {**payload, "split": {**payload["split"], "contract": "purchase-chronological-80-20-v1"}}
        with self.assertRaisesRegex(ValueError, "manifest differs"):
            validate_predictions("popularity", old, snapshot, splits)
        changed = replace(snapshot, purchases=tuple(replace(e, order_id="new-" + e.order_id)
                                                    for e in snapshot.purchases))
        with self.assertRaisesRegex(ValueError, "manifest differs"):
            validate_predictions("popularity", payload, changed, chronological_split(changed.purchases))

    def test_refresh_invalidates_custom_predictions_metrics_and_proofs(self):
        with TemporaryDirectory(dir=WORKSPACE) as directory:
            root = Path(directory)
            files = {"custom-customer.json": {"split": {}, "predictions": []},
                     "svd_metrics.json": {}, "svd_determinism.json": {},
                     "benchmark_summary.json": {}, "event_row_baseline.json": {"historical_only": True}}
            for name, payload in files.items():
                (root / name).write_text(json.dumps(payload), encoding="utf-8")
            with patch("offline_benchmark.runners.prepare_split.OUTPUTS", root), \
                 patch("offline_benchmark.runners.prepare_split.artifact_path", side_effect=lambda name: root / name):
                removed = invalidate_outputs()
            self.assertEqual(set(removed), set(files) - {"event_row_baseline.json"})
            self.assertTrue((root / "event_row_baseline.json").is_file())


if __name__ == "__main__":
    unittest.main()
