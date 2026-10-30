"""Causal state, immutable prediction boundary, metrics, source and storage contracts."""
from dataclasses import asdict, replace
from datetime import datetime, timedelta, timezone
from io import StringIO
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from app.recommendation.v1_model import CatalogProduct, catalog_fingerprint, hybrid_scores
from app.schemas.recommendation import RecommendationMode
from app.service.recommendation_service import MIN_HYBRID_PURCHASE_ORDERS
from offline_benchmark.common.manifest import build_manifest, digest as original_digest
from offline_benchmark.common.types import CatalogProduct as BinaryProduct, DatabaseSnapshot, PurchaseEvent
from offline_benchmark.data.exact_tfidf import payload
from offline_benchmark.evaluation.contract import FrozenPredictions, RankedItem
from offline_benchmark.evaluation.output import allocate_run, export_run, write_artifacts
from offline_benchmark.evaluation.runner import evaluate, freeze_predictions, verify_archive
from offline_benchmark.evaluation.v1_state import CanonicalTrain, build_opportunities, build_state
from offline_benchmark.split.chronological_split import chronological_split


def fixture():
    products=tuple(CatalogProduct(f"p{i}",f"TEA{i}",f"Trà {i}",f"CAT{i%2}",(f"ING{i%3}",)) for i in range(7))
    start=datetime(2026,1,1)
    events=tuple(PurchaseEvent(f"{user}-{n}",user,f"p{n%6}",start+timedelta(days=n,hours=offset),
                               f"order-{user}-{n}","PURCHASE")
                 for user,offset in (("sensitive-customer-A",0),("sensitive-customer-B",12)) for n in range(1,11))
    snapshot=DatabaseSnapshot(events,tuple(BinaryProduct(p.product_id,p.category_code,p.ingredient_codes) for p in products))
    return snapshot,products,chronological_split(events)


def save_fixture(directory):
    snapshot,products,splits=fixture()
    archive=Path(directory)/"input.json"; manifest=Path(directory)/"split.json"
    audit={"database":"recommend_app","catalog_sha256":catalog_fingerprint(products),
           "purchases_sha256":original_digest([asdict(e) for e in snapshot.purchases])}
    archive.write_text(json.dumps(payload(snapshot,products,audit),default=str),encoding="utf-8")
    manifest.write_text(json.dumps(build_manifest(snapshot,splits)),encoding="utf-8")
    return archive,manifest,hashlib.sha256(archive.read_bytes()).hexdigest()


class InputTests(unittest.TestCase):
    def test_exact_manifest_and_deterministic_original_split(self):
        with TemporaryDirectory() as directory:
            args=save_fixture(directory); data=verify_archive(*args)
            self.assertEqual(data.manifest,build_manifest(data.snapshot,data.splits))
            self.assertEqual(data.splits,chronological_split(tuple(reversed(data.snapshot.purchases))))
            self.assertFalse({e.event_id for s in data.splits for e in s.train}&{e.event_id for s in data.splits for e in s.test})
            self.assertFalse({e.order_id for s in data.splits for e in s.train}&{e.order_id for s in data.splits for e in s.test})

    def test_wrong_trusted_archive_hash_fails_before_inference(self):
        with TemporaryDirectory() as directory:
            a,m,h=save_fixture(directory)
            with self.assertRaisesRegex(ValueError,"trusted fingerprint"):
                verify_archive(a,m,"0"*64)

    def test_changed_original_manifest_fails_closed(self):
        with TemporaryDirectory() as directory:
            a,m,h=save_fixture(directory); value=json.loads(m.read_text()); value['train_event_count']+=1
            m.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError,"original canonical manifest"):
                verify_archive(a,m,h)

    def test_changed_exact_catalog_with_old_internal_hash_rejected(self):
        with TemporaryDirectory() as directory:
            a,m,h=save_fixture(directory); value=json.loads(a.read_text()); value['catalog'][0]['name']='changed'
            a.write_text(json.dumps(value)); h=hashlib.sha256(a.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError,"fingerprints"):
                verify_archive(a,m,h)

    def test_boundary_tie_rejected(self):
        snapshot,products,splits=fixture(); first=splits[0]
        changed=replace(first,test=(replace(first.test[0],event_time=first.train[-1].event_time),)+first.test[1:])
        with self.assertRaisesRegex(ValueError,"Ambiguous"):
            build_opportunities((changed,))

    def test_spanning_earlier_own_order_rejected(self):
        snapshot,products,splits=fixture(); first=splits[0]
        extra=replace(first.train[0],event_id='extra',event_time=first.train[-1].event_time+timedelta(hours=1))
        with self.assertRaisesRegex(ValueError,"spans"):
            build_opportunities((replace(first,train=first.train+(extra,)),))


