from collections import Counter
from dataclasses import replace
from datetime import datetime, timedelta
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import numpy as np

from app.recommendation.v1_model import (
    CatalogProduct, PurchaseCount, V1ModelError, build_artifact, feature_tokens,
    hybrid_scores, normalize_scores,
)
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory
from app.service.recommendation_service import RecommendationService
from offline_benchmark.common.types import DatabaseSnapshot, PurchaseEvent
from offline_benchmark.data.exact_tfidf import payload, replay
from offline_benchmark.metrics.exact_tfidf import (
    CURRENT, Policy, comparisons, metric_rows, policies, policy_rows, select, summarize, windows,
)
from offline_benchmark.models.exact_tfidf import CausalTfidf, MODELS, MODES, predict, rank, rankings
from offline_benchmark.runners.run_exact_tfidf_benchmark import regression_probes, reserve_output, write
from offline_benchmark.split.prefix_history import build_prefix_cases, causal_cases


def fixture():
    products = tuple(CatalogProduct(f'p{i}', f'SKU{i}', f'Trà món {i}',
                    'tea' if i < 4 else 'fruit', (f'ing{i%3}',)) for i in range(8))
    events = []
    for customer, size in (('alice', 12), ('bob', 8), ('carol', 5), ('single', 1)):
        for k in range(size):
            for line, p in enumerate((k%8, (k+1)%8)):
                events.append(PurchaseEvent(f'{customer}-{k}-{line}', customer, f'p{p}',
                    datetime(2026, 1, 1)+timedelta(days=k, hours={'alice':0, 'bob':1, 'carol':2, 'single':3}[customer]),
                    f'{customer}-order-{k}', 'PURCHASE'))
    cases, rejected = causal_cases(build_prefix_cases(tuple(events)))
    assert not rejected
    return products, tuple(events), cases


