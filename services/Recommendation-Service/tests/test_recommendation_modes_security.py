from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
import hashlib
import hmac
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import time
from types import SimpleNamespace
from uuid import uuid4
import unittest
from unittest.mock import MagicMock, patch

from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
import numpy as np
from pydantic import SecretStr
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from app.api.recommendations import router, get_recommendation_service, authorize_customer
from app.models.business_report import RecommendationSnapshot
from app.api.catalog_sync import router as sync_router
from app.core import recommendation_auth as auth
from app.core.settings import Settings
from app.database.session import get_db
from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_model import build_artifact, hybrid_scores
from app.repositories.v1_recommendation_repository import V1RecommendationRepository, CustomerPurchaseHistory
from app.schemas.recommendation import RecommendationMode as Mode
from app.service.recommendation_service import RecommendationService
from tests.test_v1_recommendations import catalog, artifact


USER = '11111111-1111-4111-8111-111111111111'
CUSTOMER = '33333333-3333-4333-8333-333333333333'
OTHER = '44444444-4444-4444-8444-444444444444'
SECRET = 'isolated-recommendation-context-secret-32'


def settings(**kw):
    return Settings(_env_file=None, mysql_user='fixture', mysql_password='fixture',
        app_environment='test', internal_header_secret=SECRET, internal_replay_backend='memory', **kw)


def signed(path, query='', user=USER, roles='CUSTOMER', customer=CUSTOMER,
           timestamp=None, expiry=None, nonce=None, method='GET', authorization=None):
    stamp = int(time.time()) if timestamp is None else timestamp
    expires = stamp+300 if expiry is None else expiry
    if user.startswith('service:'):
        expires, customer = 0, ''
    values = (user, roles, str(uuid4()), authorization or ('Bearer ' + user if user.startswith('service:') else 'Bearer fixture-jwt'))
    extension = (str(stamp), nonce or str(uuid4()), customer, str(expires))
    headers = dict(zip(auth.BASE, values)) | dict(zip(auth.EXTENSION, extension))
    headers['X-Signature'] = hmac.new(SECRET.encode(), '\n'.join(values).encode(), hashlib.sha256).hexdigest()
    # Independent canonical formatting, not the verifier's context_text helper.
    raw = '\n'.join(('hvtra-internal-v2', *values, *extension, method, path, query))
    headers['X-Context-Signature'] = hmac.new(SECRET.encode(), raw.encode(), hashlib.sha256).hexdigest()
    return headers


class ModeFixture:
    def setUp(self):
        self.repo = MagicMock(spec=V1RecommendationRepository)
        self.repo.active_catalog.return_value = catalog()
        self.store = MagicMock(spec=V1ArtifactStore)
        self.store.load.return_value = artifact()
        self.service = RecommendationService(self.store, self.repo)
        self.history(2, {'a':3, 'c':1})
        def popular(db, excluded, limit, eligible_ids=None):
            # Global popularity intentionally differs from customer's frequencies.
            values = {'a':1, 'b':60, 'c':100, 'd':50, 'e':40, 'f':30}
            return [(p.product_id,p.product_code,p.name,values[p.product_id])
                for p in sorted(self.repo.active_catalog.return_value, key=lambda p:(-values[p.product_id],p.product_id))
                if p.product_id not in excluded and (eligible_ids is None or p.product_id in eligible_ids)][:limit]
        self.repo.popularity.side_effect = popular

    def history(self, orders, products):
        self.repo.customer_history.return_value = CustomerPurchaseHistory(orders, products)


