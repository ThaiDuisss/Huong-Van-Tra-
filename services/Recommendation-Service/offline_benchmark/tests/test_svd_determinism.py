from dataclasses import replace
import random
import unittest
from unittest.mock import patch

import numpy as np

from offline_benchmark.common.filtering import top_five
from offline_benchmark.common.types import TrainingInput
from offline_benchmark.models.svd import SVD, stable_svd_scores
from offline_benchmark.runners.common import evaluate
from offline_benchmark.split.chronological_split import chronological_split
from offline_benchmark.tests.test_benchmark import fixture


def fixture_training():
    snapshot = fixture()
    splits = chronological_split(snapshot.purchases)
    return TrainingInput(tuple(s.customer_id for s in splits), snapshot.products,
                         tuple(event for s in splits for event in s.train))


class SVDDeterminismTests(unittest.TestCase):
    def test_one_hundred_repeated_fits_have_identical_ordered_top5(self):
        training = fixture_training()
        results = [tuple(top_five(SVD(training).scores(user)) for user in training.customer_ids)
                   for _ in range(100)]
        self.assertEqual(len(set(results)), 1)
        self.assertEqual(results[0][0], ("p0", "p1", "p2", "p3", "p4"))
        self.assertEqual(results[0][1], ("p4", "p5", "p6", "p7", "p0"))

    def test_numerical_ties_use_product_id_independent_of_iteration_order(self):
        pairs = (("p1", 0.5000000000000002), ("p0", 0.5), ("p2", 0.5))
        expected = ("p0", "p1", "p2")
        self.assertEqual(top_five(stable_svd_scores(dict(pairs))), expected)
        self.assertEqual(top_five(stable_svd_scores(dict(reversed(pairs)))), expected)

    def test_clearly_different_scores_keep_score_order(self):
        self.assertEqual(top_five(stable_svd_scores({"p0": 0.5, "p1": 0.50000001})), ("p1", "p0"))

    def test_absolute_near_zero_and_relative_large_score_tolerances(self):
        self.assertEqual(top_five(stable_svd_scores({"p0": 0., "p1": 2e-16})), ("p0", "p1"))
        self.assertEqual(top_five(stable_svd_scores({"p0": 1000., "p1": 1000. + 2e-10})), ("p0", "p1"))
        self.assertEqual(top_five(stable_svd_scores({"p0": 1000., "p1": 1000. + 1e-6})), ("p1", "p0"))

    def test_anchored_ties_do_not_chain_different_scores(self):
        scores = {"a": 0., "b": 0.75e-12, "c": 1.5e-12}
        self.assertEqual(top_five(stable_svd_scores(scores)), ("b", "c", "a"))

    def test_no_nonfinite_scores_or_duplicate_candidates(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.assertRaises(ValueError):
                stable_svd_scores({"p0": value})
        ranking = top_five(SVD(fixture_training()).scores("bob"))
        self.assertEqual(len(ranking), len(set(ranking)))

    def test_fit_receives_only_train_and_unchanged_test_does_not_affect_ranking(self):
        snapshot = fixture()
        splits = chronological_split(snapshot.purchases)
        changed = tuple(replace(s, test=tuple(replace(e, product_id="future-only") for e in s.test)) for s in splits)
        actual_inputs = []
        original = SVD.__init__

        def recording_init(model, training):
            actual_inputs.append(training)
            original(model, training)

        with patch.object(SVD, "__init__", recording_init):
            first = evaluate("svd", snapshot.products, splits)
            second = evaluate("svd", snapshot.products, changed)
        self.assertEqual([r.top5 for r in first], [r.top5 for r in second])
        self.assertEqual(actual_inputs[0], actual_inputs[1])
        holdout_ids = {e.event_id for s in splits for e in s.test}
        self.assertFalse(holdout_ids & {e.event_id for e in actual_inputs[0].purchases})

    def test_changing_train_can_change_ranking(self):
        training = fixture_training()
        changed = replace(training, purchases=tuple(replace(e, product_id="p7") for e in training.purchases))
        self.assertNotEqual(top_five(SVD(training).scores("alice")), top_five(SVD(changed).scores("alice")))

    def test_global_random_state_does_not_affect_or_get_modified_by_fitting(self):
        training = fixture_training()
        old_python, old_numpy = random.getstate(), np.random.get_state()
        try:
            rankings = []
            for seed in range(20):
                random.seed(seed)
                np.random.seed(seed)
                python_before, numpy_before = random.getstate(), np.random.get_state()
                model = SVD(training)
                rankings.append(top_five(model.scores("bob")))
                self.assertEqual(random.getstate(), python_before)
                numpy_after = np.random.get_state()
                self.assertEqual(numpy_after[0], numpy_before[0])
                np.testing.assert_array_equal(numpy_after[1], numpy_before[1])
                self.assertEqual(numpy_after[2:], numpy_before[2:])
            self.assertEqual(len(set(rankings)), 1)
        finally:
            random.setstate(old_python)
            np.random.set_state(old_numpy)

    def test_solver_rank_and_start_are_explicit_and_reproducible(self):
        from scipy.sparse.linalg import svds

        with patch("offline_benchmark.models.svd.svds", wraps=svds) as solver:
            SVD(fixture_training())
        args = solver.call_args.kwargs
        self.assertEqual(args["k"], 1)
        self.assertEqual(args["solver"], "arpack")
        self.assertEqual(args["which"], "LM")
        self.assertEqual(args["tol"], 0)
        self.assertEqual(args["random_state"], 42)
        self.assertAlmostEqual(np.linalg.norm(args["v0"]), 1)
        self.assertGreater(np.ptp(args["v0"]), 0)


if __name__ == "__main__":
    unittest.main()
