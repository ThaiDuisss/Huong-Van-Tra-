"""Next-order macro metrics and paired customer-cluster bootstrap."""
from collections import defaultdict
from dataclasses import asdict
from math import fsum, isclose
from statistics import median

import numpy as np

from offline_benchmark.metrics.ranking_metrics import ranking_metrics
from offline_benchmark.models.prefix_history import CONFIGURATIONS, MODES
from offline_benchmark.split.prefix_history import GROUPS


METRICS = ("recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5",
           "catalog_coverage_at_5")


def aggregate(rows, catalog_size):
    if not rows:
        return None
    n = len(rows)
    recommended = {product for row in rows for product in row["top5"]}
    total_hits = sum(len(row["hits"]) for row in rows)
    result = {key: fsum(row[key] for row in rows)/n
              for key in METRICS if key != "catalog_coverage_at_5"}
    result.update({"evaluated_cases": n, "unique_customers": len({r["customer_id"] for r in rows}),
                   "total_hits": total_hits, "mean_hits_per_case": total_hits/n,
                   "unique_recommended_products": len(recommended),
                   "recommendable_catalog_size": catalog_size,
                   "catalog_coverage_at_5": len(recommended)/catalog_size,
                   "returned_recommendation_slots": sum(len(r["top5"]) for r in rows),
                   "fixed_precision_denominator_slots": n*5,
                   "cases_with_fewer_than_five_recommendations": sum(len(r["top5"])<5 for r in rows)})
    if not isclose(result["precision_at_5"], total_hits/(n*5), abs_tol=1e-15):
        raise ValueError("Precision does not equal hits/(cases*5)")
    return result


def evaluate_prefix(cases, predictions, catalog_ids):
    if not catalog_ids or len(set(catalog_ids)) != len(catalog_ids):
        raise ValueError("Catalog must be nonempty and unique")
    catalog = set(catalog_ids)
    expected_ids = {case.case_id for case in cases}
    results = {}
    for mode in MODES:
        configurations = []
        for key, family, weights in CONFIGURATIONS:
            if set(predictions[mode][key]) != expected_ids:
                raise ValueError("Models must evaluate identical prefix cases")
            rows = []
            for case in cases:
                top5 = predictions[mode][key][case.case_id]
                relevant = tuple(sorted({event.product_id for event in case.target.events}))
                if not set(top5) <= catalog:
                    raise ValueError("Recommendation outside active catalog")
                if mode == MODES[1] and set(top5) & {event.product_id for event in case.events}:
                    raise ValueError("Purchased product returned in exclusion mode")
                values = asdict(ranking_metrics(top5, relevant))
                values["map_at_5"] = values.pop("ap_at_5")
                rows.append({"case_id": case.case_id, "customer_id": case.customer_id,
                             "history_group": case.group, "top5": top5,
                             "hits": tuple(p for p in top5 if p in relevant), **values})
            configurations.append({"configuration": key, "family": family,
                                   "weights_pop_content_cf": weights,
                                   "history_groups": {g: aggregate([r for r in rows if r["history_group"]==g], len(catalog))
                                                      for g in GROUPS},
                                   "overall": aggregate(rows, len(catalog)), "case_results": rows})
        results[mode] = {"exclude_purchased": mode == MODES[1], "configurations": configurations}
    return results


def select_best(configurations, group, family=None):
    eligible = [r for r in configurations if (family is None or r["family"]==family)
                and r["history_groups"][group] is not None]
    return min(eligible, key=lambda r: (-r["history_groups"][group]["ndcg_at_5"],
                                       -r["history_groups"][group]["map_at_5"],
                                       -r["history_groups"][group]["recall_at_5"],
                                       r["configuration"])) if eligible else None


def paired_cluster_bootstrap(rows_a, rows_b, samples=2000, seed=42):
    if samples < 100:
        raise ValueError("Bootstrap requires at least 100 resamples")
    if [r["case_id"] for r in rows_a] != [r["case_id"] for r in rows_b]:
        raise ValueError("Bootstrap requires paired identical case order")
    blocks = defaultdict(lambda: [0, 0.0, 0.0])
    for a, b in zip(rows_a, rows_b):
        block = blocks[a["customer_id"]]
        block[0] += 1
        block[1] += b["ndcg_at_5"]-a["ndcg_at_5"]
        block[2] += b["recall_at_5"]-a["recall_at_5"]
    if not blocks:
        return None
    data = np.array([blocks[c] for c in sorted(blocks)], dtype=float)
    rng = np.random.default_rng(seed)
    estimates = np.empty((samples, 2))
    for start in range(0, samples, 100):
        indices = rng.integers(len(data), size=(min(100, samples-start), len(data)))
        selected = data[indices].sum(axis=1)
        estimates[start:start+len(indices)] = selected[:, 1:]/selected[:, :1]
    return {"method": "paired customer-cluster percentile bootstrap; case-weighted metric",
            "confidence_level": 0.95, "resamples": samples, "seed": seed,
            "customer_clusters": len(blocks), "selection_bias_corrected": False,
            "intervals_b_minus_a": {metric: {"point_estimate": float(data[:, i+1].sum()/data[:, 0].sum()),
                                            "lower": float(np.quantile(estimates[:, i], 0.025)),
                                            "upper": float(np.quantile(estimates[:, i], 0.975))}
                                    for i, metric in enumerate(("ndcg_at_5", "recall_at_5"))}}


def compare_by_history(results, samples=2000):
    for mode in MODES:
        comparisons, winners = {}, {}
        configurations = results[mode]["configurations"]
        for group in GROUPS:
            a, b, best = (select_best(configurations, group, family) for family in ("A", "B", None))
            if best is None:
                winners[group], comparisons[group] = None, None
                continue
            winners[group] = {"configuration": best["configuration"], "family": best["family"],
                              "weights_pop_content_cf": best["weights_pop_content_cf"],
                              "aggregate": best["history_groups"][group]}
            rows_a = [r for r in a["case_results"] if r["history_group"]==group]
            rows_b = [r for r in b["case_results"] if r["history_group"]==group]
            comparisons[group] = {"a": a["configuration"], "b": b["configuration"],
                                  "deltas_b_minus_a": {metric: b["history_groups"][group][metric]-a["history_groups"][group][metric]
                                                       for metric in METRICS},
                                  "bootstrap": paired_cluster_bootstrap(rows_a, rows_b, samples)}
        results[mode]["best_by_history"] = winners
        results[mode]["a_vs_b_by_history"] = comparisons
    return results


def signal_analysis(cases, diagnostics):
    output = {}
    for mode in MODES:
        output[mode] = {}
        for group in ("1", "2"):
            subset = [diagnostics[c.case_id]["modes"][mode] for c in cases if c.group==group]
            result = {"evaluated_cases": len(subset)}
            for component in ("cf", "content"):
                positive = [r[component]["positive_candidates"] for r in subset]
                signals = sum(r[component]["positive_signal"] for r in subset)
                result.update({f"cases_with_positive_{component}_signal": signals,
                               f"cases_with_flat_{component}_scores": sum(r[component]["flat_scores"] for r in subset),
                               f"percentage_with_positive_{component}_signal": 100*signals/len(subset) if subset else None,
                               f"mean_positive_{component}_candidates": fsum(positive)/len(subset) if subset else None,
                               f"median_positive_{component}_candidates": median(positive) if positive else None})
            output[mode][group] = result
    return output