class ModeTests(ModeFixture, unittest.TestCase):

    def test_routing_matrix_and_mode_membership(self):
        for n in (0,1,2,4,5):
            self.history(n, {} if n == 0 else {'a':3,'c':1})
            for mode in Mode:
                with self.subTest(orders=n,mode=mode):
                    result = self.service.recommend(None,CUSTOMER,99,mode=mode)
                    self.assertEqual(result.source,'POPULARITY' if n < 2 else 'HYBRID')
                    ids = {p.product_id for p in result.items}
                    bought = set(self.repo.customer_history.return_value.product_counts)
                    self.assertLessEqual(len(result.items),5)
                    self.assertEqual(len(ids),len(result.items))
                    self.assertTrue(ids <= bought if mode == Mode.REPEAT else not ids & bought)
                    self.assertEqual(result.mode,mode)
                    if n == 0 and mode == Mode.REPEAT:
                        self.assertEqual(result.items,[])

    def test_popularity_repeat_uses_global_counts_not_personal_frequency(self):
        self.history(1,{'a':99,'c':1})
        result = self.service.recommend(None,CUSTOMER,5,mode=Mode.REPEAT)
        self.assertEqual([p.product_id for p in result.items],['c','a'])
        self.assertEqual([p.score for p in result.items],[100,1])
        self.store.load.assert_not_called()

    def test_repeated_product_is_one_candidate_and_no_padding(self):
        self.history(2,{'a':100})
        result = self.service.recommend(None,CUSTOMER,5,mode=Mode.REPEAT)
        self.assertEqual([p.product_id for p in result.items],['a'])
        # A singleton normalizes to flat and uses the existing safe fallback.
        self.assertEqual(result.fallback_reason,'BOTH_COMPONENTS_FLAT')

    def test_missing_corrupt_incompatible_artifact_all_modes(self):
        with TemporaryDirectory() as directory:
            for kind in ('missing','corrupt','incompatible'):
                path = Path(directory)/(kind+'.npz')
                if kind == 'corrupt':
                    path.write_bytes(b'isolated corruption')
                elif kind == 'incompatible':
                    V1ArtifactStore(path).save(artifact())
                    with np.load(path,allow_pickle=False) as source:
                        data = {k:source[k].copy() for k in source.files}
                    meta=json.loads(data['metadata'].item());meta['format_version']=999
                    data['metadata']=np.asarray(json.dumps(meta));np.savez_compressed(path,**data)
                service=RecommendationService(V1ArtifactStore(path),self.repo)
                for mode in Mode:
                    result=service.recommend(None,CUSTOMER,5,mode=mode)
                    ids={p.product_id for p in result.items}
                    self.assertEqual(result.fallback_reason,'V1_MODEL_NOT_AVAILABLE')
                    self.assertTrue(ids <= {'a','c'} if mode == Mode.REPEAT else not ids & {'a','c'})

    def test_catalog_mismatch_fallback_preserves_mode(self):
        self.repo.active_catalog.return_value = catalog()[:-1]
        for mode in Mode:
            result = self.service.recommend(None,CUSTOMER,5,mode=mode)
            self.assertEqual(result.fallback_reason,'V1_MODEL_NOT_AVAILABLE')
            ids={p.product_id for p in result.items}
            self.assertTrue(ids <= {'a','c'} if mode == Mode.REPEAT else not ids & {'a','c'})

    def test_flat_scoring_fallback_preserves_mode(self):
        with patch('app.service.recommendation_service.hybrid_scores',return_value=({},True)):
            for mode in Mode:
                result=self.service.recommend(None,CUSTOMER,5,mode=mode)
                self.assertEqual(result.fallback_reason,'BOTH_COMPONENTS_FLAT')
                ids={p.product_id for p in result.items}
                self.assertTrue(ids <= {'a','c'} if mode == Mode.REPEAT else not ids & {'a','c'})

    def test_recoverable_scoring_error_preserves_mode(self):
        from app.recommendation.v1_model import V1ModelError
        with patch('app.service.recommendation_service.hybrid_scores',side_effect=V1ModelError('fixture error')):
            for mode in Mode:
                result=self.service.recommend(None,CUSTOMER,5,mode=mode)
                self.assertEqual(result.fallback_reason,'V1_MODEL_NOT_AVAILABLE')
                ids={p.product_id for p in result.items}
                self.assertTrue(ids <= {'a','c'} if mode == Mode.REPEAT else not ids & {'a','c'})

    def test_empty_candidates_stay_empty(self):
        for mode,bought in ((Mode.REPEAT,{'outside':2}), (Mode.DISCOVERY,{p.product_id:1 for p in catalog()})):
            self.history(2,bought)
            result=self.service.recommend(None,CUSTOMER,5,mode=mode)
            self.assertEqual(result.items,[])
            self.assertIsNone(result.fallback_reason)

    def test_determinism_and_sixty_forty_normalization_parity(self):
        model=artifact()
        for mode in Mode:
            first=self.service.recommend(None,CUSTOMER,5,mode=mode)
            second=self.service.recommend(None,CUSTOMER,5,mode=mode)
            self.assertEqual(first.model_dump(),second.model_dump())
            eligible=tuple(p.product_id for p in catalog() if (p.product_id in {'a','c'}) == (mode == Mode.REPEAT))
            content,cf=model.components({'a':3,'c':1})
            expected,_=hybrid_scores(content,cf,eligible)
            self.assertEqual([(i.product_id,i.score) for i in first.items],
                [(p,expected[p]) for p in sorted(expected,key=lambda p:(-expected[p],p))][:5])

    def test_legacy_false_still_allows_both_purchased_and_unpurchased(self):
        self.history(1,{'a':3,'c':1})
        result=self.service.recommend(None,CUSTOMER,6,False)
        ids={i.product_id for i in result.items}
        self.assertTrue(ids & {'a','c'})
        self.assertTrue(ids - {'a','c'})
        self.assertIsNone(result.mode)


