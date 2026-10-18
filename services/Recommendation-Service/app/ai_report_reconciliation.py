"""Operator hook: retry durable pending imports; never deletes storage objects."""
import argparse
import json
from datetime import timedelta

from sqlalchemy import select

from app.clients.file_client import FileClient
from app.database.session import SessionLocal
from app.models.ai_report import AiReportImport
from app.service.ai_report_service import AiReportService, now


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--retry", action="store_true", help="Retry pending imports older than 5 minutes")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--file-orphans", action="store_true", help="List explicitly marked unbound uploads >24h old")
    parser.add_argument("--repair-bindings", action="store_true",
        help="With --retry, also verify/rebind COMPLETE intents to their existing original (writes)")
    args = parser.parse_args()
    if not 1 <= args.limit <= 1000:
        parser.error("limit must be between 1 and 1000")
    if args.repair_bindings and not args.retry:
        parser.error("--repair-bindings requires --retry; this operation can write both services")
    with SessionLocal() as db:
        states = ("PENDING", "FAILED", "COMPLETE") if args.repair_bindings else ("PENDING", "FAILED")
        rows = db.scalars(select(AiReportImport).where(AiReportImport.status.in_(states),
            AiReportImport.updated_at < now() - timedelta(minutes=5))
            .order_by(AiReportImport.updated_at, AiReportImport.id).limit(args.limit)).all()
        for row in rows:
            result = {"reportId": row.id, "fileId": row.file_id, "status": row.status, "error": row.last_error}
            if args.retry and (row.status == "PENDING" or (args.repair_bindings and row.status == "COMPLETE")):
                try:
                    AiReportService().reconcile(db, row)
                    result["result"] = "COMPLETE"
                except Exception:
                    db.rollback()
                    result["result"] = "REQUIRES_REVIEW"
            print(json.dumps(result))
    if args.file_orphans:
        print(json.dumps({"unboundFileCandidates": FileClient().orphan_candidates()}))


if __name__ == "__main__":
    main()
