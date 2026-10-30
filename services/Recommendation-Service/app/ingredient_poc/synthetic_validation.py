"""Full in-memory validator used before import and again against locked DB rows."""
from collections import Counter, defaultdict
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from itertools import combinations
import math
import re
from uuid import UUID, uuid5

from sqlalchemy import DateTime, Integer, JSON, Numeric, String, UniqueConstraint

from app.ingredient_poc import schema as s
from app.ingredient_poc.contracts import content_hash, recipe_hash
from app.ingredient_poc.synthetic import (
    CREATED, END, NAMESPACE, POLICY, TABLES, T1, T2, VERSION, canonical_json,
    generation_config, partition, recipe_contract, sale_contract, split_view, table_checksums, unit_contract,
)


class DatasetInvalid(ValueError):
    def __init__(self, report):
        self.report = report
        super().__init__("synthetic validation failed: " + ",".join(sorted({r["code"] for r in report["reasons"]})))


def validate(bundle, *, raise_on_error=True):
    reasons = []
    stats = {}

    def require(ok, code, path, detail=""):
        if not ok:
            reasons.append({"code": code, "path": path, "detail": str(detail)[:240]})
        return ok

    def finish():
        report = {"status": "FAIL" if reasons else "PASS", "policy": POLICY,
                  "reasons": reasons, "statistics": stats}
        if reasons and raise_on_error:
            raise DatasetInvalid(report)
        return report

    if not require(isinstance(bundle, dict) and set(bundle) == {"dataset", "tables", "evaluation"},
                   "BUNDLE_SHAPE", "bundle"):
        return finish()
    ds, tables = bundle["dataset"], bundle["tables"]
    if not require(isinstance(ds, dict) and isinstance(tables, dict) and set(tables) == set(TABLES),
                   "TABLE_SET", "tables"):
        return finish()
    if not require(isinstance(bundle["evaluation"], dict)
                   and isinstance(bundle["evaluation"].get("profiles"), dict)
                   and isinstance(bundle["evaluation"].get("choices"), list)
                   and all(isinstance(r, dict) and set(r) == {"sale_id", "mode", "utility"}
                           for r in bundle["evaluation"]["choices"])
                   and all(isinstance(p, dict) for p in bundle["evaluation"]["profiles"].values()),
                   "EVALUATION_SHAPE", "evaluation"):
        return finish()
    try:
        canonical_json(bundle["evaluation"])
    except (ValueError, TypeError):
        require(False, "EVALUATION_SHAPE", "evaluation")
        return finish()
    all_rows = {"datasets": [ds], **tables}
    by_id = {}
    # Metadata-driven scalar, unique and composite FK validation on every row.
    for short, rows in all_rows.items():
        table = s.metadata.tables["ingredient_poc_" + short]
        if not require(isinstance(rows, list), "ROWS_SHAPE", short):
            return finish()
        by_id[short] = {}
        for row in rows:
            if not require(isinstance(row, dict) and set(row) == set(table.c.keys()), "COLUMN_SET", short):
                return finish()
            path = short + ":" + str(row["id"])
            for col in table.c:
                value = row[col.name]
                if value is None:
                    require(col.nullable, "REQUIRED", path + "." + col.name)
                    continue
                good = True
                if isinstance(col.type, Numeric):
                    try:
                        good = (isinstance(value, Decimal) and value.is_finite()
                                and value == value.quantize(Decimal(1).scaleb(-col.type.scale))
                                and abs(value) < Decimal(10) ** (col.type.precision - col.type.scale))
                    except InvalidOperation:
                        good = False
                elif isinstance(col.type, Integer):
                    good = isinstance(value, int) and not isinstance(value, bool) and -(2**31) <= value < 2**31
                elif isinstance(col.type, DateTime):
                    good = (isinstance(value, datetime) and value.tzinfo is not None
                            and value.utcoffset() is not None and value.utcoffset().total_seconds() == 0)
                elif isinstance(col.type, String):
                    good = isinstance(value, str) and 0 < len(value) <= col.type.length
                    if good and col.type.length == 36:
                        try:
                            good = str(UUID(value)) == value
                        except ValueError:
                            good = False
                elif isinstance(col.type, JSON):
                    try:
                        canonical_json(value)
                        good = isinstance(value, dict)
                    except (ValueError, TypeError):
                        good = False
                require(good, "SCALAR_TYPE_PRECISION", path + "." + col.name)
            if not isinstance(row["id"], str):
                return finish()
            try:
                require(str(UUID(row["id"])) == row["id"], "UUID_CANONICAL", path)
            except (ValueError, TypeError, AttributeError):
                require(False, "UUID_CANONICAL", path)
            require(row["source_kind"] == "SYNTHETIC", "ORIGIN", path)
            require(row["created_at"] == CREATED, "CREATED_AT_POLICY", path)
            if short != "datasets":
                require(row["dataset_id"] == ds["id"], "CROSS_DATASET", path)
            if "source_system" in row:
                require(row["source_system"] == "research:hvtra-synthetic-v2", "SOURCE_NAMESPACE", path)
            for name in ("source_product_id", "source_variant_id", "source_ingredient_id", "source_unit_id"):
                if name in row:
                    require(row[name] is None, "SYNTHETIC_SOURCE_ID", path)
            require(not any(k in canonical_json(row) for k in ('"weights"', '"profiles"', '"cohort_design"', '"utility"')),
                    "LATENT_FEATURE_LEAK", path)
            require(row["id"] not in by_id[short], "DUPLICATE_ID", path)
            by_id[short][row["id"]] = row
        for constraint in table.constraints:
            if isinstance(constraint, UniqueConstraint):
                seen = set()
                for row in rows:
                    key = tuple(row[c.name] for c in constraint.columns)
                    if any(v is None for v in key):
                        continue
                    require(key not in seen, "DUPLICATE_BUSINESS_KEY", str(constraint.name))
                    seen.add(key)
    if reasons:  # invalid scalar types cannot safely participate in arithmetic/joins
        return finish()
    for short, rows in all_rows.items():
        table = s.metadata.tables["ingredient_poc_" + short]
        for fk in table.foreign_key_constraints:
            target_short = fk.referred_table.name.removeprefix("ingredient_poc_")
            targets = {tuple(r[e.column.name] for e in fk.elements) for r in all_rows[target_short]}
            for row in rows:
                key = tuple(row[e.parent.name] for e in fk.elements)
                if all(v is not None for v in key):
                    require(key in targets, "REFERENCE", short + ":" + row["id"], fk.name)
    if reasons:
        return finish()
    manifest = ds["manifest_json"]
    config = manifest.get("config", {})
    if not require(isinstance(config, dict), "CONFIG", "manifest.config"):
        return finish()
    require(config.get("generator_version") == VERSION and config.get("policy") == POLICY,
            "GENERATOR_POLICY", "manifest.config")
    require(ds["schema_version"] == 1 and ds["validation_policy_version"] == POLICY,
            "SCHEMA_POLICY", "dataset")
    require(ds["status"] == "STAGED", "BUNDLE_LIFECYCLE", "dataset.status")
    try:
        require(content_hash(config) == content_hash(generation_config(config["seed"])),
                "CONFIG_POLICY", "manifest.config")
        namespace = uuid5(NAMESPACE, canonical_json(config))
        require(str(uuid5(namespace, "dataset")) == ds["id"], "DATASET_IDENTITY", "dataset.id")
        require(ds["dataset_name"] == f"hvtra-synthetic-{VERSION}-{config['seed']}", "DATASET_IDENTITY", "dataset.name")
        require(config["uuid5_namespace"] == str(NAMESPACE), "DATASET_IDENTITY", "namespace")
        require(content_hash(config["cutoffs"]) == content_hash({"train_end": T1, "validation_end": T2, "test_end": END}),
                "TEMPORAL_POLICY", "manifest.cutoffs")
        require(config["serving_basis"] == {"output_ml": 500, "servings": 1,
                                           "meaning": "synthetic prepared serving; not a mass-volume conversion"},
                "BASIS_POLICY", "manifest.serving_basis")
    except (KeyError, ValueError, TypeError):
        require(False, "CONFIG", "manifest.config")
    units, ingredients, recipes = by_id["units"], by_id["ingredients"], by_id["recipes"]
    for row in units.values():
        try:
            unit_contract(row)
            require(row["validation_status"] == "VALIDATED" and row["conversion_factor"] is not None,
                    "UNIT_VALIDATION", row["id"])
        except ValueError:
            require(False, "UNIT_CONVERSION", row["id"])
    items_by_recipe = defaultdict(list)
    for item in tables["recipe_items"]:
        items_by_recipe[item["recipe_id"]].append(item)
        require(item["validation_status"] == "VALIDATED", "ITEM_VALIDATION", item["id"])
        require(item["ingredient_role"] == ingredients[item["ingredient_id"]]["provenance"].get("role"),
                "INGREDIENT_ROLE", item["id"])
        require(item["ingredient_role"] in {"BASE_TEA", "AROMA", "SWEETENER", "SUPPLEMENT", "DILUENT"}
                and item["canonical_unit"] == ("ml" if item["ingredient_role"] == "DILUENT" else "g"),
                "INGREDIENT_DIMENSION", item["id"])
    ingredient_sets = defaultdict(set)
    family_versions = defaultdict(set)
    for row in recipes.values():
        try:
            contract = recipe_contract(row, items_by_recipe[row["id"]], units, ingredients)
            require(row["recipe_hash"] == recipe_hash(contract), "RECIPE_HASH", row["id"])
            require(all(i.quantity_canonical is not None for i in contract.items), "CANONICAL_MISSING", row["id"])
        except (ValueError, KeyError):
            require(False, "RECIPE_QUANTITY_UNIT", row["id"])
        require(len(items_by_recipe[row["id"]]) >= 3, "RECIPE_NONEMPTY", row["id"])
        require(row["recipe_kind"] == "CUSTOM" and row["validation_status"] == "VALIDATED",
                "RECIPE_KIND_STATUS", row["id"])
        require(row["portion_quantity"] == 500 and row["portion_unit"] == "ml"
                and row["servings_per_recipe"] == 1 and row["provenance"].get("basis") == "one-designed-500ml-serving-v1",
                "SERVING_BASIS", row["id"])
        require(row["provenance"].get("not_shop_approved") is True
                and row["provenance"].get("effective_time_unknown") is False,
                "SYNTHETIC_PROVENANCE", row["id"])
        family_versions[row["recipe_family_key"]].add(row["recipe_version"])
        signature = tuple(sorted(i["ingredient_id"] for i in items_by_recipe[row["id"]]))
        ingredient_sets[signature].add(row["recipe_hash"])
    require(len(recipes) == 80 and len({r["recipe_hash"] for r in recipes.values()}) == 80,
            "RECIPE_COUNT_DISTINCT", "recipes")
    require(len(family_versions) == 20 and all(v == {1, 2, 3, 4} for v in family_versions.values()),
            "FAMILY_VERSIONS", "recipes")
    require(set(family_versions) == {f"research-family-{i:02}" for i in range(20)}, "FAMILY_POLICY", "recipes")
    require(any(len(v) >= 4 for v in ingredient_sets.values()), "QUANTITY_VARIATION", "recipes")
    for p in tables["products"] + tables["variants"]:
        require(p["provenance"].get("bridge_recipe_id") in recipes, "PRODUCT_BOM_BRIDGE", p["id"])
    for v in tables["variants"]:
        require(v["provenance"].get("bridge_recipe_id") == by_id["products"][v["product_id"]]["provenance"].get("bridge_recipe_id"),
                "PRODUCT_BOM_BRIDGE", v["id"])
    types, order_splits, family_customers, repeat_orders = defaultdict(set), defaultdict(set), defaultdict(set), defaultdict(set)
    order_types, train_family_customers, histories = defaultdict(set), defaultdict(set), defaultdict(list)
    first_customer, first_recipe = {}, {}
    sales_count, split_counts, family_counts = Counter(), Counter(), Counter()
    for row in tables["orders"]:
        require(row["order_status"] == "COMPLETED" and row["payment_status"] == "PAID", "ORDER_STATUS", row["id"])
    for row in tables["sale_items"]:
        try:
            contract = sale_contract(row)
            require(contract.payload_hash() == row["payload_hash"], "SALE_PAYLOAD_HASH", row["id"])
        except ValueError:
            require(False, "SALE_CONTRACT", row["id"])
        require(row["sale_unit_id"] in units and units[row["sale_unit_id"]]["symbol"] == "count"
                and row["quantity"] > 0, "SALE_UNIT", row["id"])
        require(row["recipe_id"] in recipes and row["actual_recipe_snapshot_id"] is None,
                "SYNTHETIC_RECIPE_REFERENCE", row["id"])
        if row["recipe_id"] not in recipes:
            continue
        recipe = recipes[row["recipe_id"]]
        if row["purchase_type"] == "PRODUCT":
            require(by_id["variants"][row["variant_id"]]["provenance"]["bridge_recipe_id"] == row["recipe_id"],
                    "SALE_PRODUCT_BOM_BRIDGE", row["id"])
        when = row["purchased_at"]
        try:
            available = datetime.fromisoformat(recipe["provenance"]["available_at"])
            require(available.tzinfo is not None and CREATED <= available <= when < END,
                    "FUTURE_RECIPE", row["id"])
        except (ValueError, KeyError, TypeError):
            require(False, "RECIPE_AVAILABILITY", recipe["id"])
        split = partition(when)
        types[row["customer_id"]].add(row["purchase_type"])
        order_splits[row["order_id"]].add(split)
        order_types[row["order_id"]].add(row["purchase_type"])
        sales_count[row["purchase_type"]] += 1
        split_counts[split] += 1
        family = recipe["recipe_family_key"]
        family_counts[family] += 1
        family_customers[family].add(row["customer_id"])
        if split == "TRAIN":
            train_family_customers[family].add(row["customer_id"])
        histories[row["customer_id"]].append((when, row["recipe_id"]))
        repeat_orders[row["customer_id"], row["recipe_id"]].add(row["order_id"])
        first_customer[row["customer_id"]] = min(first_customer.get(row["customer_id"], when), when)
        first_recipe[row["recipe_id"]] = min(first_recipe.get(row["recipe_id"], when), when)
    cohorts = Counter("BOTH" if len(t) == 2 else "PRODUCT_ONLY" if "PRODUCT" in t else "BOM_ONLY" for t in types.values())
    require(len(tables["customers"]) == 400 and len(types) == 400, "CUSTOMER_COUNT", "customers")
    require(sales_count == {"PRODUCT": 1600, "CUSTOM_BOM": 2400}, "SALE_COUNTS", "sales", sales_count)
    require(cohorts == {"PRODUCT_ONLY": 120, "BOM_ONLY": 120, "BOTH": 160}, "COHORT_COUNTS", "cohorts", cohorts)
    cohort_sales = defaultdict(Counter)
    for row in tables["sale_items"]:
        t = types[row["customer_id"]]
        cohort = "BOTH" if len(t) == 2 else "PRODUCT_ONLY" if "PRODUCT" in t else "BOM_ONLY"
        cohort_sales[cohort][row["purchase_type"]] += 1
    require(dict(cohort_sales) == {"PRODUCT_ONLY": Counter(PRODUCT=720), "BOM_ONLY": Counter(CUSTOM_BOM=1200),
                                  "BOTH": Counter(PRODUCT=880, CUSTOM_BOM=1200)}, "COHORT_SALE_COUNTS", "cohorts")
    require(len(tables["products"]) == 60 and len(tables["variants"]) == 60, "PRODUCT_DIMENSION", "products")
    require(not tables["recipe_snapshots"], "NO_FAKE_ACTUAL", "snapshots")
    require(all(len(v) == 1 for v in order_splits.values()) and len(order_splits) == len(tables["orders"]),
            "ORDER_SPLIT", "orders")
    require(set(split_counts) == {"TRAIN", "VALIDATION", "TEST"}, "TEMPORAL_SPLITS", "sales")
    cold_customers = {c for c, when in first_customer.items() if when >= T1}
    cold_recipes = {r for r, when in first_recipe.items() if when >= T1}
    require(len(cold_customers) == 20 and sum(T1 <= first_customer[c] < T2 for c in cold_customers) == 10,
            "COLD_CUSTOMER", "temporal")
    require(len(cold_recipes) == 4 and len(first_recipe) == 80, "COLD_RECIPE", "temporal")
    repeat_edges = sum(len(v) >= 2 for v in repeat_orders.values())
    require(repeat_edges >= 200, "REPEAT_SUPPORT", "sales")
    overlaps = [len(a & b) for a, b in combinations(family_customers.values(), 2)]
    edges = sum(map(len, family_customers.values()))
    require(len(family_customers) == 20 and min(map(len, family_customers.values())) >= 5
            and sum(v >= 5 for v in overlaps) >= 100, "CF_OVERLAP", "families")
    require(max(family_counts.values()) >= 2 * min(family_counts.values()), "HEAD_TAIL", "families")
    train_overlaps = [len(a & b) for a, b in combinations(train_family_customers.values(), 2)]
    require(len(train_family_customers) == 20 and min(map(len, train_family_customers.values())) >= 5
            and sum(v >= 5 for v in train_overlaps) >= 100, "TRAIN_CF_OVERLAP", "families")
    unique_histories = len({tuple(r for _, r in sorted(h)) for h in histories.values()})
    require(unique_histories >= 300, "BEHAVIOR_DIVERSITY", "sales")
    for row in tables["feedback"]:
        sale = by_id["sale_items"][row["sale_item_id"]]
        require(sale["purchased_at"] <= row["feedback_time"] < END, "FEEDBACK_TIME", row["id"])
        typ = row["feedback_type"]
        require((typ == "RATING" and row["rating"] in (1, 2, 3, 4, 5) and row["return_reason"] is None)
                or (typ == "PREFERENCE_DISLIKE" and row["rating"] is None and row["return_reason"] is None)
                or (typ == "OPERATIONAL_RETURN" and row["rating"] is None and row["return_reason"] == "DAMAGED"),
                "FEEDBACK_CONTRACT", row["id"])
    ratings = Counter(r["rating"] for r in tables["feedback"] if r["feedback_type"] == "RATING")
    require(len(ratings) >= 4, "RATING_DIVERSITY", "feedback")
    evaluation = bundle["evaluation"]
    profiles, choices = evaluation.get("profiles", {}), evaluation.get("choices", [])
    require(set(profiles) == set(by_id["customers"]) and evaluation.get("not_serving_features") is True,
            "EVALUATION_SIDECAR", "evaluation")
    require({c["sale_id"] for c in choices} == set(by_id["sale_items"]) and len(choices) == 4000,
            "CHOICE_DIAGNOSTICS", "evaluation")
    require(all(set(p) == {"weights", "drift", "designed_cold_start", "cohort_design"}
                and isinstance(p["weights"], list) and len(p["weights"]) == 5
                and all(isinstance(w, (int, float)) and not isinstance(w, bool)
                        and math.isfinite(w) and 0 < w < 3 for w in p["weights"])
                and isinstance(p["drift"], bool) and isinstance(p["designed_cold_start"], bool)
                for p in profiles.values()), "LATENT_PROFILE_SHAPE", "evaluation")
    require({c for c, p in profiles.items() if p.get("designed_cold_start")} == cold_customers,
            "COLD_DESIGN_HISTORY", "evaluation")
    require(all(p.get("cohort_design") == ("BOTH" if len(types[c]) == 2 else "PRODUCT_ONLY" if "PRODUCT" in types[c] else "BOM_ONLY")
                for c, p in profiles.items() if c in types), "COHORT_DESIGN_HISTORY", "evaluation")
    modes = Counter(c.get("mode") for c in choices)
    require(0.1 <= modes["EXPLORE"] / 4000 <= 0.3, "EXPLORATION", "evaluation")
    # Generator diagnostics only: compare sampled utility with the same head-tail
    # prior absent preferences. This is neither training nor recommendation performance.
    features = {}
    for rid, rows in items_by_recipe.items():
        quantities = {i["ingredient_role"]: float(i["quantity_canonical"]) for i in rows}
        dry_total = sum(float(i["quantity_canonical"]) for i in rows if i["canonical_unit"] == "g")
        features[rid] = [quantities.get("BASE_TEA", 0) / 35, quantities.get("SWEETENER", 0) / 20,
                         quantities.get("AROMA", 0) / 4, quantities.get("BASE_TEA", 0) / dry_total if dry_total else 0,
                         (len(rows) - 3) / 2]
    lifts = defaultdict(list)
    product_recipe_ids = {v["provenance"]["bridge_recipe_id"] for v in tables["variants"]}
    if not reasons:
        for choice in choices:
            sale = by_id["sale_items"][choice["sale_id"]]
            p = profiles[sale["customer_id"]]
            weights = p["weights"][:]
            if p["drift"] and sale["purchased_at"] >= T1:
                weights[1] += 0.15
            selected = sum(w * x for w, x in zip(weights, features[sale["recipe_id"]]))
            require(isinstance(choice["utility"], (int, float))
                    and abs(selected - choice["utility"]) < 0.000001, "CHOICE_UTILITY", choice["sale_id"])
            candidates = [r for r in recipes.values()
                          if datetime.fromisoformat(r["provenance"]["available_at"]) <= sale["purchased_at"]
                          and (sale["purchase_type"] != "PRODUCT" or r["id"] in product_recipe_ids)]
            priors = [1 / (1 + int(r["recipe_family_key"].rsplit("-", 1)[1]) / 2) for r in candidates]
            baseline = sum(prior * sum(w * x for w, x in zip(weights, features[r["id"]]))
                           for r, prior in zip(candidates, priors)) / sum(priors)
            lifts[choice["mode"]].append(selected - baseline)
        require(sum(lifts["UTILITY"]) > 0, "LATENT_BEHAVIOR_SIGNAL", "evaluation")
    checksums = table_checksums(tables)
    fingerprint = content_hash({"config": config, "table_checksums": checksums})
    require(manifest.get("table_checksums") == checksums and manifest.get("counts") == {k: len(v) for k, v in tables.items()},
            "MANIFEST_COUNTS_CHECKSUM", "manifest")
    require(manifest.get("content_fingerprint") == fingerprint and manifest.get("evaluation_hash") == content_hash(evaluation),
            "CONTENT_FINGERPRINT", "manifest")
    require(ds["source_snapshot_hash"] == content_hash({"content_fingerprint": fingerprint, "evaluation_hash": content_hash(evaluation)}),
            "SOURCE_CHECKSUM", "dataset")
    require(re.fullmatch(r"[0-9a-f]{64}", ds["source_snapshot_hash"]) is not None, "SHA256", "dataset")
    try:
        train = split_view(bundle)
    except (ValueError, KeyError, TypeError):
        require(False, "TRAIN_VIEW_FUTURE_RECIPE", "train")
        train = {"recipes": [], "recipe_items": [], "sale_items": []}
    stats.update(counts={k: len(v) for k, v in tables.items()}, cohorts=dict(cohorts),
                 cohort_sales={k: dict(v) for k, v in cohort_sales.items()}, sale_types=dict(sales_count),
                 split_sale_counts=dict(split_counts), orders=len(order_splits),
                 mixed_orders=sum(len(v) == 2 for v in order_types.values()),
                 cold_customers=len(cold_customers), cold_recipes=len(cold_recipes),
                 warm_customers=len(first_customer) - len(cold_customers), repeat_customer_recipe_edges=repeat_edges,
                 family_customer_edges=edges, family_density=edges / (400 * 20),
                 family_support={k: len(v) for k, v in sorted(family_customers.items())},
                 family_purchase_counts=dict(sorted(family_counts.items())),
                 family_pairs_shared_ge5=sum(v >= 5 for v in overlaps),
                 family_pair_count=len(overlaps), ratings=dict(ratings), choice_modes=dict(modes),
                 train_family_customer_edges=sum(map(len, train_family_customers.values())),
                 train_family_support={k: len(v) for k, v in sorted(train_family_customers.items())},
                 train_family_pairs_shared_ge5=sum(v >= 5 for v in train_overlaps),
                 unique_customer_histories=unique_histories,
                 generator_utility_lift_over_head_prior={k: sum(v) / len(v) for k, v in lifts.items() if v},
                 train_view_checksum=content_hash(train), train_recipe_count=len(train["recipes"]),
                 content_fingerprint=fingerprint, source_snapshot_hash=ds["source_snapshot_hash"])
    return finish()
