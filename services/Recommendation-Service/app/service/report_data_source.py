"""Explicit report-source selection; never infer source from evidence-table contents."""
from datetime import timezone
import logging

from fastapi import HTTPException

from offline_benchmark.evaluation.bundle import BundleProvider
from offline_benchmark.reporting.evaluation_workbook import (
    MARKER, PRESENTATION_SCHEMA, display_identifiers, export_workbook, verify_workbook,
)

logger = logging.getLogger(__name__)
DEMO_MARKER = MARKER
OPERATIONAL = {"kind": "OPERATIONAL", "period_policy": "operational-generated-at-v1"}


def load_bundle(cfg):
    if cfg.app_environment == "production":
        raise HTTPException(503, "Offline report source is restricted to development/test")
    if not cfg.ai_report_offline_bundle_path or not cfg.ai_report_offline_bundle_sha256:
        raise HTTPException(503, "Configure verified offline report bundle path and SHA-256")
    try:
        return BundleProvider(cfg.ai_report_offline_bundle_path, cfg.ai_report_offline_bundle_sha256).load()
    except Exception:
        logger.error("Offline report bundle verification failed; prepare a compatible verified bundle")
        raise HTTPException(503, "Offline report bundle unavailable or incompatible; prepare/rebuild verified bundle") from None


def select_source(cfg):
    if cfg.ai_report_data_source == "OPERATIONAL":
        return dict(OPERATIONAL)
    if cfg.ai_report_data_source == "OFFLINE_EVALUATION":
        return load_bundle(cfg).descriptor()
    raise HTTPException(503, "AI_REPORT_DATA_SOURCE must explicitly select OPERATIONAL or OFFLINE_EVALUATION")


def build_offline(cfg, pinned, from_date, to_date, generated_at, max_rows):
    bundle = load_bundle(cfg)
    if bundle.descriptor() != pinned:
        raise HTTPException(409, "Export intent is pinned to a different bundle; restore that bundle or use a new requestId")
    selected = bundle.for_period(from_date, to_date)
    result = selected.result
    if not result.rows:
        raise HTTPException(422, "No complete Test orders in this source-calendar period; choose a period containing Test data")
    if selected.events_selected + len(result.frozen.predictions) > max_rows:
        raise HTTPException(422, "Report evidence exceeds limit; select a shorter period")
    notes = [
        "Kỳ báo cáo chỉ chọn các đơn mua hàng đối chiếu nằm trọn trong khoảng ngày; múi giờ dữ liệu nguồn chưa xác minh.",
        "Mỗi khách dùng một danh sách đề xuất đã có sẵn. Sản phẩm mua và các tỷ lệ chỉ tính từ những đơn được chọn trong kỳ.",
        f"Đơn mua được đối chiếu trong kỳ: {selected.orders_selected}; ngoài hoặc không trọn kỳ: {selected.orders_outside}; đơn cắt biên bị loại: {selected.orders_spanning_boundary}.",
    ]
    content = export_workbook(result, bundle.dataset.products, bundle.dataset.manifest, generated_at.replace(tzinfo=timezone.utc),
                              (from_date, to_date), source="VERIFIED_BUNDLE", marker=DEMO_MARKER,
                              period_notes=notes)
    checks = verify_workbook(content, result, marker=DEMO_MARKER)
    evidence = {"report_source": pinned, "period": {"from": from_date.isoformat(), "to": to_date.isoformat(),
                "policy": pinned["period_policy"], "orders_selected": selected.orders_selected,
                "events_selected": selected.events_selected, "orders_outside": selected.orders_outside,
                "orders_spanning_boundary": selected.orders_spanning_boundary},
                "period_prediction_sha256": result.frozen.sha256, "metrics": result.metrics,
                "workbook_verification": checks,
                "presentation": {"schema": PRESENTATION_SCHEMA, "display_identifiers": display_identifiers(result),
                    "rate_contract": "matched_opportunity_product_pairs / recommended_opportunity_product_pairs",
                    "percentage_format": "0.00%", "quantity": "NOT_VERIFIED"}}
    return content, evidence