class ModeRepositoryTests(unittest.TestCase):
    def test_purchase_membership_orders_and_active_filter_in_real_sqlite_projection(self):
        from tests.test_v1_recommendations import RepositoryTests
        fixture=RepositoryTests();fixture.setUp()
        try:
            fixture.add_events([('one','a','PURCHASE'),('one','b','PURCHASE'),
                ('two','a','PURCHASE'),('one','a','RETURN'),(None,'c','PURCHASE'),(' ','d','PURCHASE')])
            fixture.db.execute(text("UPDATE products SET status='INACTIVE' WHERE id='b'"))
            fixture.db.execute(text("UPDATE products SET is_deleted=1 WHERE id='c'"))
            fixture.db.execute(text("INSERT INTO customer_interactions VALUES('u','a',2),('u','d',1),('other','d',20)"))
            products=fixture.repository.active_catalog(fixture.db)
            store=SimpleNamespace(load=lambda:build_artifact(products,fixture.repository.build_purchase_counts(fixture.db)))
            service=RecommendationService(store,fixture.repository)
            result=service.recommend(fixture.db,'u',5,mode=Mode.REPEAT)
            self.assertEqual(result.purchase_order_count,2)
            self.assertEqual({r.product_id for r in result.items},{'a','d'})
            other=service.recommend(fixture.db,'u',5,mode=Mode.DISCOVERY)
            self.assertFalse({r.product_id for r in other.items}&{'a','b','c','d'})
        finally:
            fixture.doCleanups()


