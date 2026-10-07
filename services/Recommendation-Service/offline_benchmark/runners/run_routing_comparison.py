"""Read-only, canonical-split exploration of Content/CF versus Pop/Content/CF.

Production code and the shared split manifest are never changed by this runner.
Weights are compared on the existing holdout, not on a validation set.
"""
import argparse
from bisect import bisect_right
from collections import Counter
from dataclasses import asdict, replace
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.metadata
import json
from math import isclose, isfinite
from pathlib import Path
import sys

import numpy as np
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from app.models.interaction_event import InteractionEvent
from app.models.product import Product
from offline_benchmark.common.filtering import matching_hits, top_five
from offline_benchmark.common.manifest import (
    artifact_path, build_manifest, digest, require_manifest,
)
from offline_benchmark.common.types import PredictionReport, TrainingInput
from offline_benchmark.data.db_audit import TABLES
from offline_benchmark.data.db_loader import read_snapshot
from offline_benchmark.metrics.evaluator import evaluate_reports, test_fingerprint
from offline_benchmark.models.base import normalize_scores
from offline_benchmark.models.personalized_hybrid import PersonalizedHybrid
from offline_benchmark.models.popularity import Popularity
from offline_benchmark.split.chronological_split import (
    chronological_split, ordered_orders, verify_split,
)


# (popularity, content, CF): every requested grid point is fixed before evaluation.
CONFIGURATIONS = (
    ("A_25_75", "A", (0.0, 0.25, 0.75)),
    ("A_50_50", "A", (0.0, 0.50, 0.50)),
    ("A_75_25", "A", (0.0, 0.75, 0.25)),
    ("B_10_45_45", "B", (0.10, 0.45, 0.45)),
    ("B_20_40_40", "B", (0.20, 0.40, 0.40)),
    ("B_20_50_30", "B", (0.20, 0.50, 0.30)),
    ("B_30_40_30", "B", (0.30, 0.40, 0.30)),
    ("B_40_40_20", "B", (0.40, 0.40, 0.20)),
)
METRICS = (
    "recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5",
    "catalog_coverage_at_5",
)
GROUPS = ("1", "2", "3-4", ">=5")
SOURCE_ROOT = Path(__file__).resolve().parents[2]


def load_context(env_file):
    """Canonical projection, counts and display labels in one read-only snapshot."""
    from app.core.settings import Settings

    if env_file is not None and not env_file.is_file():
        raise ValueError("The selected application environment file is missing")
    settings = Settings(_env_file=env_file) if env_file is not None else Settings()
    engine = create_engine(settings.sqlalchemy_database_url, echo=False,
                           connect_args={"connect_timeout": 10})
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            connection.exec_driver_sql("SET TRANSACTION READ ONLY")
            connection.exec_driver_sql("START TRANSACTION WITH CONSISTENT SNAPSHOT")
            try:
                with Session(bind=connection, autoflush=False) as db:
                    snapshot = read_snapshot(db)
                    counts = {
                        model.__tablename__: int(db.scalar(select(func.count()).select_from(model)))
                        for model in TABLES
                    }
                    event_counts = {
                        row[0]: int(row[1]) for row in db.execute(
                            select(InteractionEvent.event_type, func.count())
                            .group_by(InteractionEvent.event_type)
                        )
                    }
                    labels = {
                        row.id: {"code": row.product_code, "name": row.name}
                        for row in db.execute(select(Product.id, Product.product_code, Product.name))
                    }
                    return snapshot, {
                        "database": settings.mysql_database, "table_counts": counts,
                        "event_type_counts": event_counts, "product_labels": labels,
                    }
            finally:
                connection.rollback()
    finally:
        engine.dispose()


def group_name(count):
    if count == 0:
        return "0"
    if count in (1, 2):
        return str(count)
    return "3-4" if 3 <= count <= 4 else ">=5"


def routed_scores(train_purchase_count, weights, popularity, content, collaborative):
    if (any(not isfinite(weight) or weight < 0 for weight in weights)
            or not isclose(sum(weights), 1.0, abs_tol=1e-12)):
        raise ValueError("Weights must be finite, nonnegative and sum to one")
    if train_purchase_count == 0:
        return dict(popularity)
    if train_purchase_count < 0:
        raise ValueError("TRAIN purchase count cannot be negative")
    wp, wc, wf = weights
    # For A, preserve the exact canonical blend arithmetic, including near ties.
    if wp == 0:
        return {product: wc * content[product] + wf * collaborative[product]
                for product in popularity}
    return {product: wp * popularity[product] + wc * content[product]
            + wf * collaborative[product] for product in popularity}


