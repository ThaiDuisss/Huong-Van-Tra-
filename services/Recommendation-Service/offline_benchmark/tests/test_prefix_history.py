from dataclasses import replace
from datetime import datetime, timedelta
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

from offline_benchmark.common.types import CatalogProduct, DatabaseSnapshot, PurchaseEvent
from offline_benchmark.data.prefix_history import fingerprint_tables, load_prefix_context
from offline_benchmark.metrics.prefix_history import (
    compare_by_history, evaluate_prefix, paired_cluster_bootstrap, signal_analysis,
)
from offline_benchmark.models.prefix_history import (
    CONFIGURATIONS, MODES, component_signal, predict_cases, score_configuration,
)
from offline_benchmark.runners.run_prefix_history_benchmark import (
    label_mutation_probes, protected_file_hashes,
)
from offline_benchmark.split.prefix_history import (
    build_prefix_cases, causal_cases, customer_orders, verify_cases,
)


def fixture():
    products = tuple(CatalogProduct(f'p{i}', 'tea' if i<4 else 'fruit', (f'ingredient-{i%3}',))
                     for i in range(8))
    events = []
    for customer, n in (('alice', 6), ('bob', 3), ('carol', 2), ('single', 1)):
        for k in range(n):
            basket = (0, 1, 2) if k==0 else ((k%8), ((k+1)%8))
            for item, p in enumerate(basket):
                events.append(PurchaseEvent(f'{customer}-{k}-{item}', customer, f'p{p}',
                                            datetime(2026, 1, 1)+timedelta(days=2*k, seconds=item),
                                            f'{customer}-order-{k}', 'PURCHASE'))
    return DatabaseSnapshot(tuple(events), products)


class PrefixProtocolTests(unittest.TestCase):
    def test_sparse_eligibility_and_order_count_not_event_count(self):
        snapshot = fixture()
        cases = build_prefix_cases(snapshot.purchases)
        verify_cases(cases, snapshot.purchases)
        self.assertEqual(len(cases), 8)
        counts = {str(k): sum(c.history_order_count==k for c in cases) for k in range(1,6)}
        self.assertEqual(counts, {'1':3, '2':2, '3':1, '4':1, '5':1})
        first = next(c for c in cases if c.customer_id=='alice' and c.history_order_count==1)
        self.assertEqual(len(first.events), 3)
        self.assertEqual(first.history_order_count, 1)
        self.assertEqual({e.order_id for e in first.events}, {'alice-order-0'})
        self.assertEqual(first.target.order_id, 'alice-order-1')

    def test_exact_prefix_target_and_whole_basket(self):
        snapshot = fixture()
        orders = customer_orders(snapshot.purchases)['alice']
        for case in build_prefix_cases(snapshot.purchases):
            if case.customer_id!='alice':
                continue
            k = case.history_order_count
            self.assertEqual(case.history, orders[:k])
            self.assertEqual(case.target, orders[k])
            self.assertFalse(set(case.events)&set(case.target.events))
        case = next(c for c in build_prefix_cases(snapshot.purchases) if c.customer_id=='alice')
        broken = replace(case, history=(replace(case.history[0], events=case.history[0].events[:1]),))
        with self.assertRaises(ValueError):
            verify_cases((broken,), snapshot.purchases)

    def test_history_two_is_only_o1_o2_target_o3(self):
        snapshot = fixture()
        case = next(c for c in build_prefix_cases(snapshot.purchases)
                    if c.customer_id=='alice' and c.history_order_count==2)
        self.assertEqual([o.order_id for o in case.history], ['alice-order-0','alice-order-1'])
        self.assertEqual(case.target.order_id, 'alice-order-2')
        self.assertFalse(any(e.order_id=='alice-order-3' for e in case.events))

    def test_tie_order_id_determinism_and_causal_exclusion(self):
        rows = tuple(replace(e, event_time=datetime(2026,1,1)) for e in fixture().purchases)
        forward = build_prefix_cases(rows)
        self.assertEqual(forward, build_prefix_cases(tuple(reversed(rows))))
        self.assertEqual(forward[0].history[0].order_id, 'alice-order-0')
        accepted, rejected = causal_cases(forward)
        self.assertFalse(accepted)
        self.assertEqual(len(rejected), len(forward))

    def test_overlapping_order_is_preserved_then_rejected_not_partially_trained(self):
        snapshot = fixture()
        rows = tuple(replace(e, event_time=datetime(2026,1,20)) if e.event_id=='alice-0-2' else e
                     for e in snapshot.purchases)
        cases = build_prefix_cases(rows)
        first = next(c for c in cases if c.customer_id=='alice' and c.history_order_count==1)
        self.assertEqual(len(first.history[0].events), 3)
        accepted, rejected = causal_cases(cases)
        self.assertNotIn(first, accepted)
        self.assertIn(first.case_id, {r['case_id'] for r in rejected})

    def test_invalid_events_order_owner_and_duplicate_ids_fail(self):
        snapshot = fixture()
        for rows in (snapshot.purchases+(snapshot.purchases[0],),
                     (replace(snapshot.purchases[0], order_id=None),)+snapshot.purchases[1:],
                     (replace(snapshot.purchases[0], event_type='RETURN'),)+snapshot.purchases[1:],
                     (replace(snapshot.purchases[0], customer_id='bob'),)+snapshot.purchases[1:]):
            with self.subTest(rows=rows[:1]), self.assertRaises(ValueError):
                build_prefix_cases(rows)


class CausalScoringTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = fixture()
        self.cases, _ = causal_cases(build_prefix_cases(self.snapshot.purchases))

    def test_exact_existing_component_reference_and_repeated_predictions(self):
        actual, diagnostics, references = predict_cases(self.snapshot, self.cases, verify_reference=True)
        repeated, repeated_diagnostics, _ = predict_cases(self.snapshot, self.cases)
        self.assertEqual(actual, repeated)
        self.assertEqual(diagnostics, repeated_diagnostics)
        self.assertEqual({r['history_group'] for r in references}, {'1','2','3','4','>=5'})
        for case in self.cases:
            record = diagnostics[case.case_id]
            expected = [e for e in self.snapshot.purchases if e.event_time<case.prediction_time]
            self.assertEqual(record['global_training_events'], len(expected))
            self.assertLess(datetime.fromisoformat(record['latest_global_training_event_time']), case.prediction_time)

    def test_mutating_target_and_all_customer_future_labels_preserves_every_grid(self):
        predictions, diagnostics, _ = predict_cases(self.snapshot, self.cases)
        probes = label_mutation_probes(self.snapshot, self.cases, predictions, diagnostics)
        self.assertGreaterEqual(len(probes), 5)
        self.assertTrue(all(p['both_modes_all_configurations_predictions_unchanged'] for p in probes))

    def test_global_other_customer_future_labels_cannot_enter_scores(self):
        case = self.cases[0]
        changed = replace(self.snapshot, purchases=tuple(
            replace(e, product_id='future-only') if e.event_time>=case.prediction_time else e
            for e in self.snapshot.purchases))
        self.assertEqual(predict_cases(self.snapshot, (case,))[:2], predict_cases(changed, (case,))[:2])

    def test_exclusion_never_changes_history_or_target_labels(self):
        predictions, _, _ = predict_cases(self.snapshot, self.cases)
        result = evaluate_prefix(self.cases, predictions, tuple(p.product_id for p in self.snapshot.products))
        for mode in MODES:
            for config in result[mode]['configurations']:
                self.assertEqual(config['overall']['evaluated_cases'], len(self.cases))
                if mode==MODES[1]:
                    by_case = {r['case_id']:r for r in config['case_results']}
                    for case in self.cases:
                        self.assertFalse(set(by_case[case.case_id]['top5'])&{e.product_id for e in case.events})

    def test_repeat_only_target_and_empty_candidates_are_retained(self):
        products = (CatalogProduct('p0','tea'),)
        rows = (PurchaseEvent('e0','u','p0',datetime(2026,1,1),'o0','PURCHASE'),
                PurchaseEvent('e1','u','p0',datetime(2026,1,2),'o1','PURCHASE'))
        snapshot = DatabaseSnapshot(rows, products)
        cases = build_prefix_cases(rows)
        predictions, _, _ = predict_cases(snapshot, cases)
        results = evaluate_prefix(cases, predictions, ('p0',))
        for config in results[MODES[1]]['configurations']:
            m = config['history_groups']['1']
            self.assertEqual(m['evaluated_cases'], 1)
            self.assertEqual(m['returned_recommendation_slots'], 0)
            self.assertEqual(m['fixed_precision_denominator_slots'], 5)
            self.assertEqual(m['recall_at_5'], 0)
            self.assertEqual(m['precision_at_5'], 0)

    def test_signal_is_candidate_specific_and_positive_can_be_flat(self):
        self.assertEqual(component_signal({'p0':1,'p1':1}, ('p0','p1')),
                         {'positive_candidates':2,'positive_signal':True,'flat_scores':True})
        predictions, diagnostics, _ = predict_cases(self.snapshot, self.cases)
        signals = signal_analysis(self.cases, diagnostics)
        self.assertEqual(signals[MODES[0]]['1']['evaluated_cases'], 3)
        self.assertGreaterEqual(signals[MODES[0]]['1']['mean_positive_cf_candidates'],
                                signals[MODES[1]]['1']['mean_positive_cf_candidates'])
        self.assertEqual(len(predictions[MODES[0]]), len(CONFIGURATIONS))

    def test_flat_component_weights_not_redistributed(self):
        values = ({'p':1.0}, {'p':0.0}, {'p':0.0})
        self.assertEqual(score_configuration((0.2,0.4,0.4), values), {'p':0.2})
        with self.assertRaises(ValueError):
            score_configuration((0.2,0.4,0.5), values)


