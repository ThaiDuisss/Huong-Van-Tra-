"""Isolated existing-route export verification: SQLite and mocked File/storage HTTP boundaries."""
import argparse
from contextlib import ExitStack
from datetime import date, datetime, timezone
import hashlib
import hmac
import json
import os
from pathlib import Path
import sys
from uuid import uuid4
from unittest.mock import patch

SERVICE = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(SERVICE))


def verify_export(bundle_path, bundle_sha256, from_date, to_date):
    import httpx
    from fastapi import FastAPI
    from fastapi.testclient import TestClient
    from sqlalchemy import create_engine, select
    from sqlalchemy.orm import Session
    from sqlalchemy.pool import StaticPool
    from app.api.ai_reports import router, get_business_report_service
    from app.clients.file_client import FileClient, FILE_MIMES
    from app.core.settings import Settings
    from app.database.session import get_db
    from app.models.business_report import BusinessReportExport, CompletedPurchaseOrder, RecommendationSnapshot
    from app.service.business_report_service import BusinessReportService
    from app.service.report_data_source import DEMO_MARKER
    from offline_benchmark.evaluation.bundle import BundleProvider, sha256
    from offline_benchmark.reporting.evaluation_workbook import verify_workbook

    cfg=Settings(_env_file=None,mysql_user='fixture',mysql_password='fixture',app_environment='test',
        internal_header_secret='isolated-report-verification',internal_replay_backend='memory',
        ai_report_data_source='OFFLINE_EVALUATION',ai_report_offline_bundle_path=str(bundle_path),
        ai_report_offline_bundle_sha256=bundle_sha256,file_service_url='http://file.fixture',
        ai_report_storage_origins='http://storage.fixture')
    user=str(uuid4()); stored={}; calls=[]
    def signed(role='MANAGER'):
        headers={'X-User-ID':user,'X-User_Roles':role,'X-Request-ID':str(uuid4()),'Authorization':'Bearer fixture'}
        headers['X-Signature']=hmac.new(cfg.internal_header_secret.encode(),'\n'.join(headers.values()).encode(),hashlib.sha256).hexdigest()
        return headers
    def transport(request):
        calls.append((request.method,request.url.host,request.url.path))
        if request.url.host=='file.fixture':
            assert request.headers['X-User-ID']=='service:recommendation-service'
            raw='\n'.join(request.headers[n] for n in ('X-User-ID','X-User_Roles','X-Request-ID','Authorization'))
            assert hmac.compare_digest(request.headers['X-Signature'],hmac.new(cfg.internal_header_secret.encode(),raw.encode(),hashlib.sha256).hexdigest())
            identifier=request.url.path.split('/')[-2]
            if request.method=='PUT' and request.url.path.endswith('/generated'):
                data=request.content; query=request.url.params
                assert sha256(data)==query['sha256'] and query['ownerId']==user
                metadata=dict(fileId=identifier,ownerId=user,fileName=query['fileName'],fileType='XLSX',
                    contentType=FILE_MIMES['XLSX'],sizeBytes=len(data),visibility='PRIVATE',status='ACTIVE',deleted=False)
                if identifier in stored: assert stored[identifier][0]==data
                stored[identifier]=(data,metadata)
                return httpx.Response(200,json=metadata)
            if request.method=='POST' and request.url.path.endswith('/generated-download-url'):
                assert json.loads(request.content)['fileId']==identifier
                data,metadata=stored[identifier]
                return httpx.Response(200,json=dict(url='http://storage.fixture/'+identifier,
                    fileName=metadata['fileName'],contentType=metadata['contentType'],sizeBytes=len(data),
                    etag=sha256(data),expiresInSeconds=300))
        if request.method=='GET' and request.url.host=='storage.fixture':
            data,_=stored[request.url.path.strip('/')]
            assert 'Authorization' not in request.headers and 'X-Signature' not in request.headers
            assert request.headers['If-Match']=='"'+sha256(data)+'"'
            return httpx.Response(200,stream=httpx.ByteStream(data),headers={'Content-Length':str(len(data))})
        raise AssertionError('Unexpected outbound fixture request')

    engine=create_engine('sqlite://',connect_args={'check_same_thread':False},poolclass=StaticPool)
    try:
        for model in (BusinessReportExport,CompletedPurchaseOrder,RecommendationSnapshot): model.__table__.create(engine)
        with Session(engine,expire_on_commit=False) as db, ExitStack() as stack:
            app=FastAPI(); app.include_router(router)
            app.dependency_overrides[get_db]=lambda:db
            app.dependency_overrides[get_business_report_service]=lambda:BusinessReportService(FileClient())
            for target in ('app.service.business_report_service.get_settings','app.core.ai_report_auth.get_settings',
                           'app.clients.file_client.get_settings'):
                stack.enter_context(patch(target,return_value=cfg))
            stack.enter_context(patch('app.core.internal_auth.settings',cfg))
            stack.enter_context(patch('offline_benchmark.evaluation.runner.freeze_predictions',side_effect=AssertionError('inference forbidden')))
            stack.enter_context(patch('offline_benchmark.evaluation.v1_state.build_artifact',side_effect=AssertionError('model fitting forbidden')))
            real_client=httpx.Client
            stack.enter_context(patch('app.clients.file_client.httpx.Client',side_effect=lambda **kw:real_client(transport=httpx.MockTransport(transport),**kw)))
            client=stack.enter_context(TestClient(app))
            base='/api/recommendation/ai-reports/exports'
            payload=dict(fromDate=from_date.isoformat(),toDate=to_date.isoformat(),requestId=str(uuid4()))
            assert client.post(base,json=payload).status_code==401
            assert client.post(base,json=payload,headers=signed('CUSTOMER')).status_code==403
            forged=signed(); forged['X-User_Roles']='SUPER_ADMIN'
            assert client.post(base,json=payload,headers=forged).status_code==401
            first=client.post(base,json=payload,headers=signed()); assert first.status_code==200,first.text
            response=first.json(); identifier=response['id']
            record=db.get(BusinessReportExport,identifier)
            content=client.get(base+'/'+identifier+'/download',headers=signed())
            assert content.status_code==200 and content.content==record.workbook
            assert response['fileId']==identifier and sha256(content.content)==response['sha256']
            bundle=BundleProvider(bundle_path,bundle_sha256).load()
            selected=bundle.for_period(from_date,to_date)
            workbook_checks=verify_workbook(content.content,selected.result,marker=DEMO_MARKER)
            for model in (RecommendationSnapshot,CompletedPurchaseOrder): assert db.scalars(select(model)).all()==[]
            cfg.ai_report_data_source=None; cfg.ai_report_offline_bundle_path='missing-after-complete'
            again=client.post(base,json=payload,headers=signed()); assert again.json()==response
            frozen=client.get(base+'/'+identifier+'/download',headers=signed())
            assert frozen.content==content.content
            assert len(stored)==1 and len([c for c in calls if c[0]=='PUT'])==1
            result=dict(created_at=datetime.now(timezone.utc).isoformat(),test_kind='ISOLATED_API_WITH_MOCK_FILE_AND_STORAGE',
                gateway_live=False,file_service_live=False,shared_database_writes=0,inference_calls=0,
                authentication='PASS: manager, missing, customer and forged context',
                immutable_retry='PASS: same response and bytes after invalid source config',
                evidence_tables='EMPTY: no fake snapshots/purchases',file_binding='PASS: actual FileClient over MockTransport',
                response=response,provenance=record.evidence,workbook_verification=workbook_checks,
                boundary_calls=[dict(method=m,host=h,path=p) for m,h,p in calls])
            return content.content,result
    finally:
        engine.dispose()