class CausalTests(unittest.TestCase):
    def setUp(self):
        self.snapshot,self.products,self.splits=fixture()
        self.plan=build_opportunities(self.splits)
        self.training=CanonicalTrain.from_opportunities(self.plan)

    def test_whole_order_cutoff_excludes_test_and_future_other_user(self):
        first=self.plan[0]; state=build_state(self.training,self.products,first.cutoff)
        orders,_,_=self.training.at(first.cutoff)
        actual={e.event_id for o in orders for e in o.events}
        test_ids={e.event_id for s in self.splits for e in s.test}
        self.assertFalse(actual&test_ids)
        self.assertNotIn(self.splits[1].train[-1].event_id,actual)
        self.assertEqual(state.event_count,15)
        self.assertEqual(state.order_count,15)
        self.assertTrue(all(e.event_time<=first.cutoff for o in orders for e in o.events))
        self.assertEqual(state.repository.customer_history(None,first.customer_id).distinct_order_count,8)

    def test_other_customer_test_before_cutoff_is_still_excluded(self):
        late=replace(self.splits[1],train=tuple(replace(e,event_time=e.event_time+timedelta(days=30)) for e in self.splits[1].train),
                     test=tuple(replace(e,event_time=e.event_time+timedelta(days=30)) for e in self.splits[1].test))
        plan=build_opportunities((self.splits[0],late)); train=CanonicalTrain.from_opportunities(plan)
        orders,_,_=train.at(plan[1].cutoff)
        self.assertFalse({e.event_id for o in orders for e in o.events}&{e.event_id for e in self.splits[0].test})

    def test_spanning_other_order_is_excluded_whole_with_diagnostic(self):
        b=self.splits[1]; extra=replace(b.train[0],event_id='span-extra',event_time=self.plan[0].cutoff+timedelta(hours=1))
        plan=build_opportunities((self.splits[0],replace(b,train=b.train+(extra,))))
        train=CanonicalTrain.from_opportunities(plan); orders,_,spanning=train.at(plan[0].cutoff)
        self.assertGreater(spanning,0)
        self.assertNotIn(b.train[0].order_id,{o.order_id for o in orders})

    def test_test_label_mutation_does_not_change_any_prediction(self):
        original=freeze_predictions(self.plan,self.products)
        changed=tuple(replace(s,test=tuple(replace(e,product_id='test-only-poison') for e in s.test)) for s in self.splits)
        other=freeze_predictions(build_opportunities(changed),self.products)
        self.assertEqual(original,other)
        self.assertNotEqual(evaluate(original,self.splits,self.products).metrics,evaluate(other,changed,self.products).metrics)

    def test_future_train_product_mutation_does_not_change_earlier_prediction(self):
        original=freeze_predictions(self.plan,self.products)
        later=self.splits[1]
        changed=replace(later,train=later.train[:-1]+(replace(later.train[-1],product_id='p6'),))
        other=freeze_predictions(build_opportunities((self.splits[0],changed)),self.products)
        self.assertEqual(original.predictions[0],other.predictions[0])

    def test_no_production_npz_repository_database_network_or_snapshot(self):
        targets=["app.recommendation.v1_artifact.V1ArtifactStore.load",
                 "app.recommendation.v1_artifact.V1ArtifactStore.save",
                 "app.repositories.v1_recommendation_repository.V1RecommendationRepository.customer_history",
                 "app.repositories.v1_recommendation_repository.V1RecommendationRepository.active_catalog",
                 "app.repositories.v1_recommendation_repository.V1RecommendationRepository.popularity",
                 "sqlalchemy.create_engine","sqlalchemy.orm.Session.execute","numpy.load","socket.socket.connect"]
        patches=[patch(t,side_effect=AssertionError('Production dependency forbidden')) for t in targets]
        for p in patches: p.start(); self.addCleanup(p.stop)
        result=freeze_predictions(self.plan,self.products)
        self.assertEqual(len(result.predictions),2)

    def test_state_arrays_are_readonly_and_database_session_is_rejected(self):
        state=build_state(self.training,self.products,self.plan[0].cutoff)
        with self.assertRaises(ValueError): state.store.load().popularity[0]=999
        with self.assertRaisesRegex(ValueError,"database session"):
            state.repository.customer_history(object(),self.plan[0].customer_id)

    def test_duplicate_train_membership_rejected(self):
        with self.assertRaisesRegex(ValueError,"repeats"):
            CanonicalTrain.from_opportunities(self.plan+(self.plan[0],))

    def test_predictions_freeze_is_deterministic_despite_build_clock(self):
        first=freeze_predictions(self.plan,self.products)
        second=freeze_predictions(self.plan,self.products)
        self.assertEqual(first.sha256,second.sha256)
        with self.assertRaisesRegex(ValueError,"freeze"):
            replace(first,sha256='0'*64).verify()