class ExactScoringTests(unittest.TestCase):
    def test_production_unicode_namespaced_features_and_smooth_idf(self):
        p = CatalogProduct('p', 'code', 'ＴＥＡ tea_tea', ' TEA ', ('Milk', 'Milk'))
        tokens = feature_tokens(p)
        self.assertEqual(tokens['name:tea'], 3)
        self.assertEqual(tokens['category:tea'], 1)
        self.assertEqual(tokens['ingredient:milk'], 1)
        other = CatalogProduct('q', 'q', 'Juice', None)
        a = CausalTfidf((p, other), ()).template
        np.testing.assert_allclose(a.idf, [1+np.log(3/2)]*len(a.vocabulary))
        np.testing.assert_allclose(np.asarray(a.content.power(2).sum(axis=1)).ravel(), [1,1])

    def test_cf_and_components_equal_independent_production_builder(self):
        products, events, cases = fixture()
        engine = CausalTfidf(products, events)
        for case in cases:
            artifact = engine.advance(case.prediction_time)
            counts = Counter((e.customer_id, e.product_id) for e in events if e.event_time < case.prediction_time)
            reference = build_artifact(products, tuple(PurchaseCount(u, p, n) for (u,p),n in counts.items()))
            np.testing.assert_array_equal(artifact.similarity.toarray(), reference.similarity.toarray())
            np.testing.assert_array_equal(artifact.popularity, reference.popularity)
            self.assertEqual(artifact.history_sha256, reference.history_sha256)
            history = Counter(e.product_id for e in case.events)
            self.assertEqual(artifact.components(history), reference.components(history))

    def test_normalization_after_exclusion_and_sixty_parity(self):
        products, events, cases = fixture()
        a = CausalTfidf(products, events).advance(cases[-1].prediction_time)
        content = dict(zip(a.product_ids, [100, .9, 0, 1, .2, .3, .4, .5]))
        cf = dict(zip(a.product_ids, [100, 0, 1, 0, .2, .3, .4, .5]))
        fake = SimpleNamespace(product_ids=a.product_ids, popularity=a.popularity,
                               components=lambda _: (content, cf))
        candidates = a.product_ids[1:]
        scored = rankings(fake, {'p0':1}, candidates)
        serving, flat = hybrid_scores(content, cf, candidates)
        self.assertFalse(flat)
        self.assertEqual(scored['Hybrid60'].top5, rank(serving))
        expected50 = {p: .5*normalize_scores({p:content[p] for p in candidates})[p]
                           +.5*normalize_scores({p:cf[p] for p in candidates})[p] for p in candidates}
        self.assertEqual(scored['Hybrid50'].top5, rank(expected50))
        self.assertNotEqual(scored['Hybrid50'].top5, scored['Hybrid60'].top5)
        self.assertNotEqual(normalize_scores(content)['p1'], normalize_scores({p:content[p] for p in candidates})['p1'])

    def test_current_policy_ranking_matches_real_service(self):
        products, events, cases = fixture()
        for case in (cases[0], cases[-1]):
            a = CausalTfidf(products, events).advance(case.prediction_time)
            history = Counter(e.product_id for e in case.events)
            for exclude in (False, True):
                candidates = tuple(p for p in a.product_ids if not exclude or p not in history)
                result = rankings(a, history, candidates)[CURRENT.model(case.history_order_count)]
                popular = {p:float(n) for p,n in zip(a.product_ids, a.popularity)}
                repo = SimpleNamespace(customer_history=lambda *_: CustomerPurchaseHistory(case.history_order_count, history),
                    active_catalog=lambda *_: a.products,
                    popularity=lambda db, purchased, limit: [(p.product_id, p.product_code, p.name, popular[p.product_id])
                        for p in sorted(a.products, key=lambda p:(-popular[p.product_id],p.product_id))
                        if p.product_id not in purchased][:limit])
                service = RecommendationService(SimpleNamespace(load=lambda:a), repo)
                actual = service.recommend(None, case.customer_id, 5, exclude)
                self.assertEqual(tuple(i.product_id for i in actual.items), result.top5)

    def test_empty_training_flat_falls_back_with_deterministic_ties(self):
        products, _, _ = fixture()
        a = build_artifact(products, ())
        result = rankings(a, {}, a.product_ids)
        for model in MODELS:
            self.assertEqual(result[model].top5, a.product_ids[:5])
        self.assertEqual(result['Hybrid60'].fallback_reason, 'BOTH_COMPONENTS_FLAT')
        self.assertEqual(result['Content'].fallback_reason, 'CONTENT_FLAT')
        self.assertEqual(result['CF'].fallback_reason, 'CF_FLAT')
        self.assertIsNone(result['Popularity'].fallback_reason)

    def test_empty_candidates_do_not_fallback_or_pad(self):
        products, _, _ = fixture()
        result = rankings(build_artifact(products, ()), {}, ())
        for model in MODELS:
            self.assertEqual(result[model].top5, ())
            self.assertIsNone(result[model].fallback_reason)

    def test_one_flat_component_does_not_trigger_hybrid_fallback(self):
        products, _, _ = fixture()
        a = build_artifact(products, ())
        content = dict(zip(a.product_ids, range(8)))
        fake = SimpleNamespace(product_ids=a.product_ids, popularity=a.popularity,
                               components=lambda _: (content, {p:0.0 for p in a.product_ids}))
        result = rankings(fake, {}, a.product_ids)
        self.assertIsNone(result['Hybrid60'].fallback_reason)
        self.assertTrue(result['Hybrid60'].cf_flat)
        self.assertEqual(result['Hybrid60'].top5, result['Content'].top5)

    def test_recoverable_model_error_fallback(self):
        products, _, _ = fixture()
        a = build_artifact(products, ())
        def unavailable(_):
            raise V1ModelError('isolated incompatible model')
        fake = SimpleNamespace(product_ids=a.product_ids, popularity=a.popularity, components=unavailable)
        for model, result in rankings(fake, {}, a.product_ids).items():
            if model != 'Popularity':
                self.assertEqual(result.fallback_reason, 'V1_MODEL_NOT_AVAILABLE')

    def test_tie_order_is_exact_and_product_id_ascending(self):
        self.assertEqual(rank({'b':1, 'a':1, 'c':2}), ('c','a','b'))

    def test_cached_state_immutable_and_all_input_sensitive(self):
        products, events, cases = fixture()
        engine = CausalTfidf(products, events)
        cutoff = cases[-1].prediction_time
        a = engine.advance(cutoff)
        self.assertIs(engine.advance(cutoff), a)
        self.assertEqual(engine.builds, 1)
        with self.assertRaises(ValueError):
            a.popularity[0] = 1
        with self.assertRaises(ValueError):
            a.content.data[0] = 1
        changed = CausalTfidf((replace(products[0],name='changed'),)+products[1:],events)
        changed.advance(cutoff)
        self.assertNotEqual(engine.state_key, changed.state_key)
        changed = CausalTfidf(products,(replace(events[0],product_id='p7'),)+events[1:])
        changed.advance(cutoff)
        self.assertNotEqual(engine.state_key, changed.state_key)


