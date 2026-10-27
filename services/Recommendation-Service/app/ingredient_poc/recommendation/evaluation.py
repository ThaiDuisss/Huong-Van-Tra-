"""One-model sanity evaluation. Frozen split boundaries; no weight search."""
from collections import Counter, defaultdict
import math

from app.ingredient_poc.synthetic import END, T1, T2


def demos(model):
    data = model.data
    result = {}
    for cohort, mode in (("PRODUCT_ONLY", "CUSTOM_BOM"), ("BOM_ONLY", "PRODUCT"), ("MIXED", "UNIFIED")):
        candidates = [c for c in sorted(data.customers) if data.cohort(c, T2) == cohort]
        if not candidates:
            raise ValueError("missing demo history cohort")
        customer = candidates[0]
        result[cohort] = model.recommend(customer, as_of=T2, mode=mode, policy="DISCOVERY")
    cold = [c for c in sorted(data.customers) if not data.history(c, T1)]
    if not cold:
        raise ValueError("no cold-start customer for demo")
    result["COLD_START"] = model.recommend(cold[0], as_of=T1, mode="UNIFIED", policy="DISCOVERY")
    return result


def evaluate(model, split):
    if split not in ("VALIDATION", "TEST"):
        raise ValueError("only held-out sanity evaluation is supported")
    start, end = (T1, T2) if split == "VALIDATION" else (T2, END)
    data = model.data
    candidates = data.candidates(start)
    eligible = {(c["recommendation_type"], c["item_id"]) for c in candidates}
    truth, excluded = defaultdict(set), 0
    for row in data.tables["sale_items"]:
        if start <= row["purchased_at"] < end:
            identity = row["purchase_type"], row["product_id"] if row["purchase_type"] == "PRODUCT" else row["recipe_id"]
            if identity in eligible:
                truth[row["customer_id"]].add(identity)
            else:
                excluded += 1
    records, predictions = [], []
    for customer in sorted(truth):
        prediction = model.recommend(customer, as_of=start, mode="UNIFIED", policy="REPEAT")
        predictions.append(prediction)
        ranked = [(r["recommendation_type"], r["item_id"]) for r in prediction["recommendations"]]
        hits = [identity in truth[customer] for identity in ranked]
        dcg = sum(int(hit) / math.log2(rank + 2) for rank, hit in enumerate(hits))
        ideal = sum(1 / math.log2(rank + 2) for rank in range(min(5, len(truth[customer]))))
        cohort = data.cohort(customer, END)  # Reporting labels only, never fitting/profile input.
        cold = not data.history(customer, T1)
        history_cohort = data.cohort(customer, start)
        cross_p_b = cross_b_p = False
        if history_cohort == "PRODUCT_ONLY":
            cross_p_b = bool(model.recommend(customer, as_of=start, mode="CUSTOM_BOM", policy="REPEAT")["recommendations"])
        elif history_cohort == "BOM_ONLY":
            cross_b_p = bool(model.recommend(customer, as_of=start, mode="PRODUCT", policy="REPEAT")["recommendations"])
        records.append({"customer_id": customer, "cohort": cohort, "history_cohort": history_cohort,
                        "temperature": "COLD_TRAIN" if cold else "WARM_TRAIN",
                        "precision_at_5": sum(hits) / 5,
                        "recall_at_5": sum(hits) / len(truth[customer]),
                        "ndcg_at_5": dcg / ideal if ideal else 0,
                        "returned_k": len(ranked), "cold_fallback": prediction["fallback"] == "POPULARITY_COLD",
                        "recommended": ranked, "relevant_count": len(truth[customer]),
                        "product_to_bom": cross_p_b, "bom_to_product": cross_b_p,
                        "unified_product_to_bom": history_cohort == "PRODUCT_ONLY" and any(t == "CUSTOM_BOM" for t, _ in ranked),
                        "unified_bom_to_product": history_cohort == "BOM_ONLY" and any(t == "PRODUCT" for t, _ in ranked)})

    def aggregate(rows):
        if not rows:
            return {"customers": 0}
        recommended = {tuple(pair) for row in rows for pair in row["recommended"]}
        cold_rows = [r for r in rows if r["temperature"] == "COLD_TRAIN"]
        return {"customers": len(rows),
                **{name: sum(r[name] for r in rows) / len(rows) for name in ("precision_at_5", "recall_at_5", "ndcg_at_5")},
                "coverage_at_5": len(recommended) / len(eligible) if eligible else 0,
                "unique_recommended_items": len(recommended), "eligible_typed_items": len(eligible),
                "full_top5_rate": sum(r["returned_k"] == 5 for r in rows) / len(rows),
                "cold_start_fallback_rate": sum(r["cold_fallback"] for r in cold_rows) / len(cold_rows) if cold_rows else None,
                "popularity_fallback_rate_all": sum(r["cold_fallback"] for r in rows) / len(rows),
                "product_to_bom_customers": sum(r["product_to_bom"] for r in rows),
                "bom_to_product_customers": sum(r["bom_to_product"] for r in rows),
                "unified_product_to_bom_customers": sum(r["unified_product_to_bom"] for r in rows),
                "unified_bom_to_product_customers": sum(r["unified_bom_to_product"] for r in rows)}

    metrics = {"split": split, "as_of": start.isoformat(), "label_end_exclusive": end.isoformat(),
               "protocol": "frozen-boundary;REPEAT;strict-typed-labels;macro-average;CF/popularity Train-only;profile history before boundary",
               "cohort_reporting": "derived from full observed history only for stratification; not a model feature",
               "excluded_unavailable_label_rows": excluded, "overall": aggregate(records),
               "by_cohort": {g: aggregate([r for r in records if r["cohort"] == g]) for g in ("PRODUCT_ONLY", "BOM_ONLY", "MIXED")},
               "by_temperature": {g: aggregate([r for r in records if r["temperature"] == g]) for g in ("WARM_TRAIN", "COLD_TRAIN")},
               "by_cohort_temperature": {g + ":" + t: aggregate([r for r in records if r["cohort"] == g and r["temperature"] == t])
                                         for g in ("PRODUCT_ONLY", "BOM_ONLY", "MIXED") for t in ("WARM_TRAIN", "COLD_TRAIN")},
               "recommendation_type_counts": dict(Counter(r["recommendation_type"] for p in predictions for r in p["recommendations"])),
               "strategy_counts": dict(Counter(r["strategy"] for p in predictions for r in p["recommendations"])),
               "model_fingerprint": model.payload()["model_fingerprint"],
               "dataset_id": data.id, "model_version": model.metadata["model_version"]}
    return metrics, {"customers": records, "predictions": predictions}