def fit_predict(products, splits):
    """No TEST labels are accessed here; counts, fit and routing use TRAIN only."""
    verify_split(splits)
    if len(products) < 5 or not splits:
        raise ValueError("Canonical evaluation requires customers and at least five products")
    training = TrainingInput(
        tuple(split.customer_id for split in splits), products,
        tuple(event for split in splits for event in split.train),
    )
    hybrid = PersonalizedHybrid(training)
    popularity = Popularity(training)
    raw_popularity = popularity.scores("")
    norm_popularity = normalize_scores(raw_popularity)
    purchase_counts = Counter(event.customer_id for event in training.purchases)
    predictions = {key: {} for key, _, _ in CONFIGURATIONS}
    components = {}
    diagnostics = {"flat_content_customers": 0, "flat_cf_customers": 0,
                   "zero_purchase_evaluated_customers": 0}
    canonical_equal = True
    for customer in training.customer_ids:
        raw_content = hybrid.content.scores(customer)
        raw_cf = hybrid.collaborative.scores(customer)
        content, collaborative = normalize_scores(raw_content), normalize_scores(raw_cf)
        components[customer] = {
            "train_purchase_events": purchase_counts[customer],
            "content": raw_content, "cf": raw_cf,
        }
        diagnostics["flat_content_customers"] += len(set(raw_content.values())) <= 1
        diagnostics["flat_cf_customers"] += len(set(raw_cf.values())) <= 1
        diagnostics["zero_purchase_evaluated_customers"] += purchase_counts[customer] == 0
        for key, _, weights in CONFIGURATIONS:
            scores = routed_scores(purchase_counts[customer], weights, norm_popularity,
                                   content, collaborative)
            predictions[key][customer] = top_five(scores)
        canonical_equal &= predictions["A_50_50"][customer] == top_five(hybrid.scores(customer))
    if not canonical_equal:
        raise ValueError("A 50/50 differs from the existing PersonalizedHybrid")
    # Functional check only: this is not an accuracy result for unseen customers.
    for _, _, weights in CONFIGURATIONS:
        if routed_scores(0, weights, norm_popularity, {}, {}) != norm_popularity:
            raise ValueError("The zero-TRAIN-purchase route does not return Popularity")
    # Probe whether one real TRAIN event can create scores using the audited code.
    event = next(e for e in training.purchases if e.product_id in hybrid.content.product_index)
    one_event_history = np.zeros(len(hybrid.content.product_ids))
    one_event_history[hybrid.content.product_index[event.product_id]] = 1.0
    single_content = hybrid.content.profile_scores(one_event_history)
    single_cf = hybrid.collaborative.score_map(
        np.asarray(one_event_history @ hybrid.collaborative.similarity).ravel())
    diagnostics["one_purchase_functional_probe"] = {
        "accuracy_evaluation": False, "source": "one real TRAIN event; fitted global TRAIN model",
        "product_id": event.product_id,
        "positive_content_products": sum(score > 0 for score in single_content.values()),
        "positive_cf_products": sum(score > 0 for score in single_cf.values()),
    }
    diagnostics.update({"canonical_50_50_rankings_equal": True,
                        "zero_purchase_route_functional_check": True,
                        "training_input_sha256": digest(asdict(training))})
    return predictions, top_five(raw_popularity), {
        "popularity": raw_popularity, "customers": components,
    }, diagnostics


