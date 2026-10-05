import json


def render_aggregate(result: dict) -> str:
    a = result["aggregate"]
    return "\n".join((
        f"MODEL: {result['model']}",
        f"Evaluated customers: {a['evaluated_customers']}",
        f"Total Top-5 recommendation slots: {a['total_top5_recommendation_slots']}",
        f"Total Hits: {a['total_hits']}",
        f"Mean Hits per Customer: {a['mean_hits_per_customer']:.6f}",
        f"Unique Recommended Products: {a['unique_recommended_products']}",
        f"Recommendable Catalog Products: {a['recommendable_catalog_products']}",
        f"Recall@5: {a['recall_at_5']:.2%}",
        f"Precision@5: {a['precision_at_5']:.2%}",
        f"NDCG@5: {a['ndcg_at_5']:.6f}",
        f"MAP@5: {a['map_at_5']:.6f}",
        f"Catalog Coverage@5: {a['catalog_coverage_at_5']:.2%} "
        f"({a['unique_recommended_products']}/{a['recommendable_catalog_products']})",
    ))


def render_customer(row: dict) -> str:
    return "\n".join((
        f"MODEL: {row['model']}", f"Customer: {row['customer_id']}",
        "Top-5:", json.dumps(row["top5"]),
        "Test:", json.dumps(row["test"]),
        "Hits:", json.dumps(row["hits"]),
        f"Precision@5: {row['precision_at_5']:.6f}",
        f"Recall@5: {row['recall_at_5']:.6f}",
        f"NDCG@5: {row['ndcg_at_5']:.6f}",
        f"AP@5: {row['ap_at_5']:.6f}",
    ))


def build_summary(results: tuple[dict, ...]) -> dict:
    if len(results) != 6 or len({result["model_key"] for result in results}) != 6:
        raise ValueError("Comparison requires all six models")
    from offline_benchmark.metrics.evaluator import MODEL_NAMES

    if {result["model_key"] for result in results} != set(MODEL_NAMES):
        raise ValueError("Comparison contains an unexpected model")
    baseline = results[0]
    for result in results:
        for field in ("split", "test_sha256", "customer_set_sha256", "catalog_product_ids"):
            if result[field] != baseline[field]:
                raise ValueError("Comparison models do not share the same split, customers, TEST, and catalog")
        if result["aggregate"]["evaluated_customers"] != baseline["aggregate"]["evaluated_customers"]:
            raise ValueError("Comparison customer counts differ")
    return {
        "split": baseline["split"],
        "test_sha256": baseline["test_sha256"],
        "customer_set_sha256": baseline["customer_set_sha256"],
        "catalog_product_ids": baseline["catalog_product_ids"],
        "models": [
            {"model_key": result["model_key"], "model": result["model"],
             "prediction_sha256": result["prediction_sha256"], **result["aggregate"]}
            for result in sorted(results, key=lambda result: list(MODEL_NAMES).index(result["model_key"]))
        ],
        "sanity_checks": "PASS: complete common cohort, shared split/TEST/catalog, unique Top-5, "
                         "exact Hits, finite metrics in [0,1], coverage numerator <= denominator",
    }


def render_summary(summary: dict) -> str:
    lines = [
        "# Offline benchmark results", "",
        f"Total customers: {summary['split']['total_customer_count']}; "
        f"excluded customers: {summary['split']['excluded_customer_count']}.",
        f"Eligible/evaluated customers: {summary['split']['eligible_customer_count']}; "
        f"TRAIN events: {summary['split']['train_event_count']}; "
        f"TEST events: {summary['split']['test_event_count']}.", "",
        f"Eligible orders: {summary['split']['eligible_order_count']}; "
        f"TRAIN orders: {summary['split']['train_order_count']}; "
        f"TEST orders: {summary['split']['test_order_count']}.",
        f"TRAIN/TEST order ratios: {summary['split']['train_order_ratio']:.4%} / "
        f"{summary['split']['test_order_ratio']:.4%}; "
        f"event ratios: {summary['split']['train_event_ratio']:.4%} / "
        f"{summary['split']['test_event_ratio']:.4%}.", "",
        "Eligibility requires >=5 distinct PURCHASE order IDs per customer. "
        "Whole orders are sorted by (MIN(event_time), order_id); oldest floor(80%) train.",
        "All events of an order remain together. Different orders may share a timestamp. "
        "Event ratios need not equal order ratios.", "",
        f"Split fingerprint: `{summary['split']['split_sha256']}`", "",
        "| Model | Recall@5 | Precision@5 | NDCG@5 | MAP@5 | Catalog Coverage@5 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in summary["models"]:
        lines.append(f"| {row['model']} | {row['recall_at_5']:.2%} | {row['precision_at_5']:.2%} | "
                     f"{row['ndcg_at_5']:.6f} | {row['map_at_5']:.6f} | "
                     f"{row['catalog_coverage_at_5']:.2%} "
                     f"({row['unique_recommended_products']}/{row['recommendable_catalog_products']}) |")
    lines.extend([
        "", "All ranking metrics are macro averages over customers with binary product relevance.",
        "Repeated TEST products count once for relevance. TEST products outside the current catalog remain relevant.",
        "Precision uses denominator five even for shorter recommendation lists.",
        "Coverage uses the current ACTIVE/nondeleted product universe read from the shared DB.",
        "", "Repeat purchases are allowed. Cold-start uses a global TRAIN content prior on the eligible cohort,",
        "rather than a holdout cohort of previously unseen customers. Current catalog metadata is used.",
        "Retrieval, ranking quality, and catalog coverage measure different aspects; no overall winner is assigned.",
        "", f"Sanity checks: {summary['sanity_checks']}",
        "Full-precision aggregates are in benchmark_summary.json; per-customer metrics are in each *_metrics.json.",
    ])
    return "\n".join(lines) + "\n"