class CausalityTests(unittest.TestCase):
    def test_strict_cutoff_excludes_equal_time_other_customer(self):
        products, events, cases = fixture()
        case = cases[0]
        tied = PurchaseEvent('tied','other','p7',case.prediction_time,'other-tied','PURCHASE')
        earlier = replace(tied,event_id='earlier',order_id='other-earlier',event_time=case.prediction_time-timedelta(seconds=1))
        state = CausalTfidf(products,events+(tied,earlier))
        state.advance(case.prediction_time)
        self.assertEqual(state.counts[('other','p7')],1)
        self.assertEqual(state.customer_order_ids['other'],{'other-earlier'})

    def test_spanning_global_order_never_partially_available(self):
        products, events, cases = fixture()
        cutoff = cases[0].prediction_time
        first = PurchaseEvent('span-a','other','p6',cutoff-timedelta(hours=1),'spanning','PURCHASE')
        second = replace(first,event_id='span-b',product_id='p7',event_time=cutoff+timedelta(hours=1))
        state = CausalTfidf(products,events+(first,second))
        state.advance(cutoff)
        self.assertEqual(state.counts[('other','p6')],0)
        state.advance(second.event_time+timedelta(seconds=1))
        self.assertEqual(state.counts[('other','p6')],1)
        self.assertEqual(state.counts[('other','p7')],1)

    def test_partial_local_history_is_rejected(self):
        products, events, cases = fixture()
        case = cases[0]
        broken = replace(case,history=(replace(case.history[0],events=case.history[0].events[:1]),))
        with self.assertRaises(ValueError):
            predict(products,events,(broken,))

    def test_future_labels_for_all_customers_do_not_change_earlier_predictions(self):
        products, events, cases = fixture()
        snapshot = DatabaseSnapshot(events, ())
        checks = regression_probes(products,snapshot,cases)
        self.assertGreaterEqual(len(checks),5)
        self.assertTrue(all(c['all_customer_future_label_mutation_unchanged'] for c in checks))

    def test_determinism_and_exclusion_all_models(self):
        products, events, cases = fixture()
        a, proof, builds = predict(products,events,cases)
        b, other, _ = predict(products,tuple(reversed(events)),cases)
        self.assertEqual(proof,other)
        self.assertEqual(builds,len({c.prediction_time for c in cases}))
        for mode in MODES:
            for model in MODELS:
                self.assertEqual(a[mode][model],b[mode][model])
                if mode == 'discovery':
                    for case, _, result in a[mode][model]:
                        self.assertFalse(set(result.top5)&{e.product_id for e in case.events})

    def test_backwards_cutoff_is_rejected(self):
        products, events, cases = fixture()
        state = CausalTfidf(products,events)
        state.advance(cases[-1].prediction_time)
        with self.assertRaises(ValueError):
            state.advance(cases[0].prediction_time)