def evaluate_configuration(key, family, weights, predictions, products, splits, popular_top5, labels):
    reports = tuple(PredictionReport(
        key, split.customer_id, predictions[split.customer_id],
        tuple(event.product_id for event in split.test),
        matching_hits(predictions[split.customer_id], tuple(event.product_id for event in split.test)),
    ) for split in splits)
    catalog_ids = tuple(product.product_id for product in products)
    result = evaluate_reports(reports, catalog_ids)
    result.update({"configuration": key, "family": family,
                   "weights_pop_content_cf": weights, "test_sha256": test_fingerprint(splits),
                   "customer_set_sha256": digest(sorted(predictions))})
    aggregate = result["aggregate"]
    count = len(splits)
    if any(len(report.top5) != 5 for report in reports):
        raise ValueError("A candidate failed to return exactly five unique recommendations")
    if not isclose(aggregate["precision_at_5"], aggregate["total_hits"] / (count * 5), abs_tol=1e-15):
        raise ValueError("Precision is inconsistent with total Hits / Top-5 slots")
    groups = {}
    for group in GROUPS:
        customers = {split.customer_id for split in splits if group_name(len(split.train)) == group}
        subset = tuple(report for report in reports if report.customer_id in customers)
        groups[group] = {
            "customers": len(subset),
            "warning": ("No customers in canonical cohort" if not subset
                        else "Fewer than 30 customers; exploratory small-sample result" if len(subset) < 30 else None),
            "aggregate": evaluate_reports(subset, catalog_ids)["aggregate"] if subset else None,
        }
    result["history_groups_purchase_events"] = groups
    frequency = Counter(product for report in reports for product in report.top5)
    sets = Counter(tuple(sorted(report.top5)) for report in reports)
    popular_set = set(popular_top5)
    overlap = sum(len(set(report.top5) & popular_set) for report in reports)
    result["popularity_domination"] = {
        "unique_recommended_products": len(frequency),
        "top10_recommended_products": [
            {"product_id": product, **labels.get(product, {}), "customer_appearances": appearances,
             "slot_share": appearances / (count * 5)}
            for product, appearances in sorted(frequency.items(), key=lambda row: (-row[1], row[0]))[:10]
        ],
        "globally_popular_top5_product_ids": popular_top5,
        "slots_in_globally_popular_top5": overlap,
        "slot_share_in_globally_popular_top5": overlap / (count * 5),
        "mean_overlap_with_popularity_top5": overlap / count,
        "exact_popularity_top5_set_customers": sum(set(report.top5) == popular_set for report in reports),
        "exact_popularity_top5_order_customers": sum(report.top5 == popular_top5 for report in reports),
        "distinct_top5_sets": len(sets),
        "most_common_top5_set_customers": max(sets.values()),
        "most_common_top5_set_customer_share": max(sets.values()) / count,
    }
    return result


