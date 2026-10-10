"""Operator hook: retry durable pending imports; never deletes storage objects."""
import argparse
import json
from datetime import timedelta

from sqlalchemy import select

from app.clients.file_client import FileClient
from app.database.session import SessionLocal
from app.models.ai_report import AiReportImport
from app.schemas.ai_report import AiReportCreate
from app.service.ai_report_service import AiReportService, now


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--retry", action="store_true", help="Retry pending imports older than 5 minutes")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--file-orphans", action="store_true", help="List explicitly marked unbound uploads >24h old")
    args = parser.parse_args()
    if not 1 <= args.limit <= 1000:
        parser.error("limit must be between 1 and 1000")
    with SessionLocal() as db:
        rows = db.scalars(select(AiReportImport).where(AiReportImport.status != "COMPLETE",
            AiReportImport.updated_at < now() - timedelta(minutes=5))
            .order_by(AiReportImport.updated_at, AiReportImport.id).limit(args.limit)).all()
        for row in rows:
            result = {"reportId": row.id, "fileId": row.file_id, "status": row.status, "error": row.last_error}
            if args.retry and row.status == "PENDING":
                try:
                    AiReportService().create(db, AiReportCreate(name=row.name, version=row.version,
                        fileId=row.file_id), row.created_by)
                    result["result"] = "COMPLETE"
                except Exception:
                    db.rollback()
                    result["result"] = "REQUIRES_REVIEW"
            print(json.dumps(result))
    if args.file_orphans:
        print(json.dumps({"unboundFileCandidates": FileClient().orphan_candidates()}))


if __name__ == "__main__":
    main()
