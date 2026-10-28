"""Fixed physical-role feature scaling; no fit to held-out quantities or latent data."""
from collections import defaultdict
from dataclasses import dataclass
from decimal import Decimal
import math

import numpy as np

FEATURE_CONFIG = {"version": "role-log-serving-v1", "kernel": "exp(-0.5*squared_euclidean)",
                  "roles": {"BASE_TEA": {"scale_g": 20, "weight": 1.0},
                            "AROMA": {"scale_g": 3, "weight": 1.1},
                            "SWEETENER": {"scale_g": 15, "weight": 0.6},
                            "SUPPLEMENT": {"scale_g": 2, "weight": 0.8},
                            "DILUENT": {"weight": 0.0}},
                  "product_confidence": 0.75, "custom_synthetic_confidence": 1.0,
                  "purchase_quantity_cap_per_order": 3, "recipe_interest_cap": 3}


class Features:
    def __init__(self, data, train_recipes):
        self.data = data
        self.columns = sorted({i["ingredient_id"] for rid in train_recipes for i in data.items[rid]
                               if i["ingredient_role"] != "DILUENT"})
        self.index = {v: n for n, v in enumerate(self.columns)}

    def vector(self, recipe_id):
        r = self.data.recipes[recipe_id]
        vector = np.zeros(len(self.columns), dtype=np.float64)
        for i in self.data.items[recipe_id]:
            role = i["ingredient_role"]
            config = FEATURE_CONFIG["roles"].get(role)
            if config is None:
                raise ValueError("unrecognized ingredient role")
            if not config["weight"]:
                continue
            if i["canonical_unit"] != "g" or i["ingredient_id"] not in self.index:
                raise ValueError("unsupported physical dimension or unseen ingredient; rebuild required")
            per_serving = i["quantity_canonical"] / r["servings_per_recipe"]
            if not per_serving.is_finite() or per_serving <= 0:
                raise ValueError("invalid canonical serving quantity")
            vector[self.index[i["ingredient_id"]]] = config["weight"] * math.log1p(float(per_serving / Decimal(config["scale_g"])))
        if not np.isfinite(vector).all():
            raise ValueError("nonfinite feature vector")
        return vector

    def summary(self, recipe_id):
        servings = self.data.recipes[recipe_id]["servings_per_recipe"]
        return [{"ingredient": i["ingredient_code_snapshot"], "role": i["ingredient_role"],
                 "quantity": format((i["quantity_canonical"] / servings).normalize(), "f"),
                 "unit": i["canonical_unit"]}
                for i in sorted(self.data.items[recipe_id], key=lambda i: i["ingredient_code_snapshot"])]


def interest(history):
    orders = defaultdict(dict)
    for sale in history:
        confidence = 0.75 if sale["purchase_type"] == "PRODUCT" else 1.0
        value = confidence * min(float(sale["quantity"]), 3.0)
        rid, oid = sale["recipe_id"], sale["order_id"]
        orders[rid][oid] = max(orders[rid].get(oid, 0), value)
    return {rid: min(3.0, math.log1p(sum(rows.values()))) for rid, rows in orders.items()}


@dataclass
class TasteProfile:
    vector: np.ndarray | None
    weights: dict
    dislikes: set
    seen_hashes: set
    order_count: int
    product_inferred_orders: int
    custom_synthetic_orders: int


def taste_profile(data, features, customer_id, as_of):
    history = data.history(customer_id, as_of)
    weights = interest(history)
    vector = (sum(features.vector(r) * w for r, w in weights.items()) / sum(weights.values())) if weights else None
    history_ids = {s["id"] for s in history}
    dislikes = set()
    for f in data.feedback.get(customer_id, []):
        if f["feedback_time"] >= as_of:
            break
        if f["sale_item_id"] in history_ids and f["feedback_type"] == "PREFERENCE_DISLIKE":
            dislikes.add(data.sales[f["sale_item_id"]]["recipe_id"])
    return TasteProfile(vector, weights, dislikes,
                        {data.recipes[s["recipe_id"]]["recipe_hash"] for s in history},
                        len({s["order_id"] for s in history}),
                        len({s["order_id"] for s in history if s["purchase_type"] == "PRODUCT"}),
                        len({s["order_id"] for s in history if s["purchase_type"] == "CUSTOM_BOM"}))
