"""Trusted, lazy research artifact adapter for official AI report exports.

No demo HTTP/token/auth mode, Product history, training, or filesystem persistence.
Persistence is the existing BusinessReportExport/File-Service lifecycle.
"""
from dataclasses import dataclass
from datetime import timedelta
from io import BytesIO
from pathlib import Path
import re

from fastapi import HTTPException
from openpyxl import load_workbook

from app.ingredient_demo.bom_report import PROTOCOL
from app.ingredient_demo.service import IngredientDemoService
from app.ingredient_demo.workbook import WORKBOOK_SCHEMA, verify_workbook
from app.ingredient_poc.synthetic import END, T2
from app.schemas.business_report import BOM_FROM, BOM_TO


@dataclass(frozen=True, repr=False)
class ReportArtifacts:
    dataset_path: Path
    dataset_sha256: str
    dataset_id: str
    model_path: Path
    model_sha256: str


def load_research(cfg):
    if not cfg.ai_report_bom_enabled:
        raise HTTPException(503, "Official BOM research export is not configured")
    try:
        artifacts = ReportArtifacts(
            Path(cfg.ai_report_bom_dataset_path or ""), cfg.ai_report_bom_dataset_sha256 or "",
            cfg.ai_report_bom_dataset_id or "", Path(cfg.ai_report_bom_model_path or ""),
            cfg.ai_report_bom_model_sha256 or "")
        if (not artifacts.dataset_id or not artifacts.dataset_path.is_absolute() or not artifacts.model_path.is_absolute()
                or not re.fullmatch(r"[0-9a-f]{64}", artifacts.dataset_sha256)
                or not re.fullmatch(r"[0-9a-f]{64}", artifacts.model_sha256)
                or T2.date() != BOM_FROM or (END - timedelta(days=1)).date() != BOM_TO):
            raise ValueError("Invalid research artifact configuration")
        service = IngredientDemoService(artifacts)
        service.verify_integrity()
        return service, artifacts
    except Exception:
        raise HTTPException(503, "Verified BOM dataset/model unavailable or incompatible") from None


def descriptor(service, artifacts):
    return {"kind": "SYNTHETIC_BOM_RESEARCH", "source_kind": "SYNTHETIC", "research_only": True,
            "production_approved": False, "dataset_id": artifacts.dataset_id,
            "dataset_sha256": artifacts.dataset_sha256, "model_sha256": artifacts.model_sha256,
            "model_version": service._model.metadata["model_version"],
            "model_fingerprint": service._model.payload()["model_fingerprint"],
            "source_snapshot_hash": service.data.dataset["source_snapshot_hash"],
            "protocol": {"name": PROTOCOL, "mode": "CUSTOM_BOM", "policy": "REPEAT", "top_k": 5,
                         "as_of": T2.isoformat(), "label_end_exclusive": END.isoformat()}}


def select_bom_source(cfg):
    return descriptor(*load_research(cfg))


def build_bom(cfg, pinned):
    service, artifacts = load_research(cfg)
    if descriptor(service, artifacts) != pinned:
        raise HTTPException(409, "BOM export is pinned to different artifacts; restore them or use a new requestId")
    try:
        content = service.excel()  # New exports use V3; the lifecycle retains frozen V2 bytes.
        snapshot = service.report_snapshot()
        service.verify_integrity()
        workbook = load_workbook(BytesIO(content))
        try:
            workbook_evidence = verify_workbook(workbook, service.data, snapshot)
        finally:
            workbook.close()
        return content, {"report_type": "CUSTOM_BOM", "report_source": pinned,
                         "protocol": snapshot["protocol"], "summary": snapshot["summary"],
                         "workbook_schema": WORKBOOK_SCHEMA, "workbook_evidence": workbook_evidence}
    except Exception:
        raise HTTPException(503, "BOM research workbook verification failed") from None
