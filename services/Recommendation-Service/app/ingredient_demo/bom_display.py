"""Presentation identities and original snapshot amounts; never used for matching."""
from decimal import Decimal, InvalidOperation


INCOMPLETE = "Chưa đủ thông tin thành phần"


def source_text(value):
    return value.strip() if isinstance(value, str) and value.strip() else None


def recipe_codes(data):
    return {rid: f"CT{n:03d}" for n, rid in enumerate(sorted(data.recipes), 1)}


def customer_codes(data):
    """Workbook-local audit mapping, independent of inference/record iteration order."""
    return {cid: f"KH{n:03d}" for n, cid in enumerate(sorted(data.customers), 1)}


def recipe_names(data):
    # name_snapshot is optional; family labels are not commercial recipe names.
    return {rid: source_text(data.recipes[rid].get("name_snapshot")) or f"Công thức {code}"
            for rid, code in recipe_codes(data).items()}


def recipe_components(data, rid):
    """All original ingredient lines, or one explicit incomplete marker for the recipe.

    Do not silently present an incomplete recipe as complete. No conversion, waste,
    serving inference, family reconstruction or translation of source names here.
    """
    units = {u["id"]: u for u in data.tables.get("units", [])}
    ingredients = getattr(data, "ingredients", {})
    items = getattr(data, "items", {}).get(rid, [])
    result, seen = [], set()
    try:
        if not items:
            raise ValueError("Empty recipe")
        for item in items:
            ingredient = ingredients[item["ingredient_id"]]
            unit = units[item["unit_id"]]
            symbol = source_text(item.get("unit_symbol_snapshot"))
            quantity = Decimal(str(item["quantity_original"]))
            supported_units = {"g": "MASS", "kg": "MASS", "ml": "VOLUME",
                               "l": "VOLUME", "L": "VOLUME", "count": "COUNT"}
            if (item["recipe_id"] != rid or item["ingredient_id"] in seen
                    or item.get("ingredient_code_snapshot") != ingredient.get("ingredient_code")
                    or not symbol or symbol != source_text(unit.get("symbol"))
                    or symbol not in supported_units or supported_units[symbol] != unit.get("dimension")
                    or not quantity.is_finite() or quantity <= 0
                    or item.get("validation_status") != "VALIDATED"
                    or unit.get("validation_status") != "VALIDATED"):
                raise ValueError("Invalid snapshot reference or amount")
            seen.add(item["ingredient_id"])
            name = (source_text(ingredient.get("name_snapshot"))
                    or source_text(ingredient.get("ingredient_code")) or item["ingredient_id"])
            result.append({"ingredient_id": item["ingredient_id"], "name": name,
                           "quantity": quantity, "unit": symbol})
    except (KeyError, TypeError, ValueError, InvalidOperation):
        return [{"ingredient_id": None, "name": INCOMPLETE, "quantity": None, "unit": None}]
    return sorted(result, key=lambda r: (r["name"], r["ingredient_id"]))
