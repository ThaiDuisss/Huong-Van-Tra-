"""Deterministic research fixtures. No DB, live data, serving or model training.

Quantities are Decimal; floating point is used only for seeded behaviour sampling.
Latent profiles/choice diagnostics stay in a separate evaluation sidecar.
"""
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import json
import math
import random
from uuid import UUID, uuid5

from app.ingredient_poc.contracts import (
    RecipeContract, RecipeItemContract, SaleContract, UnitContract,
    _canonical, content_hash, recipe_hash,
)

VERSION = "synthetic-v2.1.0"
POLICY = "synthetic-validation-v2.1.0"
NAMESPACE = UUID("3929023c-5531-54d2-9fe6-ea257bcd92f1")
UTC = timezone.utc
CREATED = datetime(2026, 1, 1, tzinfo=UTC)
T1 = datetime(2026, 3, 1, tzinfo=UTC)
T2 = datetime(2026, 4, 1, tzinfo=UTC)
END = datetime(2026, 5, 1, tzinfo=UTC)
TABLES = ("customers", "units", "ingredients", "products", "variants", "orders",
          "recipes", "recipe_items", "recipe_snapshots", "sale_items", "feedback")


def canonical_json(value):
    return json.dumps(_canonical(value), sort_keys=True, separators=(",", ":"),
                      ensure_ascii=True, allow_nan=False)


def partition(value):
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("UTC-aware purchase time required")
    return "TRAIN" if value < T1 else "VALIDATION" if value < T2 else "TEST"


def unit_contract(row):
    return UnitContract(**{k: row[k] for k in (
        "symbol", "dimension", "canonical_unit", "conversion_factor", "conversion_rule_version")})


def recipe_contract(row, items, units, ingredients):
    return RecipeContract(
        recipe_family_key=row["recipe_family_key"], recipe_version=row["recipe_version"],
        recipe_kind=row["recipe_kind"], portion_quantity=row["portion_quantity"],
        portion_unit=row["portion_unit"], servings_per_recipe=row["servings_per_recipe"],
        effective_time_unknown=row["provenance"]["effective_time_unknown"],
        items=tuple(RecipeItemContract(
            ingredient_namespace=ingredients[i["ingredient_id"]]["source_system"],
            ingredient_code=i["ingredient_code_snapshot"], ingredient_role=i["ingredient_role"],
            quantity_original=i["quantity_original"], quantity_canonical=i["quantity_canonical"],
            waste_rate=i["waste_rate"], unit=unit_contract(units[i["unit_id"]])) for i in items))


def sale_contract(row):
    return SaleContract(**{k: row[k] for k in SaleContract.model_fields})


def table_checksums(tables):
    return {name: content_hash(sorted(tables[name], key=lambda r: r["id"])) for name in TABLES}


def seal(bundle):
    """Bind all dimension/fact rows and the separate evaluation sidecar to a manifest."""
    config = bundle["dataset"]["manifest_json"]["config"]
    checksums = table_checksums(bundle["tables"])
    fingerprint = content_hash({"config": config, "table_checksums": checksums})
    evaluation_hash = content_hash(bundle["evaluation"])
    manifest = {"config": config, "table_checksums": checksums,
                "counts": {k: len(v) for k, v in bundle["tables"].items()},
                "content_fingerprint": fingerprint, "evaluation_hash": evaluation_hash,
                "validation_complete": False}
    bundle["dataset"]["source_snapshot_hash"] = content_hash(
        {"content_fingerprint": fingerprint, "evaluation_hash": evaluation_hash})
    bundle["dataset"]["manifest_json"] = manifest
    return bundle