class EvaluationTests(unittest.TestCase):
    def rows(self):
        products, events, cases = fixture()
        predictions, _, _ = predict(products,events,cases)
        return products, cases, {mode:metric_rows(predictions[mode]) for mode in MODES}

    def test_metrics_fixed_precision_denominator_and_unreachable_labels_kept(self):
        products, cases, rows = self.rows()
        catalog = tuple(p.product_id for p in products)
        summary = summarize(rows['discovery']['Content'],catalog,'discovery')['overall']
        self.assertEqual(summary['evaluated_cases'],len(cases))
        self.assertGreater(summary['unreachable_target_cases'],0)
        self.assertGreater(summary['candidate_shortage_cases'],0)
        self.assertAlmostEqual(summary['precision_at_5'],summary['total_hits']/(5*len(cases)))

    def test_zero_cohort_not_fabricated_and_order_groups_not_line_counts(self):
        products, _, rows = self.rows()
        summary = summarize(rows['repeat']['Content'],tuple(p.product_id for p in products),'repeat')
        self.assertIsNone(summary['groups']['0'])
        self.assertEqual(summary['groups']['1']['evaluated_cases'],3)
        self.assertEqual(summary['groups']['2']['evaluated_cases'],3)

    def test_windows_global_and_tied_timestamps_never_split(self):
        _, _, cases = fixture()
        split, contract = windows(cases)
        self.assertLess(max(c.prediction_time for c in split['warmup']),min(c.prediction_time for c in split['validation']))
        self.assertLess(max(c.prediction_time for c in split['validation']),min(c.prediction_time for c in split['test']))
        self.assertEqual(sum(map(len,split.values())),len(cases))
        self.assertEqual({c.prediction_time for c in split['test']} & {c.prediction_time for c in split['validation']},set())
        with self.assertRaises(ValueError):
            windows(cases,contract['final_test_start_inclusive'],contract['validation_start_inclusive'])

    def test_selection_grid_and_immutable_policy(self):
        products, _, rows = self.rows()
        chosen, grid = select(rows['repeat'],tuple(p.product_id for p in products),'repeat')
        self.assertIn(chosen['overall'].key,grid)
        self.assertEqual(chosen['three'].family,'three')
        with self.assertRaises(AttributeError):
            chosen['three'].threshold = 100

    def test_runner_freezes_before_test_and_test_label_mutation_cannot_select_policy(self):
        from dataclasses import asdict
        from app.recommendation.v1_model import catalog_fingerprint
        from offline_benchmark.common.manifest import digest
        from offline_benchmark.runners import run_exact_tfidf_benchmark as runner
        products, events, cases = fixture()
        partitions, _ = windows(cases)
        cutoff = partitions['test'][0].prediction_time
        freezes = []
        with TemporaryDirectory(dir=runner.REPO/'.tmp') as folder:
            for iteration in (0, 1):
                # Poison every final-period relevant product while retaining IDs/time.
                inputs = events if iteration == 0 else tuple(replace(e,product_id='future-marker')
                    if e.event_time >= cutoff else e for e in events)
                snapshot = DatabaseSnapshot(inputs, ())
                audit = {'catalog_sha256':catalog_fingerprint(products),
                         'purchases_sha256':digest([asdict(e) for e in inputs])}
                destination = Path(folder)/f'run-{iteration}'
                args = SimpleNamespace(output_dir=destination, snapshot_file=Path(folder)/'input', env_file=None,
                    validation_start=None, test_start=None, historical_prefix=Path(folder)/'no-historical-output',
                    bootstrap_samples=100)
                original = runner.predict
                seen_test = []
                def checked_predict(products, purchases, window_cases, progress=None):
                    if window_cases[0].prediction_time >= cutoff:
                        frozen = destination/'frozen-policy.json'
                        self.assertTrue(frozen.is_file(), 'Policy must exist before any final-test prediction')
                        seen_test.append(json.loads(frozen.read_text())['modes'])
                    return original(products, purchases, window_cases)
                with patch.object(runner,'replay',return_value=(snapshot,products,audit)), \
                        patch.object(runner,'predict',side_effect=checked_predict), patch('builtins.print'):
                    runner.run(args)
                self.assertEqual(len(seen_test),1)
                freezes.append(seen_test[0])
            self.assertEqual(freezes[0],freezes[1])

    def test_router_zero_and_threshold_boundaries(self):
        p = Policy('three','Content','Hybrid60',3,'three')
        self.assertEqual([p.model(k) for k in (0,1,2,3,4)],['Popularity','Content','Content','Hybrid60','Hybrid60'])
        self.assertEqual(CURRENT.model(4),'Popularity')
        self.assertEqual(CURRENT.model(5),'Hybrid60')

    def test_misaligned_policy_cases_rejected(self):
        _, _, rows = self.rows()
        rows['repeat']['CF'] = rows['repeat']['CF'][1:]
        with self.assertRaises(ValueError):
            policy_rows(rows['repeat'],CURRENT)

    def test_bootstrap_is_paired_clustered_and_deterministic(self):
        products, _, rows = self.rows()
        chosen, _ = select(rows['repeat'],tuple(p.product_id for p in products),'repeat')
        first = comparisons(rows['repeat'],chosen,100)
        self.assertEqual(first,comparisons(rows['repeat'],chosen,100))
        self.assertLess(first['overall']['overall']['customer_clusters'],len(rows['repeat']['Content']))

    def test_snapshot_replay_verifies_hash_and_roundtrip(self):
        from app.recommendation.v1_model import catalog_fingerprint
        from offline_benchmark.common.manifest import digest
        from dataclasses import asdict
        products, events, _ = fixture()
        audit = {'catalog_sha256':catalog_fingerprint(products),'purchases_sha256':digest([asdict(e) for e in events])}
        data = payload(DatabaseSnapshot(events,()),products,audit)
        with TemporaryDirectory() as folder:
            path = Path(folder)/'snapshot.json'
            write(path,data)
            snapshot, catalog, _ = replay(path)
            self.assertEqual(snapshot.purchases,events)
            self.assertEqual(catalog,products)
            data['catalog'][0]['name'] = 'tampered'
            path.write_text(json.dumps(data,default=str),encoding='utf-8')
            with self.assertRaises(ValueError):
                replay(path)

    def test_output_reservation_rejects_existing_or_protected_paths(self):
        from offline_benchmark.runners.run_exact_tfidf_benchmark import REPO
        with self.assertRaises(ValueError):
            reserve_output(REPO/'services/Recommendation-Service/artifacts')
        with self.assertRaises(ValueError):
            reserve_output(REPO/'.tmp')
        with TemporaryDirectory(dir=REPO/'.tmp') as folder:
            with self.assertRaises(FileExistsError):
                reserve_output(Path(folder))

    def test_no_output_overwrite(self):
        with TemporaryDirectory() as folder:
            path = Path(folder)/'frozen.json'
            write(path,{'policy':'old'})
            with self.assertRaises(FileExistsError):
                write(path,{'policy':'new'})
            self.assertEqual(json.loads(path.read_text()),{'policy':'old'})


if __name__ == '__main__':
    unittest.main()
