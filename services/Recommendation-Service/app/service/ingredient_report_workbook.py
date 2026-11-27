from collections import Counter, defaultdict
from io import BytesIO
from uuid import UUID

from fastapi import HTTPException
from openpyxl import Workbook
from openpyxl.cell import WriteOnlyCell
from openpyxl.styles import Font
from sqlalchemy import select

from app.clients.bom_catalog_client import CatalogBomUnavailable, fetch_bom_recipes
from app.models.business_report import IngredientRecommendationLog
from app.repositories.explore_history_repository import ExploreHistoryError
from app.repositories.ingredient_history_repository import IngredientEvidenceLimit, retained_lines
from app.service.business_report_workbook import period_bounds
from app.service.ingredient_recommendation_service import DIRECT, PAIR, PRIOR, PROXY, recipe_for_line

INGREDIENT_SOURCE = {"kind": "OPERATIONAL", "version": "hvtra-ingredient-report-v1"}


def _table(sheet, header, rows):
    sheet.freeze_panes = "B2"
    labels = []
    for title in header:
        cell = WriteOnlyCell(sheet, title)
        cell.font = Font(bold=True, color="234F43")
        labels.append(cell)
    sheet.append(labels)
    for row in rows:
        cells = []
        for value in row:
            cell = WriteOnlyCell(sheet, value)
            if isinstance(value, str):
                cell.data_type = "s"
            cells.append(cell)
        sheet.append(cells)


def build_ingredient_workbook(db, settings, from_date, to_date, as_of):
    start, end = period_bounds(from_date, to_date)
    max_rows = settings.ai_report_max_evidence_rows
    try:
        lines = retained_lines(db, None, as_of, include_bundles=True, start=start, end=end, max_orders=max_rows)
    except IngredientEvidenceLimit:
        raise HTTPException(422, "Ingredient evidence exceeds report limit; select a shorter period") from None
    except (ExploreHistoryError, ValueError):
        raise HTTPException(503, "Ingredient report purchase evidence unavailable for this period") from None
    logs = db.scalars(select(IngredientRecommendationLog).where(
        IngredientRecommendationLog.generated_at >= start,
        IngredientRecommendationLog.generated_at < end,
        IngredientRecommendationLog.generated_at <= as_of,
    ).limit(max_rows + 1)).all()
    if len(logs) > max_rows or len(lines) > max_rows:
        raise HTTPException(422, "Ingredient evidence exceeds report limit; select a shorter period")

    try:
        ids = {line.variant_id for line in lines if line.purchase_type == "PRODUCT" and line.quantity > 0}
        recipes = fetch_bom_recipes(settings, ids, timeout=settings.taste_catalog_timeout_seconds)
    except CatalogBomUnavailable:
        raise HTTPException(503, "Catalog recipes unavailable for Ingredient report") from None

    order_sets = defaultdict(set)
    line_counts, bought, retained = Counter(), Counter(), Counter()
    names = {}
    for line in lines:
        if line.purchase_type == "CUSTOM_BUNDLE":
            components = {UUID(item["ingredient_id"]): item["ingredient_code"] for item in line.components}
            for ingredient, code in components.items():
                key = (DIRECT, ingredient)
                names.setdefault(key, code)
                order_sets[key].add(line.order_id)
                line_counts[key] += 1
                bought[key] += line.purchased_quantity
                retained[key] += line.quantity
        elif line.quantity > 0 and (recipe := recipe_for_line(line, recipes)):
            for ingredient, name in recipe.names.items():
                key = (PROXY, ingredient)
                names.setdefault(key, name)
                order_sets[key].add(line.order_id)
                line_counts[key] += 1
                retained[key] += line.quantity

    recommendations = defaultdict(Counter)
    display = {}
    for log in sorted(logs, key=lambda row: (row.generated_at, row.id)):
        for item in log.items:
            key = item["ingredientId"]
            display[key] = item["ingredientCode"], item["ingredientName"]
            recommendations[key]["total"] += 1
            recommendations[key].update(item["evidenceSources"])

    has_data = bool(order_sets or recommendations)
    workbook = Workbook(write_only=True)
    overview = workbook.create_sheet("Overview")
    _table(overview, ("Field", "Value"), (
        ("From (GMT+7, inclusive)", from_date.isoformat()), ("To (GMT+7, inclusive)", to_date.isoformat()),
        ("Status", "OK" if has_data else "NO_DATA"), ("Issued ingredient lists", len(logs)),
        ("Recommendation count", "One occurrence per ingredient per served response. Refreshes can count again."),
        ("Direct evidence", "Signed CUSTOM_BUNDLE components. Order window uses completion date."),
        ("Retained bundle quantity", "Original bundle units less signed completed returns observed by export time."),
        ("Inferred evidence", "Retained product line mapped to its current active BOM at export time."),
        ("Scope", "Aggregate evidence without recommendation-to-purchase linkage."),
    ))
    _table(workbook.create_sheet("Recommendations"),
           ("Ingredient ID", "Code", "Name", "Times issued", PROXY, DIRECT, PAIR, PRIOR),
           ((key, *display[key], *(counts[source] for source in ("total", PROXY, DIRECT, PAIR, PRIOR)))
            for key, counts in sorted(recommendations.items(), key=lambda item: (-item[1]["total"], item[0]))))
    _table(workbook.create_sheet("Direct bundle purchases"),
           ("Ingredient ID", "Code", "Orders", "Order lines", "Purchased bundle units", "Retained bundle units"),
           ((str(key[1]), names[key], len(order_sets[key]), line_counts[key], bought[key], retained[key])
            for key in sorted(order_sets) if key[0] == DIRECT))
    _table(workbook.create_sheet("Inferred product BOM"),
           ("Ingredient ID", "Name", "Orders", "Retained order lines", "Retained product units", "Source quality"),
           ((str(key[1]), names[key], len(order_sets[key]), line_counts[key], retained[key], "CURRENT_BOM_PROXY")
            for key in sorted(order_sets) if key[0] == PROXY))
    buffer = BytesIO()
    workbook.save(buffer)
    return buffer.getvalue(), {
        "report_type": "INGREDIENT", "report_source": INGREDIENT_SOURCE,
        "data_status": "OK" if has_data else "NO_DATA",
        "issued_lists": len(logs), "recommended_ingredients": len(recommendations),
        "direct_ingredients": sum(key[0] == DIRECT for key in order_sets),
        "inferred_ingredients": sum(key[0] == PROXY for key in order_sets),
    }
