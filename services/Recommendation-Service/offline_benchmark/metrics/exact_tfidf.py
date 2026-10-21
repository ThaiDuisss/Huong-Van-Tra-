"""Chronological policy selection and unchanged binary relevance metrics."""
from dataclasses import asdict, dataclass
from datetime import datetime

from offline_benchmark.metrics.prefix_history import aggregate, paired_cluster_bootstrap
from offline_benchmark.metrics.ranking_metrics import ranking_metrics


GROUPS = ("0", "1", "2-4", ">=5", "2", "3", "4", "5-9", ">=10")
THRESHOLDS = (2, 3, 4, 5, 6, 8, 10)


@dataclass(frozen=True, slots=True)
class Policy:
    key: str
    low: str
    high: str
    threshold: int
    family: str

    def model(self, orders):
        if orders == 0:
            return "Popularity"
        return self.low if orders < self.threshold else self.high


CURRENT = Policy("Current_Pop4_Hybrid60_5", "Popularity", "Hybrid60", 5, "current")


def policies():
    from offline_benchmark.models.exact_tfidf import MODELS
    result = [Policy(m, m, m, 1, "constant") for m in MODELS] + [CURRENT]
    for threshold in THRESHOLDS:
        for hybrid in ("Hybrid50", "Hybrid60"):
            result.extend((Policy(f"Three_Content_{hybrid}_{threshold}", "Content", hybrid, threshold, "three"),
                           Policy(f"Simple_Pop_{hybrid}_{threshold}", "Popularity", hybrid, threshold, "simple")))
    return tuple(result)


def group_matches(orders, group):
    if group == "2-4":
        return 2 <= orders <= 4
    if group == ">=5":
        return orders >= 5
    if group == "5-9":
        return 5 <= orders <= 9
    if group == ">=10":
        return orders >= 10
    return orders == int(group)


def metric_rows(predictions):
    output = {}
    for model, entries in predictions.items():
        rows = []
        for case, relevant, result in entries:
            values = asdict(ranking_metrics(result.top5, relevant))
            values['map_at_5'] = values.pop('ap_at_5')
            history = {e.product_id for e in case.events}
            # Reachability includes exact candidates, not just the returned Top-5.
            rows.append({"case_id": case.case_id, "customer_id": case.customer_id,
                "history_orders": case.history_order_count, "top5": result.top5,
                "hits": tuple(p for p in result.top5 if p in relevant),
                "relevant": relevant, "purchased": tuple(sorted(history)),
                "candidate_count": result.candidate_count, "fallback_reason": result.fallback_reason,
                "source": result.source, "content_flat": result.content_flat, "cf_flat": result.cf_flat,
                **values})
        output[model] = rows
    return output


def policy_rows(rows, policy):
    if not rows:
        return []
    aligned = list(rows.values())
    expected = [r['case_id'] for r in aligned[0]]
    if any([r['case_id'] for r in model] != expected for model in aligned):
        raise ValueError("Policies require identical ordered evaluation cases")
    return [rows[policy.model(row['history_orders'])][i] for i, row in enumerate(aligned[0])]


def summarize(rows, catalog, mode):
    def subset_summary(subset):
        result = aggregate(subset, len(catalog))
        if result is None:
            return None
        unreachable = 0
        for r in subset:
            candidates = set(catalog) - (set(r['purchased']) if mode == 'discovery' else set())
            if r['candidate_count'] != len(candidates):
                raise ValueError("Candidate count differs between model contracts")
            if not set(r['top5']) <= candidates:
                raise ValueError("Ranking violates shared candidate eligibility")
            unreachable += not bool(set(r['relevant']) & candidates)
        n = len(subset)
        result.update(unreachable_target_cases=unreachable,
            candidate_shortage_cases=sum(r['candidate_count'] < 5 for r in subset),
            empty_candidate_cases=sum(r['candidate_count'] == 0 for r in subset),
            content_flat_cases=sum(r['content_flat'] for r in subset),
            cf_flat_cases=sum(r['cf_flat'] for r in subset),
            fallback_cases=sum(r['fallback_reason'] is not None for r in subset),
            fallback_rate=sum(r['fallback_reason'] is not None for r in subset)/n,
            content_flat_rate=sum(r['content_flat'] for r in subset)/n,
            cf_flat_rate=sum(r['cf_flat'] for r in subset)/n)
        return result
    return {'overall': subset_summary(rows),
            'groups': {g: subset_summary([r for r in rows if group_matches(r['history_orders'], g)]) for g in GROUPS}}