class SecurityApiTests(ModeFixture, unittest.TestCase):
    # Mode fixtures plus full dependency chain, not an authorization override.
    def setUp(self):
        super().setUp()
        self.cfg=settings()
        self.replay=auth.MemoryReplay()
        self.patches=[patch.object(auth,'get_settings',return_value=self.cfg),
                      patch.object(auth,'get_replay_store',return_value=self.replay)]
        for p in self.patches:p.start();self.addCleanup(p.stop)
        app=FastAPI();app.include_router(router);app.include_router(sync_router)
        engine = create_engine('sqlite://', connect_args={'check_same_thread':False}, poolclass=StaticPool)
        RecommendationSnapshot.__table__.create(engine)
        db = Session(engine)
        self.addCleanup(engine.dispose); self.addCleanup(db.close)
        app.dependency_overrides[get_db]=lambda:db
        app.dependency_overrides[get_recommendation_service]=lambda:self.service
        self.client=TestClient(app);self.addCleanup(self.client.close)
        self.path='/recommendations/'+CUSTOMER

    def request(self,query='mode=DISCOVERY&limit=5',**kw):
        return self.client.get(self.path+'?'+query,headers=signed(self.path,query,**kw))

    def test_api_to_real_service_both_modes_smoke(self):
        for mode in Mode:
            response=self.request('mode='+mode.value+'&limit=99')
            self.assertEqual(response.status_code,200,response.text)
            data=response.json();self.assertEqual(data['mode'],mode.value)
            self.assertEqual(data['customerId'],CUSTOMER)
            self.assertLessEqual(len(data['items']),5)
            ids={i['productId'] for i in data['items']}
            self.assertTrue(ids <= {'a','c'} if mode == Mode.REPEAT else not ids & {'a','c'})

    def test_missing_credentials_401_without_reading_history(self):
        response=self.client.get(self.path)
        self.assertEqual(response.status_code,401)
        self.repo.customer_history.assert_not_called()

    def test_invalid_signature_token_tamper_and_unsigned_headers_401(self):
        for field in ('X-Signature','X-Context-Signature','Authorization','X-Customer-ID','X-User_Roles'):
            headers=signed(self.path);headers[field]='forged'
            self.assertEqual(self.client.get(self.path,headers=headers).status_code,401)

    def test_expired_user_token_401(self):
        self.assertEqual(self.request(expiry=int(time.time())-1).status_code,401)

    def test_cross_customer_403_even_if_subject_equals_target(self):
        self.assertEqual(self.request(customer=OTHER).status_code,403)
        self.assertEqual(self.request(user=CUSTOMER,customer=OTHER).status_code,403)

    def test_mapping_missing_fails_closed(self):
        self.assertEqual(self.request(customer='').status_code,503)

    def test_staff_not_privileged_by_default_but_explicit_policy_supported(self):
        self.assertEqual(self.request(roles='SALE_STAFF',customer=OTHER).status_code,403)
        self.cfg.recommendation_staff_roles='SALE_STAFF'
        self.assertEqual(self.request(roles='SALE_STAFF',customer='').status_code,200)

    def test_internal_service_allowlist_and_exact_bearer_contract(self):
        self.assertEqual(self.request(user='service:Order-Service',roles='SYSTEM').status_code,403)
        self.cfg.recommendation_internal_callers='Order-Service'
        self.assertEqual(self.request(user='service:Order-Service',roles='SYSTEM').status_code,200)
        self.assertEqual(self.request(user='service:Order-Service',roles='ADMIN').status_code,401)
        self.assertEqual(self.request(user='service:Order-Service',roles='SYSTEM',authorization='Bearer other').status_code,401)

    def test_replay_timestamp_boundaries_and_nonce_tamper(self):
        with patch.object(auth.time,'time',return_value=1000):
            for stamp,status in ((940,200),(1060,200),(939,401),(1061,401)):
                self.assertEqual(self.request(timestamp=stamp,expiry=2000).status_code,status)
            query='mode=REPEAT';headers=signed(self.path,query,timestamp=1000,expiry=2000)
            self.assertEqual(self.client.get(self.path+'?'+query,headers=headers).status_code,200)
            self.assertEqual(self.client.get(self.path+'?'+query,headers=headers).status_code,401)
        headers=signed(self.path);headers['X-Internal-Nonce']=str(uuid4())
        self.assertEqual(self.client.get(self.path,headers=headers).status_code,401)

    def test_method_path_query_bound_and_duplicate_signed_headers_rejected(self):
        headers=signed(self.path,'mode=DISCOVERY')
        self.assertEqual(self.client.get(self.path+'?mode=REPEAT',headers=headers).status_code,401)
        self.assertEqual(self.client.get('/recommendations/'+OTHER+'?mode=DISCOVERY',headers=headers).status_code,401)
        headers=signed(self.path,method='POST')
        self.assertEqual(self.client.get(self.path,headers=headers).status_code,401)
        headers=signed(self.path)
        duplicated=list(headers.items())+[('X-Customer-ID',OTHER)]
        self.assertEqual(self.client.get(self.path,headers=duplicated).status_code,401)

    def test_replay_identity_uuid_is_canonical_even_with_different_case(self):
        identity='abcdefab-cdef-4abc-8abc-abcdefabcdef'
        nonce=str(uuid4())
        self.assertEqual(self.request(user=identity,nonce=nonce).status_code,200)
        self.assertEqual(self.request(user=identity.upper(),nonce=nonce).status_code,401)

    def test_legacy_flags_are_exact_and_contradictions_422(self):
        for query in ('exclude_purchased=false&limit=6','exclude_purchased=true',''):
            response=self.request(query)
            self.assertEqual(response.status_code,200,response.text)
            self.assertIsNone(response.json()['mode'])
        for query in ('mode=DISCOVERY&exclude_purchased=false','mode=REPEAT&exclude_purchased=false',
                      'mode=REPEAT&exclude_purchased=true','mode=BAD'):
            self.assertEqual(self.request(query).status_code,422)
        self.assertEqual(self.request('mode=DISCOVERY&exclude_purchased=true').status_code,200)

    def test_sync_missing_invalid_unauthorized_and_replay_rejected(self):
        path='/internal/catalog/sync'
        self.assertEqual(self.client.post(path).status_code,401)
        headers=signed(path,user='service:Wrong-Service',roles='SYSTEM',method='POST')
        self.assertEqual(self.client.post(path,headers=headers).status_code,403)
        self.assertEqual(self.client.post(path,headers=signed(path,method='POST')).status_code,403)
        with patch('app.api.catalog_sync.CatalogClient') as client, patch('app.api.catalog_sync.CatalogSyncService') as service:
            service.return_value.sync_all.return_value={'categories':0,'products':0,'variants':0,'ingredients':0,'productIngredients':0}
            headers=signed(path,user='service:catalog-Service',roles='SYSTEM',method='POST')
            self.assertEqual(self.client.post(path,headers=headers).status_code,200)
            service.return_value.sync_all.assert_called_once()
            self.assertEqual(self.client.post(path,headers=headers).status_code,401)
            service.return_value.sync_all.assert_called_once()