def source_hashes():
    paths = list((SOURCE_ROOT / "offline_benchmark/models").glob("*.py"))
    paths += [SOURCE_ROOT / relative for relative in (
        "offline_benchmark/runners/run_routing_comparison.py",
        "offline_benchmark/runners/common.py", "offline_benchmark/data/db_loader.py",
        "offline_benchmark/metrics/evaluator.py", "offline_benchmark/metrics/ranking_metrics.py",
        "offline_benchmark/split/chronological_split.py", "app/training/dataset_builder.py",
        "app/training/training_config.py", "app/service/recommendation_service.py",
    )]
    return {path.relative_to(SOURCE_ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(paths)}


def select_best(results, family):
    # Predeclared reporting criterion. Holdout selection remains exploratory.
    return min((result for result in results if result["family"] == family), key=lambda result: (
        -result["aggregate"]["ndcg_at_5"], -result["aggregate"]["map_at_5"],
        -result["aggregate"]["recall_at_5"], result["configuration"],
    ))


def metric_table(results):
    lines = ["| Config | Pop / Content / CF | Recall@5 | Precision@5 | NDCG@5 | MAP@5 | Coverage@5 | Hits |",
             "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for result in results:
        a = result["aggregate"]
        weights = " / ".join(f"{value:.2f}" for value in result["weights_pop_content_cf"])
        lines.append(f"| {result['configuration']} | {weights} | {a['recall_at_5']:.2%} | "
                     f"{a['precision_at_5']:.2%} | {a['ndcg_at_5']:.6f} | {a['map_at_5']:.6f} | "
                     f"{a['catalog_coverage_at_5']:.2%} | {a['total_hits']} |")
    return "\n".join(lines)


def render_report(result):
    dataset, verification = result["dataset"], result["verification"]
    akey, bkey = result["best_comparison"]["a"], result["best_comparison"]["b"]
    best = [next(row for row in result["configurations"] if row["configuration"] == key)
            for key in (akey, bkey)]
    lines = ["# Routing comparison: executed runtime benchmark", "", "## A. Audit result", "",
        "Serving currently uses LightFM WARP (32 components, learning rate 0.05, 30 epochs, "
        "seed 42, one thread) for artifact users; missing artifact/user falls back to Popularity. "
        "It does not implement either proposed purchase-count routing policy.", "",
        "Offline Popularity counts TRAIN PURCHASE events per product, not purchased quantities. "
        "Content uses category plus active-BOM recipe-ingredient (type=1) binary features, deduplicated "
        "across variants, L2-normalized; a purchase-event-count-weighted user profile is normalized "
        "and scored by cosine similarity. CF is item-item cosine on the TRAIN user-product event-count "
        "matrix with a zero diagonal, scored by history @ similarity. CF has no stochastic seed or "
        "minimum-history fallback. A single purchased item can score other items if co-purchase "
        "support exists. A flat CF component normalizes to zero; weights are not reassigned.", "",
        "N(s)=(s-min)/(max-min) over the entire active catalog separately per component/customer; "
        "flat scores become zero. Existing Hybrid is 0.50*N(content)+0.50*N(CF). "
        "A uses wc*N(content)+wf*N(CF); B uses wp*N(pop)+wc*N(content)+wf*N(CF). "
        "For both, zero TRAIN PURCHASE events route to N(pop). Top-5 ties use ascending product ID.", "",
        "Production LightFM aggregation uses max(PURCHASE events - CHANGE_OF_MIND RETURN events,0), "
        "then 1+log1p(count); this canonical offline comparison uses PURCHASE events only. "
        "Neither model learns actual custom ingredient quantities.", "",
        "## B. Dataset / Evaluation protocol", ""]
    for key, value in dataset.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "Reuse the exact purchase-order-chronological-80-20-v2 manifest. "
        "Eligibility >=5 distinct PURCHASE orders is solely the existing evaluation contract. "
        "No business routing threshold of 2/3/5 is applied. Whole orders sorted by "
        "(MIN(event_time),order_id); oldest floor(4*n/5) orders TRAIN, remainder TEST. "
        "Fit uses all eligible TRAIN rows. Test relevance is distinct product IDs per customer, "
        "including unavailable items; no difficult customers are removed.", "",
        "Canonical exclude_purchased=false: repeats are allowed. Production serving excludes every "
        "historically purchased product, including products later returned. This report preserves "
        "the canonical benchmark filter; it is not a measurement of the production exclusion policy.", "",
        "Hits=sum_u |Top5(u) intersect unique TEST products(u)|. Precision uses five slots/customer; "
        "Recall uses each customer's unique relevant-item count. NDCG uses binary relevance; "
        "AP divides by min(5,relevant count). Ranking metrics are macro averages; coverage is "
        "the Top-5 union / active catalog. Precision=Hits/(customers*5) was checked for every config.", "",
        "No validation set exists in the canonical protocol. All eight grid results are exploratory "
        "holdout results; the highest score is not production-optimal. Best is selected by NDCG, "
        "then MAP, then Recall, then configuration key, fixed before execution.", "",
        "No TEST events/orders enter any model or routing input; TEST-label mutation and repeated "
        "fits preserve all rankings. However pooled per-user TRAIN histories can contain another "
        "customer's purchases later than the target's first TEST date. Current catalog metadata is "
        "also retrospective. Reusing this canonical protocol does not prove a globally time-causal "
        "production backtest.", "", "## C. Candidate A results", "",
        metric_table([row for row in result["configurations"] if row["family"] == "A"]), "",
        "## D. Candidate B results", "",
        metric_table([row for row in result["configurations"] if row["family"] == "B"]), "",
        "## E. History-size breakdown", "",
        "Unit: number of TRAIN PURCHASE events (order-item interactions); not orders, distinct "
        "products, or purchased units. JSON includes every configuration's group results.", "",
        "| History | Config | Customers | Recall@5 | Precision@5 | NDCG@5 | MAP@5 | Coverage@5 | Hits |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for group in GROUPS:
        for row in best:
            g = row["history_groups_purchase_events"][group]
            m = g["aggregate"]
            metrics = (f"{m['recall_at_5']:.2%} | {m['precision_at_5']:.2%} | {m['ndcg_at_5']:.6f} | "
                       f"{m['map_at_5']:.6f} | {m['catalog_coverage_at_5']:.2%} | {m['total_hits']}"
                       if m else "N/A | N/A | N/A | N/A | N/A | N/A")
            lines.append(f"| {group} | {row['configuration']} | {g['customers']} | {metrics} |")
    lines.extend(["", "Groups 1/2 cannot occur under canonical eligibility; 3-4 contains only "
                  "customers with four TRAIN events and is very small. Zero-purchase accuracy is "
                  "not evaluated. No synthetic accuracy numbers are substituted.", "",
                  "## F. Best A vs Best B", "", metric_table(best), "",
                  "| Metric | B - A |", "| --- | ---: |"])
    for key, value in result["best_comparison"]["deltas"].items():
        delta = f"{100*value:+.4f} pp" if key in ("recall_at_5", "precision_at_5", "catalog_coverage_at_5") else f"{value:+.6f}"
        lines.append(f"| {key} | {delta} |")
    lines.extend(["", "All configs return the same number of slots. Additional counters:", "",
                  "| Config | Customers | Slots | Hits | Mean hits | Unique / catalog |",
                  "| --- | ---: | ---: | ---: | ---: | ---: |"])
    for row in result["configurations"]:
        m = row["aggregate"]
        lines.append(f"| {row['configuration']} | {m['evaluated_customers']} | "
                     f"{m['total_top5_recommendation_slots']} | {m['total_hits']} | "
                     f"{m['mean_hits_per_customer']:.6f} | {m['unique_recommended_products']} / "
                     f"{m['recommendable_catalog_products']} |")
    lines.extend(["", "Pure Popularity baseline:", "", metric_table([result["popularity_baseline"]]), "",
        "## G. Popularity domination check", "",
        "| B config | Unique items | Popular Top-5 slot share | Mean overlap / 5 | Exact Pop sets | Distinct sets | Most common set share |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for row in result["configurations"]:
        if row["family"] != "B":
            continue
        d = row["popularity_domination"]
        lines.append(f"| {row['configuration']} | {d['unique_recommended_products']} | "
                     f"{d['slot_share_in_globally_popular_top5']:.2%} | "
                     f"{d['mean_overlap_with_popularity_top5']:.4f} | "
                     f"{d['exact_popularity_top5_set_customers']} | {d['distinct_top5_sets']} | "
                     f"{d['most_common_top5_set_customer_share']:.2%} |")
    lines.extend(["", f"Top 10 appearances for selected {bkey} (all B configurations are in JSON):", "",
        "| Product code | Name | Customer appearances | Slot share |",
        "| --- | --- | ---: | ---: |"])
    for row in best[1]["popularity_domination"]["top10_recommended_products"]:
        name = row.get("name", "").replace("|", "\\|").replace("\n", " ")
        lines.append(f"| {row.get('code', row['product_id'])} | {name} | "
                     f"{row['customer_appearances']} | {row['slot_share']:.2%} |")
    a, b = (row["aggregate"] for row in best)
    domination = best[1]["popularity_domination"]
    lines.extend(["", "## H. Recommendation", "",
        f"On this canonical cohort, the NDCG-selected {bkey} scores NDCG={b['ndcg_at_5']:.6f} "
        f"and MAP={b['map_at_5']:.6f}, versus {akey}'s NDCG={a['ndcg_at_5']:.6f} "
        f"and MAP={a['map_at_5']:.6f}. Hits change from {a['total_hits']} to {b['total_hits']}; "
        f"coverage changes from {a['catalog_coverage_at_5']:.2%} to {b['catalog_coverage_at_5']:.2%}. "
        "Use the full five-metric comparison above to assess that trade-off.", "",
        f"Selected B has {domination['slot_share_in_globally_popular_top5']:.2%} of slots in "
        f"the globally popular Top-5; {domination['exact_popularity_top5_set_customers']} customers "
        f"receive exactly that set. Its most common set reaches "
        f"{domination['most_common_top5_set_customer_share']:.2%} of customers. "
        "Check the entire B grid above: larger Popularity weights can concentrate recommendations "
        "and sacrifice coverage even when ranking metrics improve.", "",
        "These are exploratory holdout comparisons with selection bias, not production-optimal "
        "weights or a significance claim. No evidence for one/two-purchase or zero-purchase "
        "accuracy is available; the 3-4 group is too small for a reliable sparse-history conclusion. "
        "Production routing is unchanged. Full numeric deltas and paired wins/ties/losses "
        "are retained in JSON.", "", "Verification:", ""])
    for key, value in verification.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "## I. Files changed", "",
        "Added offline_benchmark/runners/run_routing_comparison.py and offline_benchmark/ROUTING_COMPARISON.md. "
        "Generated outputs: routing_comparison.json, routing_component_scores.json, ROUTING_COMPARISON.md. "
        "Existing model/split/metric implementations and production files were not modified.", "",
        "## J. Commands to reproduce", "", "```powershell",
        "Set-Location 'E:\\Huong-Van-Tra-\\services\\Recommendation-Service'",
        "& '.\\.venv\\Scripts\\python.exe' -B -m offline_benchmark.runners.prepare_split --env-file recommend.env",
        "& '.\\.venv\\Scripts\\python.exe' -B -m offline_benchmark.runners.run_routing_comparison --env-file recommend.env",
        "& '.\\.venv\\Scripts\\python.exe' -B -m unittest discover -s offline_benchmark/tests -v",
        "```", "", "Tailscale must reach the endpoint configured in recommend.env. "
        "prepare_split stops on a changed existing manifest; do not refresh it silently."])
    return "\n".join(lines) + "\n"


def run(env_file):
    hashes_before = source_hashes()
    snapshot, before = load_context(env_file)
    splits = chronological_split(snapshot.purchases)
    manifest = build_manifest(snapshot, splits)
    require_manifest(manifest)
    print(f"Canonical dataset verified: {len(splits)} customers / {len(snapshot.products)} products", flush=True)
    predictions, popular_top5, components, diagnostics = fit_predict(snapshot.products, splits)
    repeated, repeated_pop, repeated_components, repeated_diagnostics = fit_predict(snapshot.products, splits)
    if ((predictions, popular_top5, components, diagnostics)
            != (repeated, repeated_pop, repeated_components, repeated_diagnostics)):
        raise ValueError("Repeated fits are not exactly deterministic")
    mutated = tuple(replace(split, test=tuple(replace(event, product_id="holdout-only-marker")
                                              for event in split.test)) for split in splits)
    changed, changed_pop, changed_components, changed_diagnostics = fit_predict(snapshot.products, mutated)
    if ((predictions, popular_top5, components, diagnostics)
            != (changed, changed_pop, changed_components, changed_diagnostics)):
        raise ValueError("TEST labels changed predictions, model inputs or routing")
    results = tuple(evaluate_configuration(
        key, family, weights, predictions[key], snapshot.products, splits, popular_top5, before["product_labels"],
    ) for key, family, weights in CONFIGURATIONS)
    if len({(row["test_sha256"], row["customer_set_sha256"]) for row in results}) != 1:
        raise ValueError("Candidates differ in TEST or customer population")
    pop_predictions = {split.customer_id: popular_top5 for split in splits}
    baseline = evaluate_configuration("Popularity", "baseline", (1.0, 0.0, 0.0), pop_predictions,
                                      snapshot.products, splits, popular_top5, before["product_labels"])
    best_a, best_b = select_best(results, "A"), select_best(results, "B")
    paired = {}
    for metric, user_metric in (("recall_at_5", "recall_at_5"), ("precision_at_5", "precision_at_5"),
                                ("ndcg_at_5", "ndcg_at_5"), ("map_at_5", "ap_at_5")):
        delta = [b[user_metric] - a[user_metric] for a, b in zip(best_a["customers"], best_b["customers"])]
        paired[metric] = {"b_wins": sum(d > 1e-12 for d in delta),
                          "ties": sum(abs(d) <= 1e-12 for d in delta),
                          "a_wins": sum(d < -1e-12 for d in delta)}
    train_times = sorted(event.event_time for split in splits for event in split.train)
    lookahead = [len(train_times) - bisect_right(train_times, min(e.event_time for e in split.test))
                 for split in splits]
    dataset = {
        "database": before["database"],
        "total_customers_with_purchase_history": manifest["total_customer_count"],
        "customer_registry_total": "Unavailable in Recommendation DB; zero-purchase customers not counted",
        "total_purchase_orders": len({event.order_id for event in snapshot.purchases}),
        "total_interaction_events": before["table_counts"]["interaction_events"],
        "purchase_events": len(snapshot.purchases), "all_event_types": before["event_type_counts"],
        "eligible_evaluated_customers": len(splits),
        "train_orders": manifest["train_order_count"], "test_orders": manifest["test_order_count"],
        "train_purchase_events": manifest["train_event_count"], "test_purchase_events": manifest["test_event_count"],
        "test_relevant_items_sum_customer_unique": sum(len({e.product_id for e in split.test}) for split in splits),
        "recommendable_catalog_products": len(snapshot.products),
        "total_top5_slots_per_configuration": len(splits) * 5,
        "train_purchase_event_group_counts": {group: sum(group_name(len(s.train)) == group for s in splits) for group in GROUPS},
        "train_order_group_counts": {group: sum(group_name(len(ordered_orders(s.train))) == group for s in splits) for group in GROUPS},
        "customers_with_other_customer_train_events_after_first_test_time": sum(n > 0 for n in lookahead),
        "split_sha256": manifest["split_sha256"], "test_sha256": test_fingerprint(splits),
        "exclude_purchased": False,
    }
    final_snapshot, after = load_context(env_file)
    if before != after or build_manifest(final_snapshot, chronological_split(final_snapshot.purchases)) != manifest:
        raise ValueError("DB history, catalog, labels or counts changed during the run; no results published")
    if source_hashes() != hashes_before:
        raise ValueError("Audited source files changed during the run; no results published")
    result = {
        "executed_at_asia_saigon": datetime.now(timezone(timedelta(hours=7))).isoformat(),
        "protocol": "exploratory canonical holdout; no validation split; no production behavior changes",
        "best_selection": "NDCG@5 descending, MAP@5 descending, Recall@5 descending, key ascending",
        "normalization": "per component/customer full-catalog min-max; flat component -> zero",
        "routing_history_unit": "TRAIN PURCHASE event count; zero -> Popularity, >=1 -> candidate",
        "split": manifest, "dataset": dataset, "source_sha256": hashes_before,
        "versions": {package: importlib.metadata.version(package) for package in ("numpy", "scipy", "SQLAlchemy", "PyMySQL", "pydantic-settings")},
        "configurations": results, "popularity_baseline": baseline,
        "best_comparison": {"a": best_a["configuration"], "b": best_b["configuration"],
                            "deltas": {metric: best_b["aggregate"][metric] - best_a["aggregate"][metric] for metric in METRICS},
                            "paired_customer_outcomes": paired},
        "verification": {**diagnostics, "all_grid_predictions_repeated_fit_exact": True,
                         "all_test_labels_mutated_predictions_unchanged": True,
                         "identical_customer_and_test_sets_all_configurations": True,
                         "precision_equals_hits_over_customers_times_five": True,
                         "db_counts_history_catalog_labels_unchanged": True,
                         "model_split_metric_production_sources_unchanged": True,
                         "random_seed": "Not applicable: deterministic cosine/count models; no sampling",
                         "globally_time_causal_protocol": False},
        "db_before": before["table_counts"], "db_after": after["table_counts"],
        "component_scores_sha256": digest(components),
    }
    # Publish only after every snapshot/source/invariance check has passed.
    artifact_path("routing_component_scores.json").write_text(
        json.dumps({"split": manifest, "components": components}, indent=2, allow_nan=False), encoding="utf-8")
    artifact_path("routing_comparison.json").write_text(
        json.dumps(result, indent=2, allow_nan=False), encoding="utf-8")
    artifact_path("ROUTING_COMPARISON.md").write_text(render_report(result), encoding="utf-8")
    print(metric_table(results), flush=True)
    print("Best:", best_a["configuration"], "vs", best_b["configuration"], flush=True)
    print("Deltas B-A:", json.dumps(result["best_comparison"]["deltas"]), flush=True)
    print("Repeated fits, TEST-mutation, common TEST, canonical weights, READ ONLY DB checks: PASS", flush=True)
    print("Report: offline_benchmark/outputs/ROUTING_COMPARISON.md", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, help="Existing application env file; no credential fallback")
    args = parser.parse_args()
    try:
        run(args.env_file)
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check DB access/schema and dependencies"
        print(f"Routing benchmark stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
