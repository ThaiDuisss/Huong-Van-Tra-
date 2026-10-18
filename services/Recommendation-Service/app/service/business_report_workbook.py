"""Integer business counts, deterministic next-order matching, four Vietnamese sheets."""
from collections import defaultdict
from bisect import bisect_right
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import hashlib
from io import BytesIO

from fastapi import HTTPException
from openpyxl import Workbook
from openpyxl.comments import Comment
from sqlalchemy import select

from app.models.business_report import CompletedPurchaseOrder, RecommendationSnapshot
from app.models.product import Product
from app.service.business_report_excel import (WIDTHS, add_kpis, add_methodology, finish_print_area,
                                              prepare_sheet, write_table)

LOCAL = timezone(timedelta(hours=7))
SHEETS = ["TỔNG QUAN BÁO CÁO", "CHI TIẾT THEO KHÁCH HÀNG", "THỐNG KÊ SẢN PHẨM", "ĐỐI CHIẾU KẾT QUẢ GỢI Ý"]
STATUSES = {"MATCHED": "Trùng {count} món", "NO_OVERLAP": "Không trùng", "NO_PURCHASE": "Chưa có đơn",
            "NOT_YET_EVALUATED": "Chưa đủ thời gian đánh giá",
            "SUPERSEDED": "Đơn được đối chiếu với lần gợi ý mới hơn"}


def period_bounds(from_date, to_date):
    start = datetime.combine(from_date, datetime.min.time(), LOCAL).astimezone(timezone.utc).replace(tzinfo=None)
    end = datetime.combine(to_date + timedelta(days=1), datetime.min.time(), LOCAL).astimezone(timezone.utc).replace(tzinfo=None)
    return start, end


def customer_code(customer_id):
    return "KH-" + hashlib.sha256(customer_id.encode()).hexdigest()[:16].upper()


def purchased_products(order):
    quantities = defaultdict(int)
    for line in order.lines:
        quantity = Decimal(line["quantity"])
        if not quantity.is_finite() or quantity <= 0 or quantity != quantity.to_integral_value():
            # Cups must not silently round fractional source quantities.
            raise HTTPException(422, "Purchase quantities cannot be reported as integer cups")
        quantities[line["product_id"]] += int(quantity)
    return dict(quantities)


def attribute(snapshots, orders, as_of):
    """Use the first strictly later order, then nearest-prior among its candidates.

    This is performed before period filtering, preventing double attribution across
    adjacent reports. Ties: timestamp then stable UUID descending. Window end is
    inclusive. Losing candidates never fall forward to a later order.
    """
    by_customer = defaultdict(list)
    for order in sorted(orders, key=lambda o: (o.occurred_at, o.id)):
        if order.customer_id and order.occurred_at <= as_of and order.observed_at <= as_of:
            by_customer[order.customer_id].append(order)
    candidates, first_orders = defaultdict(list), {}
    times = {customer: [o.occurred_at for o in values] for customer, values in by_customer.items()}
    for snapshot in snapshots:
        if snapshot.generated_at > as_of or snapshot.observed_at > as_of or snapshot.mode == "LEGACY_ALL":
            continue
        expiry = snapshot.generated_at + timedelta(days=snapshot.evaluation_days)
        values = by_customer[snapshot.customer_id]
        position = bisect_right(times.get(snapshot.customer_id, []), snapshot.generated_at)
        first = values[position] if position < len(values) and values[position].occurred_at <= expiry else None
        if first:
            first_orders[snapshot.id] = first
            candidates[first.id].append(snapshot)
    winners = {order_id: max(values, key=lambda s: (s.generated_at, s.id)).id
               for order_id, values in candidates.items()}
    results = {}
    for snapshot in snapshots:
        if snapshot.mode == "LEGACY_ALL":
            continue
        order = first_orders.get(snapshot.id)
        products = {i["product_id"] for i in snapshot.response["items"]}
        if order and winners[order.id] == snapshot.id:
            matches = sorted(products & purchased_products(order).keys())
            results[snapshot.id] = dict(status="MATCHED" if matches else "NO_OVERLAP",
                                       order=order, matches=matches)
        elif order:
            results[snapshot.id] = dict(status="SUPERSEDED", order=None, matches=None, competing_order_id=order.id,
                                       winner_id=winners[order.id])
        else:
            status = "NO_PURCHASE" if as_of >= snapshot.generated_at + timedelta(days=snapshot.evaluation_days) else "NOT_YET_EVALUATED"
            results[snapshot.id] = dict(status=status, order=None, matches=[] if status == "NO_PURCHASE" else None)
    return results


