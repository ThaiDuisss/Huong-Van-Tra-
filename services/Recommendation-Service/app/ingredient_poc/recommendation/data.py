"""Read verified Phase 2 projections without opening the latent evaluation sidecar."""
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
from pathlib import Path

from app.ingredient_poc.contracts import content_hash, recipe_hash
from app.ingredient_poc.synthetic import (
    END, T1, T2, canonical_json, generation_config, recipe_contract,
    sale_contract, table_checksums, unit_contract,
)


def utc(value):
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if not isinstance(value, datetime) or value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("an explicit timezone is required")
    return value.astimezone(timezone.utc)


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def external(path):
    path = Path(path).resolve()
    root = Path(__file__).resolve().parents[5]
    if path == root or root in path.parents:
        raise ValueError("research outputs must be outside the repository")
    return path


class ResearchDataset:
    @classmethod
    def load(cls, path, expected_sha256):
        path = Path(path).resolve()
        if sha256(path) != expected_sha256:
            raise ValueError("dataset file SHA-256 mismatch")
        checks = json.loads((path.parent / "artifact-checksums.json").read_text(encoding="utf-8"))
        if path.name != "dataset.json" or checks.get(path.name) != expected_sha256:
            raise ValueError("wrong Phase 2 artifact identity")
        # Deliberately do not read, import, or deserialize evaluation.json.
        for name in ("manifest.json", "validation.json"):
            if checks.get(name) != sha256(path.parent / name):
                raise ValueError("Phase 2 supporting artifact checksum mismatch")
        payload = json.loads(path.read_text(encoding="utf-8"))
        if set(payload) != {"dataset", "tables"}:
            raise ValueError("only the feature projection is allowed, never latent sidecar")
        # Typed projection reload without importing ORM/DB drivers or their runtime probes.
        decimal_columns = {"conversion_factor", "portion_quantity", "servings_per_recipe", "quantity_original",
                           "quantity_canonical", "canonical_factor", "waste_rate", "quantity"}
        time_columns = {"created_at", "purchased_at", "feedback_time", "observed_at"}
        for row in [payload["dataset"], *(r for rows in payload["tables"].values() for r in rows)]:
            for name, value in row.items():
                if value is not None and name in decimal_columns:
                    row[name] = Decimal(value)
                elif value is not None and name in time_columns:
                    row[name] = utc(value)
        obj = cls()
        obj.dataset = payload["dataset"]
        obj.tables = payload["tables"]
        obj.file_sha256 = expected_sha256
        obj.file_path = str(path)
        manifest = json.loads((path.parent / "manifest.json").read_text(encoding="utf-8"))
        validation = json.loads((path.parent / "validation.json").read_text(encoding="utf-8"))
        if canonical_json(manifest) != canonical_json(obj.dataset["manifest_json"]):
            raise ValueError("source manifest differs from dataset")
        if validation.get("status") != "PASS" or validation.get("reasons"):
            raise ValueError("Phase 2 validation did not pass")
        obj._validate()
        if validation["statistics"].get("source_snapshot_hash") != obj.dataset["source_snapshot_hash"]:
            raise ValueError("validation provenance mismatch")
        obj._index()
        return obj

    def _validate(self):
        ds, t = self.dataset, self.tables
        if ds["source_kind"] != "SYNTHETIC" or ds["source_system"] != "research:hvtra-synthetic-v2":
            raise ValueError("only verified synthetic research data is supported")
        manifest = ds["manifest_json"]
        config = manifest["config"]
        if content_hash(config) != content_hash(generation_config(config["seed"])):
            raise ValueError("unsupported generator/basis policy")
        checks = table_checksums(t)
        fingerprint = content_hash({"config": config, "table_checksums": checks})
        if (checks != manifest["table_checksums"] or manifest["content_fingerprint"] != fingerprint
                or manifest["counts"] != {k: len(v) for k, v in t.items()}
                or ds["source_snapshot_hash"] != content_hash(
                    {"content_fingerprint": fingerprint, "evaluation_hash": manifest["evaluation_hash"]})):
            raise ValueError("dataset content fingerprint mismatch")
        indexes = {k: {r["id"]: r for r in rows} for k, rows in t.items()}
        for short, rows in t.items():
            if len(indexes[short]) != len(rows):
                raise ValueError("duplicate identity")
            for row in rows:
                if row["dataset_id"] != ds["id"] or row["source_kind"] != "SYNTHETIC":
                    raise ValueError("cross-dataset/source row")
                if "source_system" in row and row["source_system"] != ds["source_system"]:
                    raise ValueError("source namespace mismatch")
                for k in ("source_product_id", "source_variant_id", "source_ingredient_id", "source_unit_id"):
                    if row.get(k) is not None:
                        raise ValueError("synthetic source identity must be null")
        for unit in t["units"]:
            unit_contract(unit)
            if unit["validation_status"] != "VALIDATED":
                raise ValueError("unvalidated unit")
        items = defaultdict(list)
        for i in t["recipe_items"]:
            items[i["recipe_id"]].append(i)
            u = indexes["units"][i["unit_id"]]
            if (i["validation_status"] != "VALIDATED" or i["canonical_unit"] != u["canonical_unit"]
                    or i["canonical_factor"] != u["conversion_factor"]
                    or i["unit_symbol_snapshot"] != u["symbol"]
                    or i["ingredient_code_snapshot"] != indexes["ingredients"][i["ingredient_id"]]["ingredient_code"]
                    or i["canonical_unit"] != ("ml" if i["ingredient_role"] == "DILUENT" else "g")):
                raise ValueError("invalid ingredient/unit snapshot")
        for r in t["recipes"]:
            contract = recipe_contract(r, items[r["id"]], indexes["units"], indexes["ingredients"])
            if (r["validation_status"] != "VALIDATED" or r["recipe_hash"] != recipe_hash(contract)
                    or r["portion_quantity"] != Decimal(500) or r["portion_unit"] != "ml"
                    or r["servings_per_recipe"] != 1 or not r["provenance"].get("not_shop_approved")):
                raise ValueError("invalid research recipe/basis")
            utc(r["provenance"]["available_at"])
        for v in t["variants"]:
            r = v["provenance"]["bridge_recipe_id"]
            if (r not in indexes["recipes"] or v["provenance"]["bridge_recipe_id"]
                    != indexes["products"][v["product_id"]]["provenance"]["bridge_recipe_id"]):
                raise ValueError("unverified Product->Recipe bridge")
        seen, splits = set(), defaultdict(set)
        for sale in t["sale_items"]:
            contract = sale_contract(sale)
            key = sale["order_id"], sale["order_item_id"]
            order = indexes["orders"][sale["order_id"]]
            if (key in seen or contract.payload_hash() != sale["payload_hash"]
                    or order["customer_id"] != sale["customer_id"]
                    or sale["customer_id"] not in indexes["customers"]
                    or sale["recipe_id"] not in indexes["recipes"]):
                raise ValueError("invalid purchase identity/payload")
            if (utc(indexes["recipes"][sale["recipe_id"]]["provenance"]["available_at"]) > sale["purchased_at"]
                    or order["order_status"] != "COMPLETED" or order["payment_status"] != "PAID"):
                raise ValueError("purchase availability/status mismatch")
            seen.add(key)
            splits[sale["order_id"]].add("TRAIN" if sale["purchased_at"] < T1 else "VALIDATION" if sale["purchased_at"] < T2 else "TEST")
            if sale["purchase_type"] == "PRODUCT":
                v = indexes["variants"][sale["variant_id"]]
                if v["product_id"] != sale["product_id"] or v["provenance"]["bridge_recipe_id"] != sale["recipe_id"]:
                    raise ValueError("purchase product bridge mismatch")
        if any(len(v) != 1 for v in splits.values()):
            raise ValueError("an order crosses temporal boundaries")

    def _index(self):
        self.id = self.dataset["id"]
        for name in ("customers", "recipes", "ingredients", "units", "products", "variants"):
            setattr(self, name, {r["id"]: r for r in self.tables[name]})
        self.items = defaultdict(list)
        self.histories = defaultdict(list)
        self.feedback = defaultdict(list)
        self.sales = {r["id"]: r for r in self.tables["sale_items"]}
        for i in self.tables["recipe_items"]:
            self.items[i["recipe_id"]].append(i)
        for sale in self.tables["sale_items"]:
            self.histories[sale["customer_id"]].append(sale)
        for rows in self.histories.values():
            rows.sort(key=lambda r: (r["purchased_at"], r["order_id"], r["order_item_id"]))
        for f in self.tables["feedback"]:
            sale = self.sales[f["sale_item_id"]]
            self.feedback[sale["customer_id"]].append(f)
        for rows in self.feedback.values():
            rows.sort(key=lambda r: (r["feedback_time"], r["id"]))

    def history(self, customer_id, as_of):
        as_of = utc(as_of)
        result = []
        for row in self.histories.get(customer_id, []):
            if row["purchased_at"] >= as_of:
                break
            result.append(row)
        return result

    def available(self, recipe_id, as_of):
        r = self.recipes[recipe_id]
        return (r["validation_status"] == "VALIDATED" and r["recipe_kind"] == "CUSTOM"
                and utc(r["provenance"]["available_at"]) <= as_of)

    def candidates(self, as_of):
        as_of = utc(as_of)
        result = []
        for r in sorted(self.recipes.values(), key=lambda r: r["id"]):
            if self.available(r["id"], as_of):
                result.append({"recommendation_type": "CUSTOM_BOM", "item_id": r["id"], "recipe_id": r["id"]})
        for v in sorted(self.variants.values(), key=lambda r: r["id"]):
            p = self.products[v["product_id"]]
            rid = v["provenance"]["bridge_recipe_id"]
            if self.available(rid, as_of) and max(v["created_at"], p["created_at"]) <= as_of:
                result.append({"recommendation_type": "PRODUCT", "item_id": p["id"],
                               "variant_id": v["id"], "recipe_id": rid})
        return result

    def cohort(self, customer_id, as_of=END):
        types = {r["purchase_type"] for r in self.history(customer_id, as_of)}
        return "MIXED" if len(types) == 2 else "PRODUCT_ONLY" if types == {"PRODUCT"} else "BOM_ONLY" if types else "NO_HISTORY"
