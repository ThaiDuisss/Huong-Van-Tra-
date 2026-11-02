"""Report-only CUSTOM_BOM evaluation; the Phase 3 model/protocol stay unchanged."""
from collections import Counter, defaultdict
from decimal import Decimal

from app.ingredient_poc.synthetic import END, T2


PROTOCOL = "BOM_TEST_TOP5_REPEAT_V1"


def build_report_snapshot(model):
    data = model.data
    eligible = {c["recipe_id"] for c in data.candidates(T2) if c["recommendation_type"] == "CUSTOM_BOM"}
    purchases = [s for s in data.tables["sale_items"]
                 if s["purchase_type"] == "CUSTOM_BOM" and T2 <= s["purchased_at"] < END]
    truth, orders, quantity, units = defaultdict(set), defaultdict(set), defaultdict(Decimal), defaultdict(set)
    sale_counts, recommended, matched = Counter(), Counter(), Counter()
    suggested_customers = defaultdict(set)
    unit_symbols = {u["id"]: u["symbol"] for u in data.tables["units"]}
    business_lines = set()
    excluded = 0
    for sale in purchases:
        key = sale["order_id"], sale["order_item_id"]
        if key in business_lines:
            raise ValueError("Duplicate BOM purchase business line")
        business_lines.add(key)
        rid = sale["recipe_id"]
        if rid not in data.recipes or sale["customer_id"] not in data.customers:
            raise ValueError("Unknown BOM purchase identity")
        if rid in eligible:
            truth[sale["customer_id"]].add(rid)
        else:
            excluded += 1
        amount = Decimal(str(sale["quantity"]))
        if not amount.is_finite() or amount <= 0 or sale["sale_unit_id"] not in unit_symbols:
            raise ValueError("Invalid BOM sale quantity/unit")
        orders[rid].add(sale["order_id"])
        sale_counts[rid] += 1
        quantity[rid] += amount
        units[rid].add(sale["sale_unit_id"])

    predictions, records = [], []
    for n, customer in enumerate(sorted(data.customers), 1):
        prediction = model.recommend(customer, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=5)
        rows = prediction["recommendations"]
        ids = [r["recipe_id"] for r in rows]
        if (prediction["customer_id"] != customer or prediction["mode"] != "CUSTOM_BOM"
                or prediction["as_of"] != T2.isoformat() or prediction["policy"] != "REPEAT"
                or prediction["requested_k"] != 5 or prediction["returned_k"] != len(rows)
                or len(rows) > 5 or len(ids) != len(set(ids))
                or any(r["recommendation_type"] != "CUSTOM_BOM" or r["item_id"] != r["recipe_id"]
                       or r["recipe_id"] not in eligible for r in rows)):
            raise ValueError("Incompatible BOM report prediction")
        hits = [rid for rid in ids if rid in truth[customer]]
        for rid in ids:
            recommended[rid] += 1
            suggested_customers[rid].add(customer)
        matched.update(hits)
        predictions.append(prediction)
        records.append({"customer_id": customer, "recommendation_id": f"BOM-GY-{n:04d}",
                        "recommended": ids, "purchased": sorted(truth[customer]), "matched": hits,
                        "returned_k": len(ids), "relevant_count": len(truth[customer]), "hits": len(hits),
                        "match_rate": len(hits) / len(ids) if ids else None})

    recipes = []
    for rid in sorted(data.recipes, key=lambda r: (data.recipes[r]["recipe_family_key"],
                                                  data.recipes[r]["recipe_version"], r)):
        if len(units[rid]) > 1:
            raise ValueError("Cannot sum BOM sale quantities with different sale units")
        recipes.append({"recipe_id": rid, "suggested_customers": len(suggested_customers[rid]),
                        "recommendation_count": recommended[rid], "purchase_orders": len(orders[rid]),
                        "sale_rows": sale_counts[rid], "sold_quantity": quantity[rid],
                        "sale_unit": unit_symbols[next(iter(units[rid]))] if units[rid] else None,
                        "matched_count": matched[rid],
                        "match_rate": matched[rid] / recommended[rid] if recommended[rid] else None})
    appearances, hits = sum(recommended.values()), sum(matched.values())
    return {"protocol": {"name": PROTOCOL, "mode": "CUSTOM_BOM", "policy": "REPEAT", "top_k": 5,
                         "as_of": T2.isoformat(), "label_end_exclusive": END.isoformat(),
                         "match_identity": "(CUSTOM_BOM, recipe_id)",
                         "ratio": "matched recommendations / returned recommendations",
                         "purchase_scope": "TEST [T2, END); eligible CUSTOM_BOM labels only",
                         "phase3_original_metrics": False},
            "summary": {"customers": len(records), "customers_with_bom_labels": sum(bool(truth[c]) for c in data.customers),
                        "recommendations": appearances, "matches": hits,
                        "match_rate": hits / appearances if appearances else None,
                        "dataset_recipes": len(data.recipes), "recommended_recipes": len(recommended),
                        "eligible_recipes": len(eligible),
                        "coverage": len(recommended) / len(eligible) if eligible else None,
                        "test_bom_sale_rows": len(purchases), "excluded_unavailable_label_rows": excluded},
            "evaluation": records, "predictions": predictions, "recipes": recipes}
