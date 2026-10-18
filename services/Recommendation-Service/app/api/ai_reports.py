from uuid import UUID

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy.orm import Session

from app.core.ai_report_auth import ReportUser, require_report_user
from app.database.session import get_db
from app.schemas.ai_report import AiReportCreate, AiReportMetadata, AiReportPage, AiReportResponse
from app.service.ai_report_service import AiReportService

router = APIRouter(prefix="/api/recommendation/ai-reports", tags=["AI Reports"])


def get_report_service():
    return AiReportService()


@router.get("", response_model=AiReportPage)
def list_reports(page: int = Query(0, ge=0), size: int = Query(20, ge=1, le=100),
    user: ReportUser = Depends(require_report_user), db: Session = Depends(get_db),
    service: AiReportService = Depends(get_report_service)):
    return service.list(db, page, size)


@router.post("", response_model=AiReportResponse, status_code=201)
def create_report(payload: AiReportCreate, response: Response,
    user: ReportUser = Depends(require_report_user), db: Session = Depends(get_db),
    service: AiReportService = Depends(get_report_service)):
    report, created = service.create(db, payload, user.id)
    response.status_code = 201 if created else 200
    return report


@router.patch("/{report_id}", response_model=AiReportResponse)
def update_report(report_id: UUID, payload: AiReportMetadata,
    user: ReportUser = Depends(require_report_user), db: Session = Depends(get_db),
    service: AiReportService = Depends(get_report_service)):
    return service.update(db, str(report_id), payload, user.id)


@router.get("/{report_id}/download")
def download_report(report_id: UUID, user: ReportUser = Depends(require_report_user),
    db: Session = Depends(get_db), service: AiReportService = Depends(get_report_service)):
    return service.files.download(service.require(db, str(report_id)))