class ParityTests(unittest.TestCase):
    def setUp(self):
        snapshot,self.products,splits=fixture()
        self.opportunity=build_opportunities(splits)[0]
        self.state=build_state(CanonicalTrain.from_opportunities(build_opportunities(splits)),self.products,self.opportunity.cutoff)

    def test_current_weights_and_routing_contract(self):
        self.assertEqual(MIN_HYBRID_PURCHASE_ORDERS,2)
        scores,flat=hybrid_scores({'a':0,'b':10},{'a':10,'b':0},('a','b'))
        self.assertEqual(scores,{'a':.4,'b':.6}); self.assertFalse(flat)

    def test_service_candidates_and_normalization_match_pure_v1(self):
        service=self.state.service(); user=self.opportunity.customer_id
        history=self.state.repository.customer_history(None,user)
        for mode in (None,RecommendationMode.DISCOVERY,RecommendationMode.REPEAT):
            response=service.recommend(None,user,5,exclude_purchased=False,mode=mode)
            bought=set(history.product_counts); all_ids={p.product_id for p in self.products}
            eligible=all_ids if mode is None else all_ids-bought if mode==RecommendationMode.DISCOVERY else all_ids&bought
            candidates=tuple(p.product_id for p in self.products if p.product_id in eligible)
            content,cf=self.state.store.load().components(history.product_counts)
            scores,flat=hybrid_scores(content,cf,candidates)
            if flat and candidates:
                expected=[r[0] for r in self.state.repository.popularity(None,set(),5,eligible_ids=eligible)]
            else:
                expected=sorted(scores,key=lambda p:(-scores[p],p))[:5]
            self.assertEqual([i.product_id for i in response.items],expected)
            if response.source=='HYBRID':
                self.assertEqual([i.score for i in response.items],[scores[p] for p in expected])

    def test_zero_one_two_orders_routing_and_empty_candidates(self):
        service=self.state.service(); user=self.opportunity.customer_id
        original=self.state.repository.customer_history
        for n in (0,1,2,4,5):
            history=original(None,user)
            with patch.object(self.state.repository,'customer_history',return_value=replace(history,distinct_order_count=n)):
                response=service.recommend(None,user,5,exclude_purchased=False)
            self.assertEqual(response.source,'POPULARITY' if n<2 else 'HYBRID')
        self.assertEqual(service.recommend(None,'unknown',5,mode=RecommendationMode.REPEAT).items,[])

    def test_flat_components_fallback_uses_only_train_popularity(self):
        one=(self.products[0],)
        state=build_state(CanonicalTrain.from_opportunities((self.opportunity,)),one,self.opportunity.cutoff)
        response=state.service().recommend(None,self.opportunity.customer_id,5,exclude_purchased=False)
        self.assertEqual(response.fallback_reason,'BOTH_COMPONENTS_FLAT')
        self.assertEqual(response.items[0].score,state.repository.counts['p0'])

    def test_stable_product_id_tie_break_in_popularity(self):
        rows=self.state.repository.popularity(None,set(),7)
        self.assertEqual(rows,sorted(rows,key=lambda r:(-r[3],r[0])))


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        snapshot,self.products,splits=fixture(); self.splits=(splits[0],)
        self.frozen=freeze_predictions(build_opportunities(self.splits),self.products)

    def test_hand_calculated_fixed_k_metrics(self):
        prediction=replace(self.frozen.predictions[0],items=(RankedItem(1,'p3',.7),))
        result=evaluate(FrozenPredictions.freeze((prediction,)),self.splits,self.products)
        self.assertEqual(result.rows[0].hits,('p3',))
        self.assertEqual(result.metrics['precision_at_5'],.2)
        self.assertEqual(result.metrics['recall_at_5'],.5)
        self.assertEqual(result.metrics['hit_rate_at_5'],1)
        self.assertAlmostEqual(result.metrics['map_at_5'],.5)
        from math import log2
        self.assertAlmostEqual(result.metrics['ndcg_at_5'],1/(1+1/log2(3)))

    def test_multiple_lines_same_canonical_product_count_one_hit_distinct_orders(self):
        s=self.splits[0]; test=tuple(replace(e,product_id='p3') for e in s.test)
        test=test+(replace(test[0],event_id='another-variant-line'),)
        changed=replace(s,test=test)
        prediction=replace(self.frozen.predictions[0],items=(RankedItem(1,'p3',1),))
        result=evaluate(FrozenPredictions.freeze((prediction,)),(changed,),self.products)
        self.assertEqual(result.rows[0].ground_truth,('p3',))
        self.assertEqual(result.metrics['total_matched_opportunity_product_pairs'],1)
        self.assertEqual(len(result.rows[0].test_order_products),2)
        self.assertIsNone(result.metrics['test_quantity'])

    def test_empty_recommendations_stay_in_accuracy_denominator(self):
        p=replace(self.frozen.predictions[0],items=())
        result=evaluate(FrozenPredictions.freeze((p,)),self.splits,self.products)
        self.assertEqual(result.metrics['accuracy_evaluated_opportunities'],1)
        self.assertEqual(result.metrics['precision_at_5'],0)
        self.assertEqual(result.rows[0].status,'NO_CANDIDATES')

    def test_empty_ground_truth_retained_but_accuracy_is_undefined(self):
        result=evaluate(self.frozen,(replace(self.splits[0],test=()),),self.products)
        self.assertEqual(result.rows[0].status,'EMPTY_GROUND_TRUTH')
        self.assertIsNone(result.metrics['recall_at_5'])
        self.assertEqual(result.metrics['total_evaluation_opportunities'],1)

    def test_duplicate_rank_outside_catalog_and_mismatched_cohort_rejected(self):
        p=self.frozen.predictions[0]
        for items in ((RankedItem(2,'p3',1),),(RankedItem(1,'outside',1),),(RankedItem(1,'p3',1),RankedItem(2,'p3',1))):
            with self.assertRaises(ValueError):
                evaluate(FrozenPredictions.freeze((replace(p,items=items),)),self.splits,self.products)
        with self.assertRaises(ValueError): evaluate(self.frozen,(),self.products)


