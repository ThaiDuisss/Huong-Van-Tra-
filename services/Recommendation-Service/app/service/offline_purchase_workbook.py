"""Verify the unchanged workbook with a hard-coded SQLite memory engine only."""
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from io import BytesIO

from openpyxl import load_workbook
from sqlalchemy import create_engine, func, select, text
from sqlalchemy.orm import Session

from app.models.business_report import CompletedPurchaseOrder, RecommendationSnapshot
from app.service.business_report_workbook import SHEETS, build_workbook, period_bounds


def verify_sales_only_workbook(evidences):
    """Return aggregate checks only. Never return/save workbook bytes or raw IDs.

    No configurable engine/session: this helper cannot be pointed at MySQL.
    Observed time is the real simulation clock, never copied from historical time.
    """
    engine = create_engine("sqlite:///:memory:")
    try:
        for model in (CompletedPurchaseOrder, RecommendationSnapshot):
            model.__table__.create(engine)
        with Session(engine) as db:
            db.execute(text("CREATE TABLE products(id TEXT PRIMARY KEY,name TEXT)"))
            products = sorted({line.product_id for e in evidences for line in e.lines})
            if products:
                db.execute(text("INSERT INTO products(id,name) VALUES (:id,:name)"),
                           [dict(id=p, name="Diagnostic product") for p in products])
            observed = datetime.now(timezone.utc).replace(tzinfo=None)
            for evidence in evidences:
                for line in evidence.lines:
                    q = Decimal(line.quantity)
                    if not q.is_finite() or q <= 0 or q != q.to_integral_value():
                        raise ValueError("workbook_requires_integer_variant_units")
                db.add(CompletedPurchaseOrder(id=evidence.order_id,
                    source_event_id=evidence.source_event_id, customer_id=evidence.customer_id,
                    occurred_at=evidence.occurred_at, observed_at=observed,
                    evidence_hash=evidence.evidence_hash, lines=[line.as_dict() for line in evidence.lines]))
            db.commit()  # This session is owned by the constant SQLite memory engine.
            local = timezone(timedelta(hours=7))
            times = [e.occurred_at.replace(tzinfo=timezone.utc).astimezone(local).date()
                     for e in evidences if e.occurred_at <= observed]
            first = min(times) if times else observed.replace(tzinfo=timezone.utc).astimezone(local).date()
            last = max(times) if times else first
            checks = []
            while first <= last:
                end = min(date(first.year, 12, 31), last)
                start_utc, end_utc = period_bounds(first, end)
                selected = [e for e in evidences if start_utc <= e.occurred_at < end_utc and e.occurred_at <= observed]
                quantities, orders_by_product = {}, {}
                for evidence in selected:
                    for line in evidence.lines:
                        quantities[line.product_id] = quantities.get(line.product_id, 0) + int(Decimal(line.quantity))
                        orders_by_product.setdefault(line.product_id, set()).add(evidence.order_id)
                units = sum(quantities.values())
                content, audit = build_workbook(db, first, end, observed)
                workbook = load_workbook(BytesIO(content), data_only=True)
                if workbook.sheetnames != SHEETS or audit["snapshots"]:
                    raise ValueError("unexpected_recommendation_evidence")
                kpis = [workbook.worksheets[0].cell(row, 2).value for row in range(4, 8)]
                if kpis != [0, 0, units, 0] or len(audit["sales"]) != len(selected):
                    raise ValueError("sales_only_kpi_mismatch")
                product_rows = [row for row in workbook.worksheets[2].iter_rows(min_row=4, values_only=True)
                                if isinstance(row[0], int)]
                if len(product_rows) != len(quantities) or sum(row[5] for row in product_rows) != units:
                    raise ValueError("product_table_quantity_mismatch")
                if any(row[2] != 0 or row[3] != 0 for row in product_rows):
                    raise ValueError("unexpected_product_suggestions")
                if sorted(row[4] for row in product_rows) != sorted(len(v) for v in orders_by_product.values()):
                    raise ValueError("product_table_order_mismatch")
                for index in (1, 3):
                    if workbook.worksheets[index].tables:
                        raise ValueError("unexpected_recommendation_detail_rows")
                checks.append(dict(from_date=str(first), to_date=str(end), orders=len(selected),
                    lines=sum(len(e.lines) for e in selected), variant_units=units,
                    kpis=kpis, product_rows=len(product_rows), sheets=workbook.sheetnames,
                    recommendation_detail_rows=0, matching_rows=0))
                first = end + timedelta(days=1)
            if db.scalar(select(func.count()).select_from(RecommendationSnapshot)) != 0:
                raise ValueError("unexpected_snapshot_rows")
            return dict(engine="sqlite:///:memory:", snapshots=0, inserted_orders=len(evidences),
                        unit_label_warning="Existing KPI label says cups; source quantities are variant sale units.",
                        periods=checks)
    finally:
        engine.dispose()
