"""Synthetic contract checks for AI-02D-2. Fixtures are unrelated to model quality."""
from contextlib import redirect_stdout
from dataclasses import replace
from datetime import datetime, timedelta, timezone
import hashlib
from io import StringIO
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import Mock, patch
from uuid import UUID

from pydantic import ValidationError

from app.recommendation.taste_ranking_artifact import (
    LogisticModel, TasteRankingArtifact, TasteReranker, load_approved_rerankers,
)
from app.recommendation.taste_ranking_features import feature_names, vector
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import CONTRACT_VERSION, CatalogProduct, PurchaseCount, build_artifact, fingerprint
from app.repositories.explore_history_repository import ExploreHistory
from app.schemas.taste_catalog import REQUIRED_ATTRIBUTES, digest
from app.schemas.taste_recommendation import TasteCriteriaDocument
from app.service.explore_recommendation_service import ExploreRecommendationService
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch
from app.service.taste_recommendation_service import normalize_criteria
from app.training.taste_ranking_dataset import DatasetBlocked, EvidenceBundle, build_dataset, _projection
from app.training.taste_ranking_evaluation import evaluate
from app.training.train_taste_ranking import main, train_and_evaluate

BASE = datetime(2026, 1, 1, tzinfo=timezone.utc)
ATTRIBUTES = tuple(sorted(REQUIRED_ATTRIBUTES))
TAXONOMY = "test-v1"


def stamp(when):
    return when.isoformat().replace("+00:00", "Z")


def uid(number):
    return str(UUID(int=number))


def projection(when):
    manifest = {"versionCode": TAXONOMY, "schemaVersion": 1, "attributes": [
        {"attributeCode": attribute, "label": attribute, "displayOrder": 1, "maxSelections": 1,
         "matchingSemantics": "EXACT", "selectionKind": "ENUM_SINGLE",
         "values": [{"valueCode": value, "label": value, "displayOrder": 1} for value in ("HIGH", "LOW")]}
        for attribute in ATTRIBUTES
    ]}
    taxonomy_sha = digest(manifest)
    products, variants, profiles = [], [], []
    protocol = {"instructions": "Steep", "servingBasis": "One cup"}
    for index, name in enumerate(("Lotus floral", "Black tea", "Lotus green", "Black herbal"), 1):
        products.append({"id": uid(index), "name": name, "status": "ACTIVE", "isDeleted": False})
        variant = {"id": uid(index + 10), "productId": uid(index), "name": name, "sku": f"SKU-{index}",
                   "status": "ACTIVE", "isDeleted": False, "sellable": True, "customBundle": False,
                   "compositionRevision": 1, "compositionSha256": "a" * 64}
        variants.append(variant)
        content = {"variantId": variant["id"], "profileRevision": 1, "compositionRevision": 1,
                   "compositionSha256": variant["compositionSha256"], "taxonomySha256": taxonomy_sha,
                   "preparationCode": "HOT", "preparationSha256": digest(protocol), "sourceMethod": "EXPERT",
                   "sensoryValues": {"BITTERNESS": ["LOW" if index % 2 else "HIGH"]}}
        profiles.append(dict(content, id=uid(index + 20), taxonomyVersion=TAXONOMY,
                             profileSha256=digest(content), preparationProtocol=protocol, status="APPROVED",
                             approvedAt=stamp(when - timedelta(days=1))))
    payload = {"schemaVersion": 1, "catalogState": "PUBLISHED", "supportedTaxonomyVersions": [TAXONOMY],
               "taxonomies": [{"versionCode": TAXONOMY, "manifestSha256": taxonomy_sha,
                               "state": "PUBLISHED", "manifest": manifest}],
               "products": products, "variants": variants, "profiles": profiles, "preparationCodes": ["HOT"],
               "counts": {"products": len(products), "variants": len(variants), "approvedProfiles": len(profiles)}}
    checksum = digest(payload)
    return {"snapshotId": f"taste-v1-{checksum}", "sourceReadAt": stamp(when), "payloadSha256": checksum,
            "payload": payload}, taxonomy_sha