class StorageTests(unittest.TestCase):
    def test_no_overwrite_and_in_project_destination_rejected(self):
        with TemporaryDirectory() as directory:
            path=Path(directory); write_artifacts(path,{'metrics.json':b'{}'})
            with self.assertRaises(FileExistsError): write_artifacts(path,{'metrics.json':b'changed'})
            self.assertEqual((path/'metrics.json').read_bytes(),b'{}')
        from offline_benchmark.evaluation.output import PROJECT
        with self.assertRaisesRegex(ValueError,'outside the project'):
            allocate_run(PROJECT,datetime.now(timezone.utc))

    def test_quantity_metadata_cannot_change_predictions_or_metrics(self):
        with TemporaryDirectory() as directory:
            dataset=verify_archive(*save_fixture(directory))
            def subprocess_result(args,**kwargs):
                if '--show-toplevel' in args:
                    return SimpleNamespace(returncode=128,stdout='',stderr='not a git repository')
                if args[0]=='git': return SimpleNamespace(returncode=0,stdout='fixture-revision',stderr='')
                return SimpleNamespace(returncode=0,stdout='external-fixture-report.md',stderr='')
            with patch('offline_benchmark.evaluation.output.subprocess.run',side_effect=subprocess_result):
                first=export_run(dataset,Path(directory)/'exports',{'status':'NOT_VERIFIED','test_quantity':1})
                second=export_run(dataset,Path(directory)/'exports',{'status':'NOT_VERIFIED','test_quantity':999999})
            self.assertEqual(first['metrics'],second['metrics'])
            self.assertNotEqual(first['directory'],second['directory'])
            self.assertEqual(json.loads((Path(first['directory'])/'manifest.json').read_text())['source'],'ARCHIVE')
            self.assertNotIn('sensitive-customer', (Path(first['directory'])/'predictions.json').read_text())


