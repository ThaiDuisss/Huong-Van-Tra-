"""Fixed, explainable CB + recipe item-item CF for offline synthetic research."""
from collections import defaultdict
import json
import math
from pathlib import Path

import numpy as np

from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.synthetic import END, T1, canonical_json
from app.ingredient_poc.recommendation.data import external, sha256, utc
from app.ingredient_poc.recommendation.features import FEATURE_CONFIG, Features, interest, taste_profile

MODEL_VERSION = "ingredient-research-v1"
RULES = {"version": "fixed-ranking-v1", "cf": "recipe-cosine-shared/(shared+5)",
         "min_item_customers": 2, "min_pair_customers": 2,
         "min_profile_orders": 2, "min_profile_cf_recipes": 2,
         "cb_weight": 0.70, "cf_weight": 0.25, "popularity_weight": 0.05,
         "cb_fallback_weight": 0.95, "dislike_multiplier": 0.5,
         "cf_normalization": "divide by max raw CF over all available recipes, independent of mode/policy",
         "discovery": "exclude seen recipe content across both domains",
         "unified": "global typed-candidate scoring then deduplicate recipe content, no quota"}


class IngredientRecommender:
    @classmethod
    def fit(cls, data):
        obj = cls()
        obj.data = data
        obj.recipe_ids = sorted(rid for rid, r in data.recipes.items()
                                if r["validation_status"] == "VALIDATED"
                                and utc(r["provenance"]["available_at"]) < T1)
        obj.recipe_index = {rid: i for i, rid in enumerate(obj.recipe_ids)}
        obj.features = Features(data, obj.recipe_ids)
        users, histories = [], {}
        train = []
        typed_users = defaultdict(set)
        for customer in sorted(data.customers):
            history = data.history(customer, T1)  # Filter before any matrix/profile statistic.
            if not history:
                continue
            if any(r["recipe_id"] not in obj.recipe_index for r in history):
                raise ValueError("future/unsupported recipe in training")
            users.append(customer)
            histories[customer] = history
            train.extend(history)
            for row in history:
                item_id = row["product_id"] if row["purchase_type"] == "PRODUCT" else row["recipe_id"]
                typed_users[(row["purchase_type"], item_id)].add(customer)
        matrix = np.zeros((len(users), len(obj.recipe_ids)), dtype=np.float64)
        for n, customer in enumerate(users):
            for rid, weight in interest(histories[customer]).items():
                matrix[n, obj.recipe_index[rid]] = weight
        presence = (matrix > 0).astype(np.int64)
        obj.support = presence.sum(axis=0)
        shared = presence.T @ presence
        gram = matrix.T @ matrix
        norms = np.sqrt(np.diag(gram))
        denominator = norms[:, None] * norms[None, :]
        similarity = np.divide(gram, denominator, out=np.zeros_like(gram), where=denominator > 0)
        similarity *= shared / (shared + 5.0)
        similarity[shared < 2] = 0
        similarity[obj.support < 2, :] = 0
        similarity[:, obj.support < 2] = 0
        np.fill_diagonal(similarity, 0)
        obj.similarity = similarity
        obj.popularity = {typ + ":" + iid: len(values) for (typ, iid), values in typed_users.items()}
        obj.popularity_scale = max(obj.popularity.values(), default=1)
        features = {rid: obj.features.vector(rid).tolist() for rid in obj.recipe_ids}
        obj.metadata = {"model_version": MODEL_VERSION, "dataset_id": data.id,
                        "dataset_file_sha256": data.file_sha256,
                        "source_snapshot_hash": data.dataset["source_snapshot_hash"],
                        "source_kind": "SYNTHETIC", "research_only": True, "production_approved": False,
                        "train_cutoff": T1.isoformat(), "feature_config": FEATURE_CONFIG, "ranking_rules": RULES,
                        "train_counts": {"sales": len(train), "customers": len(users), "recipes": len(obj.recipe_ids)},
                        "fit_data_hash": content_hash({"train_sales": sorted(train, key=lambda r: r["id"]),
                                                      "train_recipe_features": features}),
                        "feature_ingredient_ids": obj.features.columns}
        obj._check()
        return obj

    def _check(self):
        n = len(self.recipe_ids)
        if (self.metadata["model_version"] != MODEL_VERSION or self.metadata["dataset_id"] != self.data.id
                or self.metadata["dataset_file_sha256"] != self.data.file_sha256
                or self.metadata["source_snapshot_hash"] != self.data.dataset["source_snapshot_hash"]
                or self.metadata["source_kind"] != "SYNTHETIC" or self.metadata["research_only"] is not True
                or self.metadata["production_approved"] is not False
                or self.metadata["train_cutoff"] != T1.isoformat()
                or self.metadata["feature_config"] != FEATURE_CONFIG or self.metadata["ranking_rules"] != RULES
                or self.features.columns != self.metadata["feature_ingredient_ids"]
                or self.similarity.shape != (n, n) or self.support.shape != (n,)
                or not np.isfinite(self.similarity).all() or (self.similarity < 0).any()
                or (self.similarity > 1.00000001).any() or not np.allclose(self.similarity, self.similarity.T)
                or not np.allclose(np.diag(self.similarity), 0)
                or (self.support < 0).any() or self.popularity_scale <= 0):
            raise ValueError("model artifact/config/source guard failed")
        if any(utc(self.data.recipes[r]["provenance"]["available_at"]) >= T1 for r in self.recipe_ids):
            raise ValueError("future recipe in fitted artifact")

    def payload(self):
        value = {"metadata": self.metadata, "recipe_ids": self.recipe_ids,
                 "support": self.support.tolist(), "similarity": self.similarity.tolist(),
                 "popularity": self.popularity, "popularity_scale": self.popularity_scale}
        return {**value, "model_fingerprint": content_hash(value)}

    def save(self, path):
        path = external(path)
        if path.name in ("v1_hybrid_model.npz", "lightfm_model.pkl"):
            raise ValueError("serving artifact names are forbidden")
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("x", encoding="utf-8", newline="\n") as output:
            output.write(canonical_json(self.payload()) + "\n")
        return sha256(path)

    @classmethod
    def load(cls, path, expected_sha256, data):
        if sha256(path) != expected_sha256:
            raise ValueError("research model SHA-256 mismatch")
        value = json.loads(Path(path).read_text(encoding="utf-8"))
        fingerprint = value.pop("model_fingerprint")
        if content_hash(value) != fingerprint:
            raise ValueError("model fingerprint mismatch")
        obj = cls()
        obj.data, obj.metadata, obj.recipe_ids = data, value["metadata"], value["recipe_ids"]
        obj.recipe_index = {rid: i for i, rid in enumerate(obj.recipe_ids)}
        obj.features = Features(data, obj.recipe_ids)
        obj.support = np.asarray(value["support"], dtype=np.int64)
        obj.similarity = np.asarray(value["similarity"], dtype=np.float64)
        obj.popularity, obj.popularity_scale = value["popularity"], value["popularity_scale"]
        obj._check()
        return obj

    def profile(self, customer_id, as_of):
        if customer_id not in self.data.customers:
            raise ValueError("customer is not in this research dataset")
        return taste_profile(self.data, self.features, customer_id, utc(as_of))

    def _cf(self, profile, recipe_ids):
        supported = {r: w for r, w in profile.weights.items() if r in self.recipe_index
                     and self.support[self.recipe_index[r]] >= 2}
        if profile.order_count < 2 or len(supported) < 2:
            return {rid: 0.0 for rid in recipe_ids}
        weights = np.zeros(len(self.recipe_ids))
        for rid, weight in supported.items():
            weights[self.recipe_index[rid]] = weight
        raw = weights @ self.similarity / weights.sum()
        return {rid: float(raw[self.recipe_index[rid]]) if rid in self.recipe_index else 0.0 for rid in recipe_ids}

    def recommend(self, customer_id, *, as_of, mode="UNIFIED", top_k=5, policy="DISCOVERY"):
        as_of = utc(as_of)
        if mode not in ("PRODUCT", "CUSTOM_BOM", "UNIFIED") or policy not in ("DISCOVERY", "REPEAT"):
            raise ValueError("invalid mode/policy")
        if isinstance(top_k, bool) or not isinstance(top_k, int) or not 1 <= top_k <= 50:
            raise ValueError("top_k must be between 1 and 50")
        if not T1 <= as_of <= END:
            raise ValueError("inference requires Train-complete as-of within the verified research window")
        profile = self.profile(customer_id, as_of)
        all_candidates = self.data.candidates(as_of)
        eligible_recipes = sorted({c["recipe_id"] for c in all_candidates})
        raw_cf = self._cf(profile, eligible_recipes)
        cf_scale = max(raw_cf.values(), default=0)
        scored = []
        for candidate in all_candidates:
            typ, iid, rid = candidate["recommendation_type"], candidate["item_id"], candidate["recipe_id"]
            if mode != "UNIFIED" and typ != mode:
                continue
            recipe = self.data.recipes[rid]
            if policy == "DISCOVERY" and recipe["recipe_hash"] in profile.seen_hashes:
                continue
            pop = self.popularity.get(typ + ":" + iid, 0) / self.popularity_scale
            cf = raw_cf[rid] / cf_scale if cf_scale > 0 else 0.0
            if profile.vector is None:
                cb, score, strategy = 0.0, pop, "POPULARITY_COLD"
            else:
                difference = profile.vector - self.features.vector(rid)
                cb = math.exp(-0.5 * float(difference @ difference))
                if cf > 0:
                    score, strategy = 0.70 * cb + 0.25 * cf + 0.05 * pop, "CB_CF"
                else:
                    score, strategy = 0.95 * cb + 0.05 * pop, "CB_FALLBACK"
            if rid in profile.dislikes:
                score *= 0.5
            if not math.isfinite(score):
                raise ValueError("nonfinite recommendation score")
            if profile.vector is None:
                reason = "Phổ biến trong Train; customer chưa có lịch sử trước as-of."
            else:
                vector = self.features.vector(rid)
                shared = sorted(((min(float(vector[i]), float(profile.vector[i])), ingredient)
                                 for i, ingredient in enumerate(self.features.columns) if vector[i] > 0 and profile.vector[i] > 0), reverse=True)
                codes = [self.data.ingredients[ingredient]["ingredient_code"] for _, ingredient in shared[:2]]
                signal = "PRODUCT suy diễn" if not profile.custom_synthetic_orders else "CUSTOM_BOM synthetic" if not profile.product_inferred_orders else "PRODUCT suy diễn + CUSTOM_BOM synthetic"
                reason = f"Hợp hồ sơ {signal}: {', '.join(codes)}; " + ("có CF recipe từ Train." if cf > 0 else "CB fallback, CF chưa đủ support.")
            if rid in profile.dislikes:
                reason += " Giảm ưu tiên do phản hồi không thích trước as-of."
            row = {**candidate, "customer_id": customer_id, "score": round(score, 10),
                   "reason": reason, "model_version": MODEL_VERSION, "dataset_id": self.data.id,
                   "as_of": as_of.isoformat(), "strategy": strategy, "policy": policy,
                   "research_only": True, "production_approved": False,
                   "components": {"cb": round(cb, 10), "cf": round(cf, 10), "train_popularity": round(pop, 10)},
                   "profile_signals": {"orders": profile.order_count, "product_inferred_orders": profile.product_inferred_orders,
                                       "custom_synthetic_orders": profile.custom_synthetic_orders},
                   "recipe_family": recipe["recipe_family_key"], "recipe_hash": recipe["recipe_hash"],
                   "ingredient_summary": self.features.summary(rid)}
            scored.append(row)
        scored.sort(key=lambda r: (-r["score"], r["recommendation_type"], r["item_id"]))
        output, seen, typed_seen = [], set(), set()
        for row in scored:
            identity = row["recipe_hash"] if mode == "UNIFIED" else (row["recommendation_type"], row["item_id"])
            typed_identity = row["recommendation_type"], row["item_id"]
            if identity in seen or typed_identity in typed_seen:
                continue
            seen.add(identity)
            typed_seen.add(typed_identity)
            output.append({**row, "rank": len(output) + 1})
            if len(output) == top_k:
                break
        return {"customer_id": customer_id, "dataset_id": self.data.id, "model_version": MODEL_VERSION,
                "as_of": as_of.isoformat(), "mode": mode, "policy": policy, "requested_k": top_k,
                "returned_k": len(output), "fallback": "POPULARITY_COLD" if profile.vector is None else "CB_FALLBACK" if not any(raw_cf.values()) else "CB_CF",
                "status": "OK" if len(output) == top_k else "INSUFFICIENT_ELIGIBLE_CANDIDATES",
                "recommendations": output}