def build_workbook(db, from_date, to_date, as_of, max_rows=20000):
    start, end = period_bounds(from_date, to_date)
    # Max allowed evaluation window is 90 days, including competing snapshots
    # outside the selected period. Exports are bounded, never silently truncated.
    snapshots = list(db.scalars(select(RecommendationSnapshot).where(
        RecommendationSnapshot.generated_at >= start - timedelta(days=90),
        RecommendationSnapshot.generated_at < end + timedelta(days=90),
        RecommendationSnapshot.generated_at <= as_of, RecommendationSnapshot.observed_at <= as_of,
    ).order_by(RecommendationSnapshot.generated_at, RecommendationSnapshot.id).limit(max_rows + 1)))
    orders = list(db.scalars(select(CompletedPurchaseOrder).where(
        CompletedPurchaseOrder.occurred_at > start - timedelta(days=90),
        CompletedPurchaseOrder.occurred_at < end + timedelta(days=90),
        CompletedPurchaseOrder.occurred_at <= as_of, CompletedPurchaseOrder.observed_at <= as_of,
    ).order_by(CompletedPurchaseOrder.occurred_at, CompletedPurchaseOrder.id).limit(max_rows + 1)))
    if len(snapshots) > max_rows or len(orders) > max_rows or sum(len(o.lines) for o in orders) > max_rows * 5:
        raise HTTPException(422, "Report too large; select a shorter period")
    results = attribute(snapshots, orders, as_of)
    selected = [s for s in snapshots if start <= s.generated_at < end and s.mode != "LEGACY_ALL"]
    sales = [o for o in orders if start <= o.occurred_at < end]
    stats = defaultdict(lambda: dict(customers=set(), appearances=0, orders=set(), units=0, matches=0))
    names = {}
    for snapshot in selected:
        for item in snapshot.response["items"]:
            product = item["product_id"]
            names.setdefault(product, item["name"])
            stats[product]["customers"].add(snapshot.customer_id)
            stats[product]["appearances"] += 1
        for product in results[snapshot.id]["matches"] or []:
            stats[product]["matches"] += 1
    for order in sales:
        for product, quantity in purchased_products(order).items():
            stats[product]["orders"].add(order.id)
            stats[product]["units"] += quantity
    if stats:
        for product_id, product_name in db.execute(select(Product.id, Product.name).where(Product.id.in_(stats))):
            names.setdefault(product_id, product_name)
    def name(product):
        return names.get(product, "Sản phẩm chưa có tên (" + product + ")")
    wb = Workbook()
    wb.remove(wb.active)
    wb.properties.creator = "Hương Vân Trà"
    wb.properties.title = "Báo cáo gợi ý AI và hành vi mua hàng"
    local_generated = as_of.replace(tzinfo=timezone.utc).astimezone(LOCAL).replace(tzinfo=None)
    for title, widths in zip(SHEETS, WIDTHS):
        ws = wb.create_sheet(title)
        prepare_sheet(ws, from_date, to_date, local_generated, widths)
    overview = wb.worksheets[0]
    add_kpis(overview, [
        ("Khách hàng được gợi ý", len({s.customer_id for s in selected if s.response["items"]})),
        ("Lượt gợi ý sản phẩm", sum(v["appearances"] for v in stats.values())),
        ("Số ly bán thực tế", sum(v["units"] for v in stats.values())),
        ("Lượt gợi ý trùng mua", sum(v["matches"] for v in stats.values())),
    ])
    notes = [
        "Gợi ý được tạo; chưa xác nhận khách đã xem. Đối chiếu mua hàng không chứng minh tác động của AI.",
        "Khách hàng được gợi ý: đếm Mã KH khác nhau có ít nhất một sản phẩm được gợi ý trong kỳ; phản hồi rỗng không tính khách được gợi ý.",
        "Lượt gợi ý sản phẩm: mỗi sản phẩm xuất hiện một lần trong mỗi phản hồi, tối đa 5; không phải lượt xem.",
        "Số ly bán: tổng số lượng các dòng của đơn COMPLETED/PAID trong kỳ, gồm cả khách vãng lai; không trừ RETURN vận hành.",
        "Lượt trùng mua: số cặp lần gợi ý – sản phẩm có trong đơn kế tiếp được đối chiếu; mỗi sản phẩm tính 1, không theo số ly.",
        "Đơn phải xảy ra sau thời điểm gợi ý; tối đa một lần gợi ý gần nhất được đối chiếu với mỗi đơn, kể cả ngoài kỳ báo cáo.",
        "Thời hạn đối chiếu lưu theo từng lần gợi ý; mặc định 7 ngày. Có thể dùng đơn sau cuối kỳ đến lúc tạo báo cáo.",
        "Chưa đủ thời gian đánh giá và đơn gắn với lần gợi ý mới hơn không được tính là gợi ý sai.",
        "Chỉ có dữ liệu gợi ý và đơn mua đã được ghi nhận. Lịch sử trước khi bật ghi nhận có thể thiếu; không suy ra không mua.",
        "Số khách: phân biệt Mã KH cho từng sản phẩm. Số đơn có mua: phân biệt mã đơn cho từng sản phẩm; không cộng thành tổng đơn.",
        "Tên sản phẩm lấy từ lần gợi ý hoặc danh mục hiện tại; Mã KH là mã ẩn danh ổn định. Không có tên, điện thoại, email khách.",
    ]
    products = sorted(stats, key=lambda p: (name(p), p))
    overview_rows = [[n, name(p), stats[p]["appearances"], stats[p]["units"], stats[p]["matches"]] for n, p in enumerate(products, 1)]
    write_table(overview, ["STT", "Sản phẩm", "Lượt AI gợi ý", "Số ly bán", "Lượt trùng mua"], overview_rows,
                "Overview", WIDTHS[0], header=9, totals=[None, "Tổng", sum(v["appearances"] for v in stats.values()),
                                                        sum(v["units"] for v in stats.values()), sum(v["matches"] for v in stats.values())])
    add_methodology(overview, notes)
    details, comparisons, audit = [], [], []
    for snapshot in selected:
        result = results[snapshot.id]
        order = result["order"]
        quantities = purchased_products(order) if order else {}
        matched = result["matches"]
        status = STATUSES[result["status"]].format(count=len(matched or []))
        suggested = "\n".join(i["name"] for i in snapshot.response["items"]) or "Chưa có sản phẩm phù hợp"
        purchased = "\n".join(f"{name(p)} × {q}" for p, q in quantities.items()) if order else status
        matched_names = "\n".join(name(p) for p in matched) if matched else status
        mode_label = "Khám phá" if snapshot.mode == "DISCOVERY" else "Mua lại"
        details.append([customer_code(snapshot.customer_id), snapshot.generated_at.replace(tzinfo=timezone.utc).astimezone(LOCAL).replace(tzinfo=None),
                        f"{mode_label}\n{suggested}", purchased, matched_names])
        comparisons.append([snapshot.id, customer_code(snapshot.customer_id), len(snapshot.response["items"]),
                            len(quantities) if order else (0 if result["status"] == "NO_PURCHASE" else None),
                            len(matched) if matched is not None else None, status])
        audit.append(dict(snapshot_id=snapshot.id, customer_id=snapshot.customer_id, mode=snapshot.mode,
                          generated_at=snapshot.generated_at.isoformat(), observed_at=snapshot.observed_at.isoformat(),
                          source=snapshot.source, fallback_reason=snapshot.fallback_reason,
                          evaluation_days=snapshot.evaluation_days, response=snapshot.response,
                          order_id=order.id if order else None, order_source_event_id=order.source_event_id if order else None,
                          order_evidence_hash=order.evidence_hash if order else None, order_lines=order.lines if order else None,
                          status=result["status"], matches=matched, winner_id=result.get("winner_id"),
                          competing_order_id=result.get("competing_order_id")))
    detail_rows = write_table(wb.worksheets[1], ["Mã KH", "Ngày gợi ý", "AI gợi ý", "Khách mua sau đó", "Món trùng"], details,
                              "Customers", WIDTHS[1], repeat_columns=(0, 1))
    for snapshot, rows in zip(selected, detail_rows):
        ws = wb.worksheets[1]
        for row in rows:
            continuation = f"\nDòng tiếp theo của hàng {rows[0]}." if row != rows[0] else ""
            ws.cell(row, 1).comment = Comment("Mã lần gợi ý: " + snapshot.id + "\nMã đơn: " + (results[snapshot.id]["order"].id if results[snapshot.id]["order"] else "Chưa đối chiếu") + continuation, "Hương Vân Trà")
    write_table(wb.worksheets[2], ["STT", "Sản phẩm", "Số khách được gợi ý", "Lượt gợi ý", "Số đơn có mua", "Số ly bán"],
           [[n, name(p), len(stats[p]["customers"]), stats[p]["appearances"], len(stats[p]["orders"]), stats[p]["units"]]
            for n, p in enumerate(products, 1)], "Products", WIDTHS[2])
    write_table(wb.worksheets[3], ["Mã lần gợi ý", "Mã KH", "Số món gợi ý", "Số món khách mua", "Số món trùng", "Kết quả"], comparisons,
                "Matches", WIDTHS[3], repeat_columns=(0, 1))
    for ws in wb:
        finish_print_area(ws)
    output = BytesIO(); wb.save(output)
    return output.getvalue(), dict(as_of=as_of.isoformat(), period_timezone="Asia/Bangkok",
        policy="next-order-nearest-prior-v1", snapshots=audit,
        sales=[dict(order_id=o.id, source_event_id=o.source_event_id, evidence_hash=o.evidence_hash,
                    occurred_at=o.occurred_at.isoformat(), observed_at=o.observed_at.isoformat(), lines=o.lines) for o in sales],
        missing_history="No backfill; only durably observed evidence is included")
