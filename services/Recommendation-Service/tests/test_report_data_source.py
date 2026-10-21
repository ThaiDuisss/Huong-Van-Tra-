"""Bundle validation, source pinning, period selection and existing export API regressions."""
import base64
from dataclasses import asdict, replace
from datetime import date
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from uuid import uuid4

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from sqlalchemy import select

from app.api.ai_reports import router, get_business_report_service
from app.database.session import get_db
from app.models.business_report import BusinessReportExport, CompletedPurchaseOrder, RecommendationSnapshot
from app.service.business_report_service import BusinessReportService
from app.service.report_data_source import select_source
from offline_benchmark.evaluation.bundle import (BundleProvider, SCHEMA, decode_bundle, sha256, source_hashes)
from offline_benchmark.evaluation.contract import K, MODE, PROTOCOL
from offline_benchmark.evaluation.runner import evaluate, freeze_predictions, verify_archive
from offline_benchmark.evaluation.v1_state import build_opportunities
from offline_benchmark.reporting.evaluation_workbook import verify_workbook
from app.service.report_data_source import DEMO_MARKER
from app.recommendation.v1_model import CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT
from app.service.recommendation_service import MIN_HYBRID_PURCHASE_ORDERS
from offline_benchmark.tests.test_offline_evaluation import save_fixture
from tests.test_ai_reports import signed, SECRET
from tests.test_business_reports import DbFixture, GeneratedFiles
from tests.test_recommendation_modes_security import settings


def fixture_bundle():
    with TemporaryDirectory() as directory:
        archive, manifest, digest = save_fixture(directory)
        dataset = verify_archive(archive, manifest, digest)
        frozen = freeze_predictions(build_opportunities(dataset.splits), dataset.products)
        value = dict(schema=SCHEMA, complete=True, protocol=PROTOCOL, mode=MODE, k=K,
            model_contract=CONTRACT_VERSION, feature_contract=FEATURE_CONTRACT,
            normalization_contract=NORMALIZATION_CONTRACT, routing_threshold=MIN_HYBRID_PURCHASE_ORDERS,
            source_archive_sha256=digest, original_manifest_file_sha256=sha256(manifest.read_bytes()),
            original_split_manifest=dataset.manifest, prediction_source_sha256=source_hashes(),
            prediction_sha256=frozen.sha256, source_snapshot_base64=base64.b64encode(archive.read_bytes()).decode(),
            frozen_predictions=dict(prediction_sha256=frozen.sha256,predictions=[asdict(p) for p in frozen.predictions]),
            metrics=evaluate(frozen,dataset.splits,dataset.products).metrics)
        return json.dumps(value,ensure_ascii=False).encode()


class BundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = fixture_bundle()

    def setUp(self):
        self.temporary = TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name)/'bundle.json'; self.path.write_bytes(self.raw)
        self.sha = sha256(self.raw)
        BundleProvider._cache.clear()

    def test_validation_and_period_selection_never_fit_or_load_serving_model(self):
        with (patch('offline_benchmark.evaluation.runner.freeze_predictions',side_effect=AssertionError('inference')),
              patch('offline_benchmark.evaluation.v1_state.build_artifact',side_effect=AssertionError('fit'))):
            bundle = BundleProvider(self.path,self.sha).load()
            full = bundle.for_period(date(2026,1,1),date(2026,1,31))
            subset = bundle.for_period(date(2026,1,10),date(2026,1,10))
        self.assertEqual((full.orders_selected,full.events_selected,len(full.result.rows)),(4,4,2))
        self.assertEqual((subset.orders_selected,subset.events_selected,len(subset.result.rows)),(2,2,2))
        self.assertEqual(sum(len(r.ground_truth) for r in subset.result.rows),2)
        self.assertEqual([r.prediction for r in subset.result.rows],[r.prediction for r in full.result.rows])
        self.assertNotEqual(subset.result.metrics,full.result.metrics)
        self.assertEqual(len(bundle.for_period(date(2025,1,1),date(2025,1,2)).result.rows),0)

    def test_whole_order_spanning_period_boundary_excluded(self):
        bundle=decode_bundle(self.raw,self.sha)
        first=bundle.dataset.splits[0]
        extra=replace(first.test[0],event_id='spanning',event_time=first.test[0].event_time.replace(day=11))
        changed=replace(bundle,dataset=replace(bundle.dataset,splits=(replace(first,test=first.test+(extra,)),)))
        result=changed.for_period(date(2026,1,10),date(2026,1,10))
        self.assertEqual(result.orders_selected,0)
        self.assertEqual(result.orders_spanning_boundary,1)

    def test_corruption_detected_even_after_cache_hit(self):
        provider=BundleProvider(self.path,self.sha); provider.load()
        self.path.write_bytes(self.raw+b' ')
        with self.assertRaises(ValueError): provider.load()

    def test_missing_bundle_or_pin_fails(self):
        for path,pin in ((self.path,'bad'),(self.path.parent/'missing',self.sha)):
            with self.assertRaises((ValueError,OSError)): BundleProvider(path,pin).load()

    def test_incomplete_stale_schema_contract_split_prediction_metrics_and_state_fail(self):
        for key in ('complete','schema','protocol','mode','k','model_contract','feature_contract',
                    'normalization_contract','routing_threshold','prediction_source_sha256',
                    'original_split_manifest','prediction_sha256','metrics','source_archive_sha256'):
            value=json.loads(self.raw); value[key]=None
            raw=json.dumps(value).encode()
            with self.subTest(key=key), self.assertRaises((ValueError,TypeError,AttributeError)):
                decode_bundle(raw,sha256(raw))
        value=json.loads(self.raw); value['frozen_predictions']['predictions'][0]['state_event_count']+=1
        raw=json.dumps(value).encode()
        with self.assertRaises(ValueError): decode_bundle(raw,sha256(raw))

    def test_causal_evidence_checked_after_valid_prediction_freeze(self):
        from offline_benchmark.evaluation.contract import FrozenPredictions
        bundle=decode_bundle(self.raw,self.sha)
        modified=replace(bundle.frozen.predictions[0],state_event_count=1)
        frozen=FrozenPredictions.freeze((modified,)+bundle.frozen.predictions[1:])
        value=json.loads(self.raw)
        value['frozen_predictions']=dict(prediction_sha256=frozen.sha256,predictions=[asdict(p) for p in frozen.predictions])
        value['prediction_sha256']=frozen.sha256
        value['metrics']=evaluate(frozen,bundle.dataset.splits,bundle.dataset.products).metrics
        raw=json.dumps(value).encode()
        with self.assertRaisesRegex(ValueError,'Causal canonical-TRAIN'):
            decode_bundle(raw,sha256(raw))

    def test_concurrent_bundle_loads_share_only_successfully_verified_object(self):
        from concurrent.futures import ThreadPoolExecutor
        with patch('offline_benchmark.evaluation.bundle.decode_bundle',wraps=decode_bundle) as decoder:
            with ThreadPoolExecutor(max_workers=4) as pool:
                results=list(pool.map(lambda _:BundleProvider(self.path,self.sha).load(),range(8)))
        self.assertEqual(decoder.call_count,1)
        self.assertTrue(all(result is results[0] for result in results))

    def test_actual_file_client_and_api_over_isolated_http_boundaries(self):
        from scripts.verify_ai_report_export import verify_export
        content,result=verify_export(self.path,self.sha,date(2026,1,1),date(2026,1,31))
        self.assertTrue(content.startswith(b'PK'))
        self.assertEqual(result['provenance']['metrics']['total_evaluation_opportunities'],2)
        self.assertEqual(result['inference_calls'],0)
        self.assertEqual(result['shared_database_writes'],0)

    def test_duplicate_keys_rejected(self):
        raw=b'{"schema":1,"schema":2}'
        with self.assertRaises(ValueError): decode_bundle(raw,sha256(raw))

    def test_config_explicit_no_implicit_fallback_or_production_offline(self):
        for cfg in (settings(),settings(ai_report_data_source='OFFLINE_EVALUATION'),
                    settings(ai_report_data_source='OFFLINE_EVALUATION').model_copy(update={'app_environment':'production'})):
            with self.assertRaises(HTTPException) as error: select_source(cfg)
            self.assertEqual(error.exception.status_code,503)
        self.assertEqual(select_source(settings(ai_report_data_source='OPERATIONAL'))['kind'],'OPERATIONAL')
        with self.assertRaises(ValueError): settings(ai_report_data_source='wrong')


