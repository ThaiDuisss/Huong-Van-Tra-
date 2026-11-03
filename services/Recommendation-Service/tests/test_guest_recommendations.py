"""Guest contract/security with real SELECT-only ranking on isolated SQLite."""
import unittest
from unittest.mock import patch

from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import Session
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.pool import StaticPool

from app.api.recommendations import router
from app.core import recommendation_auth as auth
from app.database.session import get_db
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.service.recommendation_snapshot_service import RecommendationSnapshotService
from tests.test_recommendation_modes_security import settings, signed


class GuestRecommendationTests(unittest.TestCase):
    def setUp(self):
        self.cfg = settings(recommendation_staff_roles="SALE_STAFF,MANAGER,SUPER_ADMIN")
        replay = auth.MemoryReplay()
        for target, value in (("get_settings", self.cfg), ("get_replay_store", replay)):
            change = patch.object(auth, target, return_value=value)
            change.start()
            self.addCleanup(change.stop)
        engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        self.addCleanup(engine.dispose)
        self.db = Session(engine)
        self.addCleanup(self.db.close)
        self.db.execute(text("CREATE TABLE products(id TEXT,product_code TEXT,name TEXT,status TEXT,is_deleted BOOLEAN)"))
        self.db.execute(text("CREATE TABLE customer_interactions(customer_id TEXT,product_id TEXT,total_purchase_count INTEGER)"))
        for index in range(8):
            product_id = f"00000000-0000-4000-8000-{index:012d}"
            self.db.execute(text("INSERT INTO products VALUES(:id,:code,'Tea','ACTIVE',0)"),
                            {"id": product_id, "code": f"TEA-{index}"})
            self.db.execute(text("INSERT INTO customer_interactions VALUES('demo-a',:id,:count)"),
                            {"id": product_id, "count": index})
        self.db.execute(text("INSERT INTO products VALUES('inactive','OFF','Off','INACTIVE',0)"))
        self.db.execute(text("INSERT INTO products VALUES('deleted','DEL','Deleted','ACTIVE',1)"))
        self.db.execute(text("INSERT INTO customer_interactions VALUES('demo-b','inactive',999)"))
        self.db.execute(text("INSERT INTO customer_interactions VALUES('demo-b','deleted',999)"))
        self.db.execute(text("INSERT INTO customer_interactions VALUES('demo-b','00000000-0000-4000-8000-000000000000',30)"))
        self.db.commit()
        self.statements = []
        event.listen(engine, "before_cursor_execute", lambda conn, cursor, statement, *args: self.statements.append(statement))
        app = FastAPI()
        app.include_router(router)
        app.dependency_overrides[get_db] = lambda: self.db
        self.client = TestClient(app)
        self.addCleanup(self.client.close)
        self.path = "/recommendations/guest"

    def request(self, query="limit=5", **identity):
        identity.setdefault("roles", "SALE_STAFF")
        identity.setdefault("customer", "")
        return self.client.get(self.path + "?" + query, headers=signed(self.path, query, **identity))

    def test_guest_global_top_five_null_identity_without_history_snapshot_or_dml(self):
        with patch.object(V1RecommendationRepository, "customer_history", side_effect=AssertionError("No guest history")), \
             patch.object(RecommendationSnapshotService, "issue", side_effect=AssertionError("No guest snapshot")), \
             patch.object(self.db, "commit") as commit, patch.object(self.db, "add") as add:
            response = self.request("limit=99")
            self.assertEqual(response.status_code, 200, response.text)
            body = response.json()
            self.assertIsNone(body["customerId"])
            self.assertEqual((body["source"], body["modelType"]), ("POPULARITY", "popularity"))
            self.assertEqual(len(body["items"]), 5)
            self.assertEqual(body["items"][0]["productCode"], "TEA-0")  # Aggregate across two customers.
            self.assertEqual(body["items"][0]["score"], 30)
            self.assertEqual([item["rank"] for item in body["items"]], list(range(1, 6)))
            self.assertNotIn("recommendationEventId", body)
            self.assertEqual(response.headers["cache-control"], "private, no-store")
            commit.assert_not_called()
            add.assert_not_called()
        self.assertTrue(self.statements)
        self.assertTrue(all(statement.lstrip().upper().startswith("SELECT") for statement in self.statements))

    def test_each_existing_pos_role_needs_explicit_grant(self):
        for role in ("SALE_STAFF", "MANAGER", "SUPER_ADMIN"):
            with self.subTest(role=role):
                self.assertEqual(self.request(roles=role).status_code, 200)
        self.cfg.recommendation_staff_roles = ""
        self.assertEqual(self.request().status_code, 403)

    def test_other_authenticated_roles_and_service_callers_denied(self):
        for role in ("CUSTOMER", "INVENTORY_STAFF", "ADMIN"):
            self.cfg.recommendation_staff_roles = "ADMIN" if role == "ADMIN" else "SALE_STAFF"
            self.assertEqual(self.request(roles=role).status_code, 403)
        self.assertEqual(self.request(user="service:orders", roles="SYSTEM").status_code, 403)

    def test_missing_tampered_or_replayed_context_denied(self):
        self.assertEqual(self.client.get(self.path).status_code, 401)
        headers = signed(self.path, "limit=5", roles="SALE_STAFF", customer="")
        self.assertEqual(self.client.get(self.path + "?limit=4", headers=headers).status_code, 401)
        self.assertEqual(self.client.get(self.path + "?limit=5", headers=headers).status_code, 200)
        self.assertEqual(self.client.get(self.path + "?limit=5", headers=headers).status_code, 401)

    def test_invalid_limit_and_empty_catalog(self):
        self.assertEqual(self.request("limit=0").status_code, 422)
        self.db.execute(text("DELETE FROM products"))  # Isolated SQLite fixture only.
        self.assertEqual(self.request().json()["items"], [])

    def test_database_error_is_sanitized_service_unavailable(self):
        with patch.object(V1RecommendationRepository, "popularity", side_effect=SQLAlchemyError("fixture detail")):
            response = self.request()
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("fixture detail", response.text)