class QuantityProbeTests(unittest.TestCase):
    def test_connection_failure_is_sanitized_and_never_claims_quantity(self):
        from offline_benchmark.evaluation.quantity import probe_quantity
        import pymysql
        with TemporaryDirectory() as directory:
            dataset=verify_archive(*save_fixture(directory))
            settings=SimpleNamespace(mysql_database='recommend_app',mysql_host='fixture',mysql_port=1,
                mysql_user='fixture',mysql_password=SimpleNamespace(get_secret_value=lambda:'DO_NOT_LOG'))
            def fail(**kwargs): raise pymysql.OperationalError(2003,'DO_NOT_LOG sensitive connection details')
            with patch('app.core.settings.Settings',return_value=settings):
                result=probe_quantity(dataset,Path(__file__),connect=fail)
            self.assertEqual(result,{'status':'NOT_VERIFIED','reason':'mysql_2003','workbook_quantity':'N/A'})
            self.assertNotIn('DO_NOT_LOG',str(result))

    def test_select_only_mapping_probe_rolls_back_and_requires_authoritative_units(self):
        from offline_benchmark.evaluation.quantity import probe_quantity
        from uuid import uuid5,NAMESPACE_DNS
        from decimal import Decimal
        uuid=lambda value:str(uuid5(NAMESPACE_DNS,value))
        data,products,splits=fixture()
        events=tuple(replace(e,event_id=uuid(e.event_id),order_id=uuid(e.order_id),product_id=uuid(e.product_id)) for e in data.purchases)
        snap=replace(data,purchases=events)
        dataset=SimpleNamespace(manifest=build_manifest(snap,chronological_split(events)),splits=chronological_split(events))
        rows=[{'id':e.event_id,'customer_id':e.customer_id,'product_id':e.product_id,'event_time':e.event_time,
               'order_id':e.order_id,'event_type':e.event_type,'product_variant_id':uuid('variant-'+e.product_id),
               'quantity':Decimal(2),'source_event_id':uuid('source-'+e.order_id),'order_item_id':uuid('line-'+e.event_id)}
              for e in sorted(events,key=lambda e:e.event_id)]
        statements=[]
        class Cursor:
            sql=''
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def execute(self,sql): self.sql=sql; statements.append(sql)
            def fetchone(self): return {'db':'recommend_app','ro':1}
            def fetchall(self):
                if 'information_schema' in self.sql: return [{'name':p,'engine':'InnoDB'} for p in ('interaction_events','product_variants')]
                if 'FROM interaction_events' in self.sql: return rows
                return [{'id':uuid('variant-'+p),'product_id':p} for p in sorted({e.product_id for e in events})]
        connection=SimpleNamespace(cursor=lambda:Cursor(),rollback=unittest.mock.Mock(),close=unittest.mock.Mock())
        settings=SimpleNamespace(mysql_database='recommend_app',mysql_host='fixture',mysql_port=1,
            mysql_user='fixture',mysql_password=SimpleNamespace(get_secret_value=lambda:'fixture'))
        with patch('app.core.settings.Settings',return_value=settings):
            result=probe_quantity(dataset,Path(__file__),connect=lambda **kwargs:connection)
        self.assertTrue(result['mapping_one_to_one'])
        self.assertEqual(result['status'],'NOT_VERIFIED')
        self.assertEqual(result['workbook_quantity'],'N/A')
        self.assertEqual(result['test_events'],4)
        connection.rollback.assert_called_once(); connection.close.assert_called_once()
        self.assertTrue(all(s.startswith(('SELECT ','SET ','START TRANSACTION ')) for s in statements))
        self.assertFalse(any('COMMIT' in s for s in statements))