class ReliabilityAndSafetyTests(unittest.TestCase):
    def test_cluster_bootstrap_preserves_pairing_and_is_deterministic(self):
        a = [{'case_id':f'case{i}', 'customer_id':'u' if i<2 else 'v',
              'ndcg_at_5':0.1, 'recall_at_5':0.2} for i in range(3)]
        b = [dict(row, ndcg_at_5=0.3, recall_at_5=0.4) for row in a]
        result = paired_cluster_bootstrap(a,b,200)
        self.assertEqual(result, paired_cluster_bootstrap(a,b,200))
        self.assertEqual(result['customer_clusters'], 2)
        self.assertAlmostEqual(result['intervals_b_minus_a']['ndcg_at_5']['lower'], 0.2)
        self.assertFalse(result['selection_bias_corrected'])
        with self.assertRaises(ValueError):
            paired_cluster_bootstrap(a, list(reversed(b)), 200)

    def test_both_modes_group_winners_are_derived_from_metrics(self):
        snapshot = fixture()
        cases = build_prefix_cases(snapshot.purchases)
        predictions, _, _ = predict_cases(snapshot, cases)
        result = compare_by_history(evaluate_prefix(cases, predictions,
                                   tuple(p.product_id for p in snapshot.products)), 100)
        for mode in MODES:
            for group in ('1','2','3','4','>=5'):
                scores = [r['history_groups'][group]['ndcg_at_5'] for r in result[mode]['configurations']]
                self.assertEqual(result[mode]['best_by_history'][group]['aggregate']['ndcg_at_5'], max(scores))

    def test_full_row_fingerprint_detects_mutation_with_unchanged_count(self):
        db = MagicMock()
        db.execute.return_value = [(1,'before')]
        counts, hashes = fingerprint_tables(db)
        db.execute.return_value = [(1,'after')]
        changed_counts, changed_hashes = fingerprint_tables(db)
        self.assertEqual(counts, changed_counts)
        self.assertNotEqual(hashes, changed_hashes)
        self.assertTrue(all(str(call.args[0]).lstrip().startswith('SELECT') for call in db.execute.call_args_list))

    def test_db_readonly_confirmed_before_reads_rollback_without_commit(self):
        engine, connection, db = MagicMock(), MagicMock(), MagicMock()
        engine.connect.return_value.__enter__.return_value = connection
        connection.exec_driver_sql.return_value.scalar.return_value = 1
        session = MagicMock()
        session.return_value.__enter__.return_value = db
        db.execute.side_effect = [[('PURCHASE',3)], [SimpleNamespace(id='p0',product_code='P0',name='Tea')]]
        settings = SimpleNamespace(sqlalchemy_database_url='mysql+pymysql://unused', mysql_database='fixture')
        with patch('app.core.settings.Settings', return_value=settings), \
             patch('offline_benchmark.data.prefix_history.create_engine', return_value=engine), \
             patch('offline_benchmark.data.prefix_history.Session', session), \
             patch('offline_benchmark.data.prefix_history.read_snapshot', return_value=fixture()), \
             patch('offline_benchmark.data.prefix_history.fingerprint_tables', return_value=({'table':3},{'table':'hash'})):
            _, metadata = load_prefix_context()
        statements = [call.args[0] for call in connection.exec_driver_sql.call_args_list]
        self.assertEqual(statements[:4], ['SET TRANSACTION ISOLATION LEVEL REPEATABLE READ',
                                        'SET SESSION TRANSACTION READ ONLY', 'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY',
                                        'SELECT @@transaction_read_only'])
        self.assertTrue(metadata['server_confirmed_read_only'])
        connection.rollback.assert_called_once()
        connection.commit.assert_not_called()
        db.flush.assert_not_called()
        db.commit.assert_not_called()
        self.assertFalse(session.call_args.kwargs['autoflush'])
        engine.dispose.assert_called_once()

    def test_protected_hashes_detect_canonical_output_change_without_writing_it(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            outputs = root/'offline_benchmark'/'outputs'
            outputs.mkdir(parents=True)
            canonical = outputs/'ROUTING_COMPARISON.md'
            canonical.write_text('canonical', encoding='utf-8')
            prefix = outputs/'PREFIX_HISTORY_BENCHMARK.md'
            prefix.write_text('new', encoding='utf-8')
            with patch('offline_benchmark.runners.run_prefix_history_benchmark.ROOT', root), \
                 patch('offline_benchmark.runners.run_prefix_history_benchmark.OUTPUTS', outputs):
                before = protected_file_hashes()
                prefix.write_text('regenerated', encoding='utf-8')
                self.assertEqual(before, protected_file_hashes())
                canonical.write_text('changed', encoding='utf-8')
                self.assertNotEqual(before, protected_file_hashes())


if __name__ == '__main__':
    unittest.main()