def select(validation, catalog, mode):
    """No final-test parameter. Tie rule fixed before accessing holdout metrics."""
    summaries = {p.key: summarize(policy_rows(validation, p), catalog, mode) for p in policies()}
    if not next(iter(summaries.values()))['overall']:
        raise ValueError("Validation window has no cases")
    def best(candidates):
        return min(candidates, key=lambda p: (
            -summaries[p.key]['overall']['ndcg_at_5'], -summaries[p.key]['overall']['map_at_5'],
            -summaries[p.key]['overall']['recall_at_5'], p.key))
    grid = policies()
    winners = {'overall': best(grid), 'three': best([p for p in grid if p.family == 'three']),
               'simple': best([p for p in grid if p.family in ('simple', 'constant', 'current')]),
               'weight_only': best([p for p in grid if p.key in ('Hybrid50', 'Hybrid60')])}
    return winners, summaries


def comparisons(rows, winners, samples=2000):
    output = {}
    for name in ('overall', 'three', 'simple', 'weight_only'):
        chosen = policy_rows(rows, winners[name])
        baseline = policy_rows(rows, CURRENT)
        output[name] = {g: paired_cluster_bootstrap(
            [r for r in baseline if g == 'overall' or group_matches(r['history_orders'], g)],
            [r for r in chosen if g == 'overall' or group_matches(r['history_orders'], g)], samples)
            for g in ('overall', '1', '2-4', '>=5')}
    output['weight_60_minus_50'] = paired_cluster_bootstrap(rows['Hybrid50'], rows['Hybrid60'], samples)
    output['three_minus_simple'] = paired_cluster_bootstrap(
        policy_rows(rows, winners['simple']), policy_rows(rows, winners['three']), samples)
    output['content_minus_popularity'] = {g: paired_cluster_bootstrap(
        [r for r in rows['Popularity'] if group_matches(r['history_orders'], g)],
        [r for r in rows['Content'] if group_matches(r['history_orders'], g)], samples)
        for g in ('1', '2-4', '>=5')}
    return output


def windows(cases, validation_start=None, test_start=None):
    times = sorted({c.prediction_time for c in cases})
    if len(times) < 3:
        raise ValueError("Need at least three distinct cutoffs for global windows")
    validation_start = datetime.fromisoformat(validation_start) if validation_start else times[3*len(times)//5]
    test_start = datetime.fromisoformat(test_start) if test_start else times[4*len(times)//5]
    if not times[0] < validation_start < test_start <= times[-1]:
        raise ValueError("Global window boundaries must be ordered and nonempty")
    partitions = {'warmup': tuple(c for c in cases if c.prediction_time < validation_start),
        'validation': tuple(c for c in cases if validation_start <= c.prediction_time < test_start),
        'test': tuple(c for c in cases if c.prediction_time >= test_start)}
    if any(not c for c in partitions.values()):
        raise ValueError("Each global window requires cases")
    return partitions, {'validation_start_inclusive': validation_start.isoformat(),
        'final_test_start_inclusive': test_start.isoformat(),
        'default_rule': '60/80 percentiles of UNIQUE cutoff timestamps; timestamps only, no relevance labels',
        'training': 'Rolling observed complete orders strictly before every cutoff, including past test-period events',
        'policy_selection': 'Validation NDCG, then MAP, Recall, lexical policy key; freeze before test evaluation'}