def main():
    os.environ.update(MYSQL_HOST='127.0.0.1',MYSQL_PORT='1',MYSQL_DATABASE='isolated_report',MYSQL_USER='fixture',
        MYSQL_PASSWORD='fixture',APP_ENVIRONMENT='test',INTERNAL_REPLAY_BACKEND='memory',
        SPRING_FLYWAY_ENABLED='false',SPRING_SQL_INIT_MODE='never',STORAGE_INITIALIZATION_ENABLED='false')
    from scripts.run_offline_evaluation_tests import guard
    sys.addaudithook(guard)
    from offline_benchmark.evaluation.output import report_helper, write_artifacts
    parser=argparse.ArgumentParser(description=__doc__,allow_abbrev=False)
    parser.add_argument('--bundle',type=Path,required=True); parser.add_argument('--bundle-sha256',required=True)
    parser.add_argument('--from-date',type=date.fromisoformat,required=True)
    parser.add_argument('--to-date',type=date.fromisoformat,required=True)
    parser.add_argument('--output-directory',type=Path,required=True)
    args=parser.parse_args()
    content,result=verify_export(args.bundle,args.bundle_sha256,args.from_date,args.to_date)
    destination=report_helper().verify_destination(args.output_directory)
    destination.mkdir(parents=True,exist_ok=False)
    artifacts={result['response']['fileName']:content,
        'api-export-verification.json':(json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode('utf-8')}
    write_artifacts(destination,artifacts)
    print('Isolated existing API export verified: '+str(destination))
    print('Workbook SHA-256: '+result['response']['sha256'])
    print('Opportunities: '+str(result['provenance']['metrics']['total_evaluation_opportunities']))


if __name__=='__main__':
    raise SystemExit(main())