def evidence_json():
    windows, receipts, interactions = [], [], []
    criteria = TasteCriteriaDocument.model_validate({"hardGroups": [
        {"attribute": "BITTERNESS", "allowedValues": ["LOW"]},
    ]})
    for index, split in enumerate(("TRAIN", "VALIDATION", "TEST")):
        cutoff = BASE + timedelta(days=10 * index)
        snapshot, taxonomy_sha = projection(cutoff)
        customers = []
        for offset in range(3):
            customer_id = uid(100 + index * 3 + offset)
            survey = {
                "customerId": customer_id, "status": "SUBMITTED", "rowVersion": 1,
                "submittedAt": stamp(cutoff - timedelta(days=1)), "taxonomyVersion": TAXONOMY,
                "taxonomySha256": taxonomy_sha, "criteriaSha256": normalize_criteria(criteria, taxonomy_sha).digest,
                "criteria": criteria.model_dump(),
            } if offset < 2 else None
            customers.append({"customer_id": customer_id, "survey_as_of": stamp(cutoff),
                              "observed_at": stamp(cutoff), "survey": survey})
            purchases = (1, 2, 1, 1, 1, offset % 2 + 1, offset % 2 + 3)
            dates = (-5, -4, -3, -2, -1, 1, 1)
            for ordinal, (day, product) in enumerate(zip(dates, purchases)):
                order = uid(1000 + (index * 3 + offset) * 7 + ordinal)
                occurred = cutoff + timedelta(days=day)
                lines = [{"order_item_id": "line", "product_id": uid(product),
                          "product_variant_id": uid(product + 10), "quantity": "1", "purchase_type": "PRODUCT"}]
                checksum = fingerprint({"customer_id": customer_id, "occurred_at": occurred.replace(tzinfo=None)
                                        .isoformat(), "lines": lines})
                times = {"occurred_at": stamp(occurred), "observed_at": stamp(occurred)}
                receipts.append(dict(times, order_id=order, customer_id=customer_id, evidence_hash=checksum,
                                     lines=lines))
                interactions.append(dict(times, event_id=order, customer_id=customer_id,
                                         product_id=uid(product), order_id=order))
        windows.append({
            "split": split, "cutoff": stamp(cutoff), "outcome_end": stamp(cutoff + timedelta(days=2)),
            "outcome_freeze": stamp(cutoff + timedelta(days=3)), "catalog_as_of": stamp(cutoff),
            "catalog_observed_at": stamp(cutoff), "catalog_source": "fixture",
            "catalog": [{"product_id": product["id"], "product_code": product["id"], "name": product["name"],
                         "category_code": "TEA", "ingredient_codes": [], "commercial": True}
                        for product in snapshot["payload"]["products"]],
            "taste_catalog": snapshot, "taste_catalog_as_of": stamp(cutoff), "taste_catalog_observed_at": stamp(cutoff),
            "taste_catalog_ttl_seconds": 300, "taste_catalog_source": "fixture", "survey_source": "fixture",
            "customer_roster_as_of": stamp(cutoff), "customers": customers,
        })
    freeze = windows[-1]["outcome_freeze"]
    return {"schema_version": 1, "source_kind": "VERIFIED_SOURCE_EXPORT",
            "purchase_history_complete_from_inception": True, "receipt_source": "fixture", "return_source": "fixture",
            "interaction_source": "fixture", "taxonomy_version": TAXONOMY, "taxonomy_sha256": taxonomy_sha,
            "receipts_complete_through": freeze, "returns_complete_through": freeze,
            "interactions_complete_through": freeze, "exported_at": freeze, "windows": windows,
            "receipts": receipts, "returns": [], "interaction_purchases": interactions}


def build(raw):
    return build_dataset(EvidenceBundle.model_validate_json(json.dumps(raw)))