class SourceExportTests(DbFixture, unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.raw=fixture_bundle()

    def setUp(self):
        super().setUp()
        self.temporary=TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.path=Path(self.temporary.name)/'bundle.json'; self.path.write_bytes(self.raw)
        self.cfg=settings(ai_report_data_source='OFFLINE_EVALUATION',ai_report_offline_bundle_path=str(self.path),
                          ai_report_offline_bundle_sha256=sha256(self.raw))
        self.files=GeneratedFiles(); self.service=BusinessReportService(self.files)
        self.app=FastAPI(); self.app.include_router(router)
        self.app.dependency_overrides[get_db]=lambda:self.db
        self.app.dependency_overrides[get_business_report_service]=lambda:self.service
        for target,cfg in [('app.service.business_report_service.get_settings',self.cfg),
                           ('app.core.ai_report_auth.get_settings',settings().model_copy(update={'internal_header_secret':SECRET}))]:
            mocked=patch(target,return_value=cfg); mocked.start(); self.addCleanup(mocked.stop)
        self.client=TestClient(self.app); self.addCleanup(self.client.close)
        self.base='/api/recommendation/ai-reports/exports'
        self.payload=dict(fromDate='2026-01-01',toDate='2026-01-31',requestId=str(uuid4()))

    def export(self): return self.client.post(self.base,json=self.payload,headers=signed(roles='MANAGER'))

    def record(self): return self.db.scalar(select(BusinessReportExport))

    def test_existing_endpoint_download_kpis_provenance_no_evidence_writes(self):
        response=self.export(); self.assertEqual(response.status_code,200,response.text)
        record=self.record(); self.assertEqual(record.evidence['report_source']['bundle_sha256'],sha256(self.raw))
        presentation=record.evidence['presentation']
        self.assertEqual(presentation['schema'],'sale-pos-ai-report-v1')
        self.assertEqual(presentation['display_identifiers'][0]['customer_display'],'KH-001')
        self.assertEqual(presentation['display_identifiers'][0]['opportunity_display'],'GY-001')
        self.assertIn('customer_reference',presentation['display_identifiers'][0])
        self.assertNotIn('presentation',response.json())
        result=decode_bundle(self.raw,sha256(self.raw)).for_period(date(2026,1,1),date(2026,1,31)).result
        verify_workbook(record.workbook,result,marker=DEMO_MARKER)
        download=self.client.get(self.base+'/'+record.id+'/download',headers=signed(roles='MANAGER'))
        self.assertEqual(download.content,record.workbook)
        for model in (RecommendationSnapshot,CompletedPurchaseOrder): self.assertEqual(self.db.scalars(select(model)).all(),[])

    def test_complete_and_prepared_bytes_survive_source_switch_and_missing_bundle(self):
        first=self.export(); self.assertEqual(first.status_code,200,first.text)
        record=self.record(); original=bytes(record.workbook)
        self.cfg.ai_report_data_source=None; self.path.unlink()
        self.assertEqual(self.export().json(),first.json())
        record.status='PREPARED'; self.db.commit()
        self.assertEqual(self.export().status_code,200)
        self.assertEqual(record.workbook,original)

    def test_pending_intent_cannot_silently_change_bundle(self):
        with patch('app.service.business_report_service.build_offline',side_effect=HTTPException(503,'temporary')):
            self.assertEqual(self.export().status_code,503)
        record=self.record(); self.assertEqual(record.status,'PENDING')
        self.assertEqual(record.evidence['report_source']['bundle_sha256'],sha256(self.raw))
        value=json.loads(self.raw); value['producer_label']='new-version'; changed=json.dumps(value).encode()
        self.path.write_bytes(changed); self.cfg.ai_report_offline_bundle_sha256=sha256(changed)
        self.assertEqual(self.export().status_code,409)
        self.payload['requestId']=str(uuid4()); self.assertEqual(self.export().status_code,200)

    def test_empty_period_fails_not_zero_success(self):
        self.payload.update(fromDate='2025-01-01',toDate='2025-01-31')
        self.assertEqual(self.export().status_code,422)
        self.assertIsNone(self.record().workbook)
        self.assertEqual(self.files.records,{})

    def test_missing_bundle_fails_without_creating_intent_or_operational_fallback(self):
        self.path.unlink(); self.assertEqual(self.export().status_code,503)
        self.assertIsNone(self.record())

    def test_authentication_roles_and_artifact_path_injection(self):
        for headers in ({},signed(roles='CUSTOMER')):
            self.assertIn(self.client.post(self.base,json=self.payload,headers=headers).status_code,(401,403))
        self.payload['artifactPath']='C:/arbitrary'
        self.assertEqual(self.export().status_code,422)
        self.assertIsNone(self.record())

    def test_pending_operational_and_legacy_intents_keep_original_source(self):
        from app.schemas.business_report import BusinessExportRequest
        from tests.test_ai_reports import A
        for legacy in (False,True):
            self.payload['requestId']=str(uuid4())
            if not legacy:
                self.cfg.ai_report_data_source='OPERATIONAL'
                with patch('app.service.business_report_service.build_workbook',side_effect=HTTPException(503,'temporary')):
                    self.assertEqual(self.export().status_code,503)
            else:
                from datetime import datetime
                self.db.add(BusinessReportExport(id=str(uuid4()),created_by=A,request_key=self.payload['requestId'],
                    from_date=date(2026,1,1),to_date=date(2026,1,31),generated_at=datetime(2026,1,31),
                    status='PENDING',file_name='AI_Report_HuongVanTra_20260101_20260131.xlsx'))
                self.db.commit()
            self.cfg.ai_report_data_source='OFFLINE_EVALUATION'
            with patch('app.service.business_report_service.build_offline',side_effect=AssertionError('source switch')):
                self.assertEqual(self.export().status_code,200)
