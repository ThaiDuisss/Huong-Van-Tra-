"""Loopback-only Java Gateway smoke fixture; no business DB/Kafka/artifact I/O."""
from pathlib import Path
import sys
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi import FastAPI
import uvicorn

from app.api.recommendations import router, get_recommendation_service
from app.database.session import get_db
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory
from app.service.recommendation_service import RecommendationService
from tests.test_v1_recommendations import artifact, catalog
from app.api.ai_reports import router as report_router, get_report_service
from app.models.ai_report import AiReport, AiReportImport
from app.models.business_report import RecommendationSnapshot
from app.service.ai_report_service import AiReportService
from tests.test_ai_reports import Files
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool


model = artifact()
def popularity(db, excluded, limit, eligible_ids=None):
    values = dict(zip(model.product_ids, model.popularity))
    return [(p.product_id,p.product_code,p.name,float(values[p.product_id]))
        for p in sorted(catalog(),key=lambda p:(-values[p.product_id],p.product_id))
        if p.product_id not in excluded and (eligible_ids is None or p.product_id in eligible_ids)][:limit]

repository = SimpleNamespace(customer_history=lambda *_:CustomerPurchaseHistory(2,{'a':3,'c':1}),
    active_catalog=lambda *_:catalog(), popularity=popularity)
service = RecommendationService(SimpleNamespace(load=lambda:model),repository)
app = FastAPI()
app.include_router(router)
app.include_router(report_router)
report_engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
AiReport.__table__.create(report_engine)
AiReportImport.__table__.create(report_engine)
RecommendationSnapshot.__table__.create(report_engine)
report_files = Files()
report_files.owner = "11111111-1111-4111-8111-111111111111"
report_service = AiReportService(report_files)
def fixture_db():
    with Session(report_engine) as db:
        yield db
app.dependency_overrides[get_db] = fixture_db
app.dependency_overrides[get_report_service] = lambda:report_service
app.dependency_overrides[get_recommendation_service] = lambda:service

@app.get('/health')
def health():
    return {'status':'ISOLATED_FIXTURE'}

if __name__ == '__main__':
    uvicorn.run(app,host='127.0.0.1',port=int(sys.argv[1]),log_level='error')