class TasteRankingPipelineTests(TestCase):
    def test_objectives_are_separate_and_use_only_cutoff_features(self):
        raw = evidence_json()
        receipt = raw["receipts"][4]
        raw["returns"].append({
            "source": "SIGNED_PRODUCT_RETURN", "return_item_id": "return", "customer_id": receipt["customer_id"],
            "order_id": receipt["order_id"], "order_item_id": "line", "variant_id": uid(11), "quantity": "1",
            "occurred_at": stamp(BASE + timedelta(days=1)), "observed_at": stamp(BASE + timedelta(days=1)),
        })
        dataset = build(raw)
        explore, familiar = dataset.queries[:2]
        self.assertEqual({row.features.product_id for row in explore.samples}, {uid(3), uid(4)})
        self.assertEqual({row.features.product_id for row in familiar.samples}, {uid(1), uid(2)})
        self.assertEqual([row.features.product_id for row in explore.samples if row.label], [uid(3)])
        self.assertEqual([row.features.product_id for row in familiar.samples if row.label], [uid(1)])
        self.assertEqual(familiar.samples[0].features.frequency, 4)
        self.assertEqual(familiar.samples[0].features.recency_days, 1)
        for mode in ("EXPLORE", "FAMILIAR"):
            artifact, report = train_and_evaluate(dataset, mode, "test-1")
            self.assertEqual(artifact.mode, mode)
            self.assertEqual(artifact.activation_status, "INACTIVE")
            self.assertEqual(artifact.evaluation_sha256, fingerprint(report))
            cohorts = report["test"]["cohorts"]
            self.assertEqual(cohorts["WITH_TASTE"]["reranked_queries"], 2)
            self.assertEqual(cohorts["WITHOUT_TASTE"]["reranked_queries"], 0)
            hybrid = cohorts["WITHOUT_TASTE"]["rankings"]["HYBRID_V1"]
            self.assertEqual(hybrid, cohorts["WITHOUT_TASTE"]["rankings"]["HYBRID_V1_TASTE_RERANKER"])

    def test_missing_history_future_observations_and_overlapping_windows_are_blocked(self):
        for issue, error in (("coverage", "literal_error"), ("survey", "FUTURE_OR_WRONG_CUSTOMER_SURVEY"),
                             ("profile", "FUTURE_PROFILE_APPROVAL"), ("window", "OVERLAPPING_WINDOWS"),
                             ("receipt", "LATE_OUTCOME_RECEIPT")):
            with self.subTest(issue=issue):
                raw = evidence_json()
                window = raw["windows"][0]
                if issue == "coverage":
                    raw["purchase_history_complete_from_inception"] = False
                elif issue == "survey":
                    window["customers"][0]["survey"]["submittedAt"] = stamp(BASE + timedelta(days=1))
                elif issue == "profile":
                    snapshot = window["taste_catalog"]
                    snapshot["payload"]["profiles"][0]["approvedAt"] = stamp(BASE + timedelta(days=1))
                    snapshot["payloadSha256"] = digest(snapshot["payload"])
                    snapshot["snapshotId"] = "taste-v1-" + snapshot["payloadSha256"]
                elif issue == "window":
                    window["outcome_freeze"] = stamp(BASE + timedelta(days=11))
                else:
                    raw["receipts"][5]["observed_at"] = stamp(BASE + timedelta(days=4))
                with self.assertRaisesRegex((DatasetBlocked, ValidationError), error):
                    build(raw)

    def test_feature_schema_and_multi_variant_guard(self):
        raw = evidence_json()
        snapshot = raw["windows"][0]["taste_catalog"]
        variant = dict(snapshot["payload"]["variants"][2], id=uid(99), sku="SECOND")
        snapshot["payload"]["variants"].append(variant)
        snapshot["payload"]["counts"]["variants"] += 1
        snapshot["payloadSha256"] = digest(snapshot["payload"])
        snapshot["snapshotId"] = "taste-v1-" + snapshot["payloadSha256"]
        dataset = build(raw)
        query = dataset.queries[0]
        self.assertTrue(query.has_taste)
        self.assertFalse(query.can_rerank)
        with self.assertRaisesRegex(ValueError, "VARIANT_PREPARATION_AGGREGATION_UNAVAILABLE"):
            vector(query.samples[0].features, dataset.attributes, ())

    def test_labels_use_retained_outcome_quantity_without_changing_cutoff_features(self):
        raw = evidence_json()
        original = build(raw).queries[0]
        receipt = raw["receipts"][6]
        returned = {
            "source": "SIGNED_PRODUCT_RETURN", "return_item_id": "return", "customer_id": receipt["customer_id"],
            "order_id": receipt["order_id"], "order_item_id": "line", "variant_id": uid(13), "quantity": "0.5",
            "occurred_at": stamp(BASE + timedelta(days=2)), "observed_at": stamp(BASE + timedelta(days=3)),
        }
        raw["returns"].append(returned)
        self.assertEqual(build(raw).queries[0].samples, original.samples)
        returned["quantity"] = "1"
        fully_returned = build(raw).queries[0]
        self.assertEqual([row.features for row in fully_returned.samples], [row.features for row in original.samples])
        self.assertFalse(any(row.label for row in fully_returned.samples))

        returned["occurred_at"] = stamp(BASE + timedelta(days=3))
        self.assertEqual(build(raw).queries[0].samples, original.samples)
        returned["occurred_at"] = stamp(BASE + timedelta(days=2))
        returned["observed_at"] = stamp(BASE + timedelta(days=4))
        with self.assertRaisesRegex(DatasetBlocked, "LATE_OUTCOME_RETURN"):
            build(raw)

    def test_outcome_requires_complete_streams_and_linked_interactions(self):
        for stream in ("returns", "interactions"):
            with self.subTest(stream=stream):
                raw = evidence_json()
                raw[f"{stream}_complete_through"] = raw["windows"][-1]["cutoff"]
                with self.assertRaisesRegex(DatasetBlocked, "INCOMPLETE_OBSERVATION_WINDOW"):
                    build(raw)
        raw = evidence_json()
        raw["interaction_purchases"][6]["order_id"] = None
        with self.assertRaisesRegex(DatasetBlocked, "OUTCOME_INTERACTION_WITHOUT_MATCHING_RECEIPT"):
            build(raw)

    def test_legacy_taste_evidence_cannot_enter_v2_fit_or_taste_cohort(self):
        draft = Path(__file__).resolve().parents[2] / (
            "Catalog-Service/src/main/resources/taste-taxonomies/hvtra-tea-v2.draft.json"
        )
        manifest = json.loads(draft.read_text(encoding="utf-8"))["manifest"]
        manifest["attributes"].sort(key=lambda item: item["attributeCode"])
        for item in manifest["attributes"]:
            item["values"].sort(key=lambda value: value["valueCode"])
        version, checksum = manifest["versionCode"], digest(manifest)
        raw = evidence_json()
        raw.update(taxonomy_version=version, taxonomy_sha256=checksum)
        for window in raw["windows"]:
            snapshot = window["taste_catalog"]
            snapshot["payload"]["taxonomies"].append(
                {"versionCode": version, "manifestSha256": checksum, "state": "PUBLISHED", "manifest": manifest})
            snapshot["payload"]["supportedTaxonomyVersions"] = sorted((TAXONOMY, version))
            snapshot["payloadSha256"] = digest(snapshot["payload"])
            snapshot["snapshotId"] = "taste-v1-" + snapshot["payloadSha256"]

        dataset = build(raw)
        self.assertEqual(len(dataset.attributes), 7)
        self.assertNotIn("SWEETNESS", dataset.attributes)
        self.assertFalse(any(query.has_taste or query.can_rerank for query in dataset.queries))
        self.assertTrue(any(query.hybrid_has_signal for query in dataset.queries))
        self.assertFalse(any(row.features.taste for query in dataset.queries for row in query.samples))
        with patch("app.training.train_taste_ranking._fit") as fit:
            with self.assertRaisesRegex(DatasetBlocked, "INSUFFICIENT_EXPLORE_TRAIN_OR_VALIDATION_SIGNAL"):
                train_and_evaluate(dataset, "EXPLORE", "test")
        fit.assert_not_called()

    def test_exact_policy_metadata_preserves_legacy_artifact_and_rejects_policy_drift(self):
        dataset = build(evidence_json())
        names = feature_names(dataset.attributes, ())
        count = len(names)
        model = LogisticModel(
            attributes=list(dataset.attributes), categories=[], feature_names=list(names),
            centers=[0.0] * count, scales=[1.0] * count, coefficients=[0.0] * count,
            intercept=0.0, regularization=0.01,
        )
        artifact = TasteRankingArtifact(
            model_version="test", mode="EXPLORE", taxonomy_version=dataset.taxonomy_version,
            taxonomy_sha256=dataset.taxonomy_sha256, dataset_sha256=dataset.fingerprint,
            training_cutoff=BASE, selection_cutoff=BASE + timedelta(days=1), evaluation_cutoff=BASE + timedelta(days=2),
            trained_at=BASE + timedelta(days=3), evaluated_at=BASE + timedelta(days=3), evaluation_status="EVALUATED",
            evaluation_sha256="a" * 64, model=model, model_sha256=fingerprint(model.model_dump(mode="json")),
        )
        legacy = artifact.model_dump(exclude={"matching_policy_version"})
        self.assertEqual(TasteRankingArtifact.model_validate(legacy), artifact)
        with self.assertRaises(ValidationError):
            TasteRankingArtifact.model_validate(dict(legacy, matching_policy_version="unknown-policy"))
        taste = TasteFeatureBatch("TASTE_FEATURE_READY", taxonomy_version=dataset.taxonomy_version,
                                  taxonomy_sha256=dataset.taxonomy_sha256)
        rows = tuple(row.features for row in dataset.queries[0].samples)
        reranker = TasteReranker(artifact=artifact)
        self.assertEqual(reranker.score(rows, taste).scores, {row.product_id: 0.5 for row in rows if row.has_signal})
        self.assertEqual(reranker.score(rows, replace(taste, matching_policy_version="unknown-policy")).status,
                         "MATCHING_POLICY_INCOMPATIBLE")
        with self.assertRaisesRegex(DatasetBlocked, "MATCHING_POLICY_INCOMPATIBLE"):
            train_and_evaluate(replace(dataset, matching_policy_version="unknown-policy"), "EXPLORE", "test")
        with self.assertRaisesRegex(ValueError, "MATCHING_POLICY_INCOMPATIBLE"):
            evaluate("EXPLORE", (), model, matching_policy_version="unknown-policy")
        queries = tuple(query for query in dataset.queries if query.split == "TEST" and query.mode == "EXPLORE")
        report = evaluate("EXPLORE", queries, model)
        self.assertEqual(report["matching_policy_version"], EXACT_POLICY_VERSION)
        cohort = report["cohorts"]["WITHOUT_TASTE"]["rankings"]
        self.assertEqual(cohort["HYBRID_V1"], cohort["HYBRID_V1_TASTE_RERANKER"])

    def test_cli_publishes_evaluated_artifact_and_matching_report(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "evidence.json"
            output = Path(directory) / "trained"
            raw = json.dumps(evidence_json()).encode()
            source.write_bytes(raw)
            with patch("sys.argv", ["train_taste_ranking", "--evidence", str(source), "--evidence-sha256",
                                    hashlib.sha256(raw).hexdigest(), "--mode", "FAMILIAR",
                                    "--model-version", "test-1", "--output", str(output)]):
                with redirect_stdout(StringIO()) as stdout:
                    self.assertEqual(main(), 0)
            result = json.loads(stdout.getvalue())
            serialized = (output / "reranker.json").read_bytes()
            model = TasteRankingArtifact.model_validate_json(serialized)
            report = json.loads((output / "evaluation.json").read_bytes())
            self.assertEqual(result["artifact_sha256"], hashlib.sha256(serialized).hexdigest())
            self.assertEqual(model.evaluation_sha256, fingerprint(report))
            self.assertEqual(model.activation_status, "INACTIVE")

    def test_activation_checksums_and_serving_fallback(self):
        evidence = EvidenceBundle.model_validate_json(json.dumps(evidence_json()))
        learned, _ = train_and_evaluate(build_dataset(evidence), "EXPLORE", "test-1")
        tampered = learned.model_dump(mode="json")
        tampered["model"]["intercept"] += 1
        with self.assertRaisesRegex(ValidationError, "Model checksum mismatch"):
            TasteRankingArtifact.model_validate_json(json.dumps(tampered))
        with TemporaryDirectory() as directory:
            artifact_path = Path(directory) / "reranker.json"
            manifest_path = Path(directory) / "activation.json"
            raw = learned.model_dump_json().encode()
            artifact_path.write_bytes(raw)
            approval = {"mode": "EXPLORE", "model_version": learned.model_version, "artifact_path": str(artifact_path),
                        "artifact_sha256": hashlib.sha256(raw).hexdigest(), "evaluation_sha256": learned.evaluation_sha256,
                        "approval_reference": "test-review", "approved_at": stamp(datetime.now(timezone.utc))}
            manifest_path.write_text(json.dumps({"schema_version": 1, "approvals": [approval]}), encoding="utf-8")
            self.assertIsNone(load_approved_rerankers(None)["EXPLORE"].artifact)
            gates = load_approved_rerankers(str(manifest_path))
            self.assertIsNotNone(gates["EXPLORE"].artifact)
            self.assertIsNone(gates["FAMILIAR"].artifact)
            artifact_path.write_bytes(raw + b" ")
            self.assertIsNone(load_approved_rerankers(str(manifest_path))["EXPLORE"].artifact)

        window = evidence.windows[0]
        products = tuple(CatalogProduct(row.product_id, row.product_code, row.name, row.category_code)
                         for row in window.catalog)
        v1 = build_artifact(products, (PurchaseCount("user", uid(1), 4), PurchaseCount("user", uid(2), 1)))
        history = ExploreHistory(5, {uid(1): 4, uid(2): 1}, {uid(1), uid(2)})
        histories = Mock()
        histories.history.return_value = history
        histories.commercial_product_ids.return_value = {product.product_id for product in products}
        catalog = Mock()
        catalog.active_catalog.return_value = products
        store = Mock()
        store.load.return_value = v1
        taste_client = Mock()
        taste_client.latest.return_value = window.customers[0].survey
        fallback = ExploreRecommendationService(store, histories, catalog).recommend(None, uid(100), 5)
        service = ExploreRecommendationService(store, histories, catalog, taste_reranker=gates["EXPLORE"],
            taste_features=PersonalizedTasteFeatures(taste_client, lambda: _projection(window)))
        active = service.recommend(None, uid(100), 5)
        self.assertNotEqual(active.product.model_contract_version, CONTRACT_VERSION)
        taste_client.latest.side_effect = RuntimeError("Taste read failed")
        self.assertEqual(service.recommend(None, uid(100), 5), fallback)