def generation_config(seed):
    if isinstance(seed, bool) or not isinstance(seed, int) or not 0 <= seed < 2**32:
        raise ValueError("seed must be an unsigned 32-bit integer")
    return {"generator_version": VERSION, "seed": seed, "uuid5_namespace": str(NAMESPACE),
              "serialization": "canonical-json-decimal-utc-v1", "policy": POLICY,
              "cutoffs": {"train_end": T1, "validation_end": T2, "test_end": END},
              "serving_basis": {"output_ml": 500, "servings": 1,
                                "meaning": "synthetic prepared serving; not a mass-volume conversion"},
              "unit_rules": "g/kg-to-g; ml/L-to-ml; count=1; no-density-no-g-to-ml",
              "family_rule": "synthetic base-tea/aroma pair v1; content versions vary sweetness",
              "origin": "SYNTHETIC_RESEARCH_ONLY_NOT_SHOP_APPROVED",
              "feedback_policy": "ratings 1..5; preference dislike separate from operational return",
              "sampling_policy": "continuous latent utilities (temperature=4) + head-tail prior + 20pct exploration + repeats;76 train/80 all-time coverage anchors",
              "cold_start_policy": "20 customers (10 validation,10 test);4 recipes available from T1"}


def generate(seed=20261027):
    config = generation_config(seed)
    rng = random.Random(seed)
    namespace = uuid5(NAMESPACE, canonical_json(config))
    key = lambda label: str(uuid5(namespace, label))
    dataset_id = key("dataset")
    source = "research:hvtra-synthetic-v2"
    tables = {name: [] for name in TABLES}

    def add(name, label, **values):
        row = dict(id=key(name + ":" + label), dataset_id=dataset_id, source_kind="SYNTHETIC",
                   created_at=CREATED, **values)
        tables[name].append(row)
        return row

    provenance = {"origin": "SYNTHETIC", "generator_version": VERSION,
                  "not_shop_approved": True}
    units = {}
    for symbol, dimension, canonical, factor in (
            ("g", "MASS", "g", 1), ("kg", "MASS", "g", 1000),
            ("ml", "VOLUME", "ml", 1), ("L", "VOLUME", "ml", 1000),
            ("count", "COUNT", "count", 1)):
        units[symbol] = add("units", symbol, source_system=source, source_unit_id=None,
                            symbol=symbol, dimension=dimension, canonical_unit=canonical,
                            conversion_factor=Decimal(factor), conversion_rule_version="physical-v1",
                            provenance=provenance.copy(), validation_status="VALIDATED")
    ingredient_defs = [("OOLONG", "BASE_TEA"), ("BLACK", "BASE_TEA"),
                       ("GREEN", "BASE_TEA"), ("WHITE", "BASE_TEA"),
                       ("JASMINE", "AROMA"), ("ROSE", "AROMA"), ("LOTUS", "AROMA"),
                       ("OSMANTHUS", "AROMA"), ("CHRYSANTHEMUM", "AROMA"),
                       ("SUGAR", "SWEETENER"), ("GINGER", "SUPPLEMENT"),
                       ("CITRUS", "SUPPLEMENT"), ("WATER", "DILUENT")]
    ingredients = {code: add("ingredients", code, source_system=source, source_ingredient_id=None,
                            ingredient_code=code, name_snapshot="Research " + code,
                            provenance={**provenance, "role": role})
                   for code, role in ingredient_defs}
    recipes = []
    recipe_features = []
    for family in range(20):
        base = ingredient_defs[family // 5][0]
        aroma = ingredient_defs[4 + family % 5][0]
        for version in range(1, 5):
            index = family * 4 + version - 1
            cold = family >= 16 and version == 4
            recipe = add("recipes", str(index), recipe_family_key=f"research-family-{family:02}",
                         recipe_version=version, recipe_kind="CUSTOM", base_product_id=None,
                         base_variant_id=None, base_sale_unit_id=None,
                         portion_quantity=Decimal(500), portion_unit_id=units["ml"]["id"],
                         portion_unit="ml", servings_per_recipe=Decimal(1), recipe_hash="0" * 64,
                         provenance={**provenance, "effective_time_unknown": False,
                                     "basis": "one-designed-500ml-serving-v1",
                                     "available_at": T1.isoformat() if cold else CREATED.isoformat()},
                         validation_status="VALIDATED")
            recipes.append(recipe)
            parts = [(base, Decimal(18 + family // 5 * 4), "BASE_TEA", "g"),
                     (aroma, Decimal(2 + family % 3), "AROMA", "g"),
                     ("SUGAR", Decimal(5 * version), "SWEETENER", "g"),
                     ("WATER", Decimal("0.4"), "DILUENT", "L")]
            if family % 3 == 0:
                parts.append(("GINGER" if family % 2 else "CITRUS", Decimal(2), "SUPPLEMENT", "g"))
            recipe_items = []
            for code, quantity, role, symbol in parts:
                # Exercise equivalent kg/g scales without treating normalized vectors as doses.
                if role == "BASE_TEA" and family % 2:
                    symbol, quantity = "kg", quantity / Decimal(1000)
                u = units[symbol]
                recipe_items.append(add(
                    "recipe_items", f"{index}:{code}", recipe_id=recipe["id"],
                    ingredient_id=ingredients[code]["id"], ingredient_code_snapshot=code,
                    ingredient_role=role, quantity_original=quantity, unit_id=u["id"],
                    unit_symbol_snapshot=symbol, quantity_canonical=quantity * u["conversion_factor"],
                    canonical_unit=u["canonical_unit"], canonical_factor=u["conversion_factor"],
                    waste_rate=Decimal("0.5"), validation_status="VALIDATED"))
            recipe["recipe_hash"] = recipe_hash(recipe_contract(
                recipe, recipe_items, {u["id"]: u for u in units.values()},
                {i["id"]: i for i in ingredients.values()}))
            dry_total = sum(q for _, q, _, symbol in parts if symbol != "L")
            recipe_features.append([float(parts[0][1]) / 35, version / 4,
                                    (2 + family % 3) / 4, float(parts[0][1] / dry_total),
                                    (len(parts) - 3) / 2])
    # 60 exact product->recipe bridges: 3 versions in each of all 20 families.
    product_recipe_indices = [f * 4 + v for f in range(20) for v in range(3)]
    variants = []
    for index, recipe_index in enumerate(product_recipe_indices):
        p = add("products", str(index), source_system=source, source_product_id=None,
                product_code=f"RESEARCH-P-{index:03}", name_snapshot=f"Synthetic tea {index:03}",
                provenance={**provenance, "bridge_recipe_id": recipes[recipe_index]["id"]})
        variants.append(add("variants", str(index), source_system=source, source_variant_id=None,
                            product_id=p["id"], sku=f"RESEARCH-SKU-{index:03}",
                            name_snapshot="Designed 500ml serving", sale_unit_id=units["count"]["id"],
                            provenance={**provenance, "bridge_recipe_id": recipes[recipe_index]["id"]}))
    evaluation = {"profiles": {}, "choices": [], "not_serving_features": True}
    custom_seen = defaultdict(list)
    product_seen = defaultdict(list)
    for customer_index in range(400):
        cohort = "PRODUCT_ONLY" if customer_index < 120 else "BOM_ONLY" if customer_index < 240 else "BOTH"
        local = customer_index if customer_index < 120 else customer_index - 120 if customer_index < 240 else customer_index - 240
        cold = local < (8 if cohort == "BOTH" else 6)
        customer = add("customers", str(customer_index), customer_id=key(f"customer-source:{customer_index}"),
                       source_system=source)
        weights = [rng.uniform(0.05, 0.5) for _ in range(5)]
        preference = customer_index % 6
        weights[preference % 5] += rng.uniform(0.6, 1.2)
        evaluation["profiles"][customer["id"]] = {
            "weights": [round(w, 8) for w in weights], "drift": preference == 5,
            "designed_cold_start": cold, "cohort_design": cohort}
        np = 6 if cohort == "PRODUCT_ONLY" else 0 if cohort == "BOM_ONLY" else 6 if local < 80 else 5
        nb = 10 if cohort == "BOM_ONLY" else 0 if cohort == "PRODUCT_ONLY" else 8 if local < 80 else 7
        if cohort == "BOM_ONLY":
            lines = [["CUSTOM_BOM", "CUSTOM_BOM"] for _ in range(5)]
        else:
            lines = [[*([] if i >= np else ["PRODUCT"]), *([] if i >= nb else ["CUSTOM_BOM"])]
                     for i in range(max(np, nb))]
        for order_index, types in enumerate(lines):
            if cold:
                split = "TEST" if local % 2 or order_index >= len(lines) // 2 else "VALIDATION"
            else:
                split = "TRAIN" if order_index < len(lines) * 0.6 else "VALIDATION" if order_index < len(lines) * 0.8 else "TEST"
            start = {"TRAIN": CREATED + timedelta(days=4), "VALIDATION": T1, "TEST": T2}[split]
            when = start + timedelta(days=(customer_index % 9) + order_index * 2,
                                     minutes=rng.randrange(30, 600))
            order = add("orders", f"{customer_index}:{order_index}", source_system=source,
                        source_order_id=key(f"order-source:{customer_index}:{order_index}"),
                        customer_id=customer["id"], order_status="COMPLETED", payment_status="PAID")
            for line_index, purchase_type in enumerate(types):
                product_domain = purchase_type == "PRODUCT"
                candidates = product_recipe_indices[:] if product_domain else list(range(80))
                candidates = [r for r in candidates if datetime.fromisoformat(recipes[r]["provenance"]["available_at"]) <= when]
                drift = 0.15 if preference == 5 and split != "TRAIN" else 0
                utilities = {r: sum((w + (drift if j == 1 else 0)) * x
                                    for j, (w, x) in enumerate(zip(weights, recipe_features[r]))) for r in candidates}
                explore = rng.random() < 0.2
                past = product_seen[customer["id"]] if product_domain else custom_seen[customer["id"]]
                if order_index == 1 and line_index == 0 and past:  # repeat across distinct orders
                    chosen = past[0]
                    mode = "REPEAT"
                elif not product_domain and cohort == "BOM_ONLY" and 6 <= local < 82 and order_index == 0 and line_index == 0:
                    chosen = [r for r in range(80) if not (r // 4 >= 16 and r % 4 == 3)][local - 6]
                    mode = "TRAIN_COVERAGE_ANCHOR"
                elif not product_domain and cohort == "BOM_ONLY" and local < 80 and order_index == 4 and line_index == 1:
                    chosen = local  # sparse coverage anchors, always in Test (or later cold history)
                    mode = "COVERAGE_ANCHOR"
                elif explore:
                    chosen, mode = rng.choice(candidates), "EXPLORE"
                else:
                    probabilities = [math.exp(utilities[r] * 4) / (1 + (r // 4) / 2) for r in candidates]
                    chosen, mode = rng.choices(candidates, weights=probabilities, k=1)[0], "UTILITY"
                past.append(chosen)
                v = variants[product_recipe_indices.index(chosen)] if product_domain else None
                sale = add("sale_items", f"{customer_index}:{order_index}:{line_index}",
                           customer_id=customer["id"], order_id=order["id"],
                           order_item_id=key(f"line-source:{customer_index}:{order_index}:{line_index}"),
                           purchase_type=purchase_type, product_id=v["product_id"] if v else None,
                           variant_id=v["id"] if v else None, recipe_id=recipes[chosen]["id"],
                           recipe_kind="CUSTOM", actual_recipe_snapshot_id=None,
                           sale_unit_id=units["count"]["id"], quantity=Decimal(1), purchased_at=when,
                           order_status="COMPLETED", payment_status="PAID", source_system=source,
                           source_event_id=key(f"event:{customer_index}:{order_index}:{line_index}"),
                           source_revision=1, payload_hash="0" * 64)
                sale["payload_hash"] = sale_contract(sale).payload_hash()
                evaluation["choices"].append({"sale_id": sale["id"], "mode": mode,
                                               "utility": round(utilities[chosen], 8)})
                n = len(tables["sale_items"])
                feedback_type = "OPERATIONAL_RETURN" if n % 53 == 0 else "PREFERENCE_DISLIKE" if n % 41 == 0 else "RATING" if n % 7 == 0 else None
                if feedback_type:
                    add("feedback", sale["id"], sale_item_id=sale["id"], source_system=source,
                        source_feedback_id="feedback:" + sale["order_item_id"], feedback_type=feedback_type,
                        rating=rng.choices([1, 2, 3, 4, 5], [1, 2, 3, 4, 3], k=1)[0] if feedback_type == "RATING" else None,
                        return_reason="DAMAGED" if feedback_type == "OPERATIONAL_RETURN" else None,
                        feedback_time=when + timedelta(hours=2))
    for rows in tables.values():
        rows.sort(key=lambda r: r["id"])
    return seal({"dataset": {"id": dataset_id, "dataset_name": f"hvtra-synthetic-{VERSION}-{seed}",
                              "schema_version": 1, "source_kind": "SYNTHETIC", "source_system": source,
                              "source_snapshot_hash": "0" * 64, "status": "STAGED", "created_at": CREATED,
                              "validation_policy_version": POLICY, "manifest_json": {"config": config}},
                 "tables": tables, "evaluation": evaluation})


def loads(value):
    """Typed reload of canonical JSON; enforce all columns later in the validator."""
    from sqlalchemy import DateTime, Numeric
    from app.ingredient_poc.schema import metadata
    bundle = json.loads(value)
    for short, rows in [("datasets", [bundle["dataset"]]), *bundle["tables"].items()]:
        table = metadata.tables["ingredient_poc_" + short]
        for row in rows:
            for column in table.columns:
                if row.get(column.name) is not None:
                    if isinstance(column.type, Numeric):
                        row[column.name] = Decimal(row[column.name])
                    elif isinstance(column.type, DateTime):
                        row[column.name] = datetime.fromisoformat(row[column.name])
    return bundle


def split_view(bundle, split="TRAIN"):
    """No fitting. Accessor for a single split with recipe availability as-of its cutoff.

    Validation/Test interactions are never appended to TRAIN, even for popularity.
    Evaluation sidecar and feedback are deliberately absent from this feature view.
    """
    cutoff = {"TRAIN": T1, "VALIDATION": T2, "TEST": END}[split]
    recipes = [r for r in bundle["tables"]["recipes"]
               if datetime.fromisoformat(r["provenance"]["available_at"]) < cutoff]
    allowed = {r["id"] for r in recipes}
    sales = [r for r in bundle["tables"]["sale_items"] if partition(r["purchased_at"]) == split]
    if any(r["recipe_id"] not in allowed for r in sales):
        raise ValueError("future recipe in split")
    return {"recipes": recipes, "recipe_items": [r for r in bundle["tables"]["recipe_items"]
                                                  if r["recipe_id"] in allowed], "sale_items": sales}


def write_artifacts(bundle, directory):
    """Exclusive external artifact export. Evaluation is a separate research file."""
    import hashlib
    from pathlib import Path
    from app.ingredient_poc.synthetic_validation import validate
    report = validate(bundle)
    root = Path(__file__).resolve().parents[4]
    directory = Path(directory).resolve()
    if directory == root or root in directory.parents:
        raise ValueError("dataset artifacts must be outside the repository")
    directory.mkdir(parents=True, exist_ok=False)
    payloads = {"dataset.json": {k: v for k, v in bundle.items() if k != "evaluation"},
                "evaluation.json": bundle["evaluation"], "validation.json": report,
                "manifest.json": bundle["dataset"]["manifest_json"]}
    checksums = {}
    for name, payload in payloads.items():
        data = (canonical_json(payload) + "\n").encode("utf-8")
        with (directory / name).open("xb") as output:
            output.write(data)
        checksums[name] = hashlib.sha256(data).hexdigest()
    with (directory / "artifact-checksums.json").open("x", encoding="utf-8") as output:
        output.write(canonical_json(checksums) + "\n")
    return checksums