class SecurityConfigTests(unittest.TestCase):
    def test_production_missing_weak_secret_or_memory_replay_rejected(self):
        for secret in ('','dev-internal-header-secret','weak',' '*32):
            cfg=settings();cfg.app_environment='production';cfg.internal_header_secret=secret
            with self.assertRaises(ValueError):auth.validate_security(cfg)
        cfg=settings();cfg.app_environment='production'
        with self.assertRaises(ValueError):auth.validate_security(cfg)
        cfg.internal_replay_backend='redis';cfg.internal_replay_redis_url=SecretStr('')
        with self.assertRaises(ValueError):auth.validate_security(cfg)
        cfg.internal_replay_backend='redis';cfg.internal_replay_redis_url=SecretStr('redis://127.0.0.1:9/0')
        auth.validate_security(cfg)

    def test_no_source_secret_fallback_and_no_unsupported_staff_role(self):
        self.assertEqual(Settings.model_fields['internal_header_secret'].default,'')
        cfg=settings();cfg.recommendation_staff_roles='CUSTOMER'
        with self.assertRaises(ValueError):auth.validate_security(cfg)

    def test_memory_replay_atomic_and_full_store_fail_closed(self):
        replay=auth.MemoryReplay(capacity=1)
        with ThreadPoolExecutor(max_workers=8) as pool:
            self.assertEqual(sum(pool.map(lambda _:replay.accept('same',10),range(8))),1)
        with self.assertRaises(HTTPException) as error:replay.accept('another',10)
        self.assertEqual(error.exception.status_code,503)

    def test_redis_replay_uses_atomic_nx_expiry_and_never_falls_back(self):
        from redis.exceptions import ConnectionError
        with patch('redis.Redis.from_url') as factory:
            store=auth.RedisReplay('redis://isolated/0')
            factory.return_value.set.return_value=True
            self.assertTrue(store.accept('key',121))
            factory.return_value.set.assert_called_with('hvtra:recommendation:nonce:key','1',nx=True,ex=121)
            factory.return_value.set.side_effect=ConnectionError('isolated failure')
            with self.assertRaises(HTTPException) as error:store.accept('key2',121)
            self.assertEqual(error.exception.status_code,503)

    def test_python_signer_and_gateway_canonical_interoperate(self):
        from app.core import internal_auth
        cfg=settings()
        with patch.object(internal_auth,'settings',cfg):
            headers=internal_auth.generate_internal_auth_headers(user_id=USER,roles='CUSTOMER',request_id='fixture-request',
                authorization='Bearer fixture-jwt',method='GET',path='/recommendations/'+CUSTOMER,query='mode=REPEAT&limit=5',
                timestamp=1700000000,nonce='22222222-2222-4222-8222-222222222222',customer_id=CUSTOMER,token_expires_at=1700000300)
        raw='\n'.join(('hvtra-internal-v2',USER,'CUSTOMER','fixture-request','Bearer fixture-jwt','1700000000',
            '22222222-2222-4222-8222-222222222222',CUSTOMER,'1700000300','GET','/recommendations/'+CUSTOMER,'mode=REPEAT&limit=5'))
        self.assertEqual(headers['X-Context-Signature'],hmac.new(SECRET.encode(),raw.encode(),hashlib.sha256).hexdigest())


if __name__ == '__main__':
    unittest.main()
