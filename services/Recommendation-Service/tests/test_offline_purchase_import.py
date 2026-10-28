"""Pure fixtures, fake MySQL connection and SQLite memory; no broker or services."""
import copy
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import importlib.util
from io import StringIO
import json
import os
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch
from uuid import UUID

from app.repositories.historical_purchase_source import (
    HistoricalSource, PROJECTIONS, SourceReadError, read_historical_source,
)
from app.service.completed_purchase_evidence import build_completed_purchase
from app.service.offline_purchase_adapter import (
    DIAGNOSTIC_WARNING, VerificationPolicy, analyze_historical_source,
    simulate_replay, simulation_action,
)
from app.service.offline_purchase_workbook import verify_sales_only_workbook
from app.service.report_purchase_service import record_completed_order


def uid(number):
    return str(UUID(int=number))


def fixture():
    when = datetime(2025, 6, 1, 10)
    tables = dict(lfm_seed_ord=[dict(ord_no=1, id_hex=UUID(int=1).hex, cust_no=1,
        event_time=when, status="COMPLETED", payment_status="PAID", n_lines=2)],
        lfm_seed_ord_line=[dict(ord_no=1, variant_no=1, qty=3), dict(ord_no=1, variant_no=2, qty=2)],
        lfm_seed_cust=[dict(cust_no=1, id_hex=UUID(int=2).hex)],
        lfm_seed_variant=[dict(variant_no=n, id_hex=UUID(int=v).hex, product_hex=UUID(int=4).hex,
                               pack_key="P100") for n, v in ((1, 3), (2, 7))],
        products=[dict(id=uid(4))], product_variants=[dict(id=uid(v), product_id=uid(4),
            variant_name="Fixture 100 Gram") for v in (3, 7)], report_purchase_orders=[],
        interaction_events=[dict(event_type="PURCHASE", order_id=uid(1), customer_id=uid(2),
            product_id=uid(4), product_variant_id=uid(v), source_event_id=uid(6),
            order_item_id=uid(i), quantity=Decimal(q), event_time=when, created_at=when)
            for v, i, q in ((3, 5, "3.000"), (7, 8, "2.000"))])
    return HistoricalSource(tables, {})


def with_second_order(source):
    h = copy.deepcopy(source.tables["lfm_seed_ord"][0])
    h.update(ord_no=2, id_hex=UUID(int=101).hex)
    source.tables["lfm_seed_ord"].append(h)
    for original in list(source.tables["lfm_seed_ord_line"]):
        source.tables["lfm_seed_ord_line"].append(dict(original, ord_no=2))
    for original in list(source.tables["interaction_events"]):
        source.tables["interaction_events"].append(dict(original, order_id=uid(101),
            source_event_id=uid(106), order_item_id=uid(UUID(original["order_item_id"]).int + 100)))
    return source


def verified_policy(**changes):
    return replace(VerificationPolicy(source_timezone=timezone.utc, timezone_evidence="fixture-clock",
        provenance_evidence="fixture-source", unit_contract_evidence="fixture-unit", usage_approval="fixture-only"),
        **changes)


def evidence():
    return analyze_historical_source(fixture(), diagnostic_assume_utc=True).decisions[0].evidence


def canonical_arguments():
    e = evidence()
    return dict(order_id=e.order_id, source_event_id=e.source_event_id, customer_id=e.customer_id,
        status="COMPLETED", payment_status="PAID", occurred_at=e.occurred_at.replace(tzinfo=timezone.utc),
        lines=[line.as_dict() for line in e.lines])


class CanonicalBuilderTests(unittest.TestCase):
    def test_existing_snapshot_import_uses_exact_same_pure_fingerprint(self):
        from app.service.evidence_fingerprint import fingerprint
        from app.service.recommendation_snapshot_service import fingerprint as existing
        self.assertIs(existing, fingerprint)
        import hashlib
        value = {"name": "Trà", "lines": [{"quantity": "2"}], "customer_id": None}
        expected = hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                             ensure_ascii=False).encode()).hexdigest()
        self.assertEqual(fingerprint(value), expected)

    def test_valid_completed_paid_and_multiple_lines(self):
        result = build_completed_purchase(**canonical_arguments())
        self.assertEqual([line.quantity for line in result.lines], ["3", "2"])
        self.assertIsNone(result.occurred_at.tzinfo)

    def test_invalid_status_and_payment(self):
        for change in (dict(status="CANCELLED"), dict(payment_status="UNPAID")):
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, "ineligible_status"):
                build_completed_purchase(**dict(canonical_arguments(), **change))

    def test_missing_and_invalid_order_source_customer_identity(self):
        for key in ("order_id", "source_event_id", "customer_id"):
            for value in ("", "not-uuid"):
                with self.subTest(key=key, value=value), self.assertRaisesRegex(ValueError, "invalid_identity"):
                    build_completed_purchase(**dict(canonical_arguments(), **{key: value}))

    def test_guest_order(self):
        self.assertIsNone(build_completed_purchase(**dict(canonical_arguments(), customer_id=None)).customer_id)

    def test_empty_duplicate_and_invalid_product_lines(self):
        base = canonical_arguments()
        for lines in ([], [base["lines"][0]] * 2,
                      [{"quantity": "1"}], [None],
                      [dict(base["lines"][0], product_id="bad")],
                      [dict(base["lines"][0], product_variant_id="")]):
            with self.subTest(lines=lines), self.assertRaises(ValueError):
                build_completed_purchase(**dict(base, lines=lines))

    def test_non_positive_nonfinite_and_invalid_quantity(self):
        for quantity in ("0", "-1", "NaN", "Infinity", "bad", None):
            base = canonical_arguments()
            base["lines"][0]["quantity"] = quantity
            with self.subTest(quantity=quantity), self.assertRaisesRegex(ValueError, "invalid_quantity"):
                build_completed_purchase(**base)

    def test_fraction_is_not_rounded_by_shared_builder(self):
        base = canonical_arguments()
        base["lines"][0]["quantity"] = "1.2500"
        self.assertEqual(build_completed_purchase(**base).lines[0].quantity, "1.25")

    def test_decimal_normalization_sort_and_hash_stability(self):
        base = canonical_arguments()
        first = build_completed_purchase(**base)
        base["lines"].reverse()
        for line in base["lines"]:
            line["quantity"] = Decimal(line["quantity"]).quantize(Decimal("0.000"))
        self.assertEqual(build_completed_purchase(**base), first)

    def test_invalid_timestamp(self):
        for value in (None, "2025-01-01", datetime(2025, 1, 1)):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "timestamp_requires_timezone"):
                build_completed_purchase(**dict(canonical_arguments(), occurred_at=value))

    def test_timezone_aware_normalization(self):
        base = canonical_arguments()
        base["occurred_at"] = datetime(2025, 6, 1, 17, tzinfo=timezone(timedelta(hours=7)))
        self.assertEqual(build_completed_purchase(**base).occurred_at, datetime(2025, 6, 1, 10))

    def test_hash_and_lines_match_unchanged_kafka_recorder(self):
        source = fixture()
        events = [SimpleNamespace(**row) for row in source.tables["interaction_events"]]
        payload = dict(eventType="ORDER_COMPLETED", orderStatus="COMPLETED", paymentStatus="PAID",
            eventId=uid(6), orderId=uid(1), customerId=uid(2), occurredAt="2025-06-01T10:00:00Z",
            items=[dict(orderItemId=e.order_item_id, quantity=str(e.quantity)) for e in events])
        db = MagicMock()
        db.scalar.return_value = None
        record_completed_order(db, payload, events)  # Fixture only, no Kafka producer/consumer.
        row = db.add.call_args.args[0]
        built = build_completed_purchase(**canonical_arguments())
        self.assertEqual(row.evidence_hash, built.evidence_hash)
        self.assertEqual(row.lines, [line.as_dict() for line in built.lines])
        self.assertEqual(row.occurred_at, built.occurred_at)
        db.commit.assert_not_called()


class OfflineAdapterTests(unittest.TestCase):
    def setUp(self):
        self.source = fixture()

    def decision(self, **kwargs):
        return analyze_historical_source(self.source, **kwargs).decisions[0]

    def test_strict_defaults_never_assume_timezone_or_make_evidence(self):
        result = self.decision()
        self.assertEqual(result.category, "NOT_VERIFIED")
        self.assertIsNone(result.evidence)
        self.assertIn("timezone_not_verified", result.reasons)

    def test_diagnostic_does_not_verify_or_approve(self):
        result = self.decision(diagnostic_assume_utc=True)
        self.assertEqual(result.category, "NOT_VERIFIED")
        self.assertIsNotNone(result.evidence)
        self.assertIn("timezone_not_verified", result.reasons)

    def test_ready_requires_all_explicit_fixture_evidence(self):
        self.assertEqual(self.decision(policy=verified_policy()).category, "READY")
        for key in ("timezone_evidence", "provenance_evidence", "unit_contract_evidence", "usage_approval"):
            with self.subTest(key=key):
                self.assertEqual(self.decision(policy=verified_policy(**{key: ""})).category, "NOT_VERIFIED")

    def test_full_order_mapping(self):
        result = self.decision(diagnostic_assume_utc=True)
        self.assertEqual(result.evidence.order_id, uid(1))
        self.assertEqual(result.evidence.customer_id, uid(2))
        self.assertEqual(result.quantity, Decimal(5))
        self.assertEqual({line.product_id for line in result.evidence.lines}, {uid(4)})

    def test_partial_order_is_held(self):
        self.source.tables["interaction_events"].pop()
        self.assertEqual(self.decision().category, "NEEDS_REVIEW")

    def test_declared_line_mismatch_never_gets_diagnostic_payload(self):
        self.source.tables["lfm_seed_ord"][0]["n_lines"] = 3
        result = self.decision(diagnostic_assume_utc=True, policy=verified_policy())
        self.assertEqual(result.category, "NEEDS_REVIEW")
        self.assertIsNone(result.evidence)
        self.assertEqual(result.reasons, ("declared_lines_mismatch",))

    def test_missing_customer_mapping_is_not_a_guest(self):
        self.source.tables["lfm_seed_cust"] = []
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_guest_requires_source_and_events_to_agree(self):
        self.source.tables["lfm_seed_ord"][0]["cust_no"] = None
        for event in self.source.tables["interaction_events"]:
            event["customer_id"] = None
        self.assertIsNone(self.decision(diagnostic_assume_utc=True).evidence.customer_id)

    def test_missing_variant_mapping_and_catalog_mismatch(self):
        self.source.tables["lfm_seed_variant"].pop()
        self.assertEqual(self.decision().category, "BLOCKED")
        self.source = fixture()
        self.source.tables["product_variants"][0]["product_id"] = uid(99)
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_product_customer_and_timestamp_mismatch(self):
        for key, value in (("product_id", uid(99)), ("customer_id", uid(99)),
                           ("event_time", datetime(2025, 6, 2))):
            self.source = fixture()
            self.source.tables["interaction_events"][0][key] = value
            with self.subTest(key=key):
                self.assertEqual(self.decision().category, "BLOCKED")

    def test_quantity_precision_and_source_disagreement(self):
        for value in ("1.5", "NaN", "0", "4"):
            self.source = fixture()
            self.source.tables["interaction_events"][0]["quantity"] = Decimal(value)
            with self.subTest(value=value):
                self.assertEqual(self.decision().category, "BLOCKED")

    def test_duplicate_line_id(self):
        self.source.tables["interaction_events"][1]["order_item_id"] = uid(5)
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_duplicate_variant_requires_review(self):
        self.source.tables["interaction_events"][1]["product_variant_id"] = uid(3)
        self.assertEqual(self.decision().category, "NEEDS_REVIEW")

    def test_cross_order_event_collision_blocks_both(self):
        with_second_order(self.source)
        for row in self.source.tables["interaction_events"]:
            row["source_event_id"] = uid(6)
        run = analyze_historical_source(self.source, diagnostic_assume_utc=True)
        self.assertEqual([d.category for d in run.decisions], ["BLOCKED", "BLOCKED"])

    def test_cross_order_source_line_collision_blocks_both(self):
        with_second_order(self.source)
        self.source.tables["interaction_events"][2]["order_item_id"] = uid(5)
        run = analyze_historical_source(self.source)
        self.assertEqual([d.category for d in run.decisions], ["BLOCKED", "BLOCKED"])

    def test_multiple_source_events_in_one_order(self):
        self.source.tables["interaction_events"][1]["source_event_id"] = uid(99)
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_invalid_header_uuid_is_classified(self):
        self.source.tables["lfm_seed_ord"][0]["id_hex"] = "invalid"
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_unpaid_is_blocked(self):
        self.source.tables["lfm_seed_ord"][0]["payment_status"] = "UNPAID"
        self.assertEqual(self.decision().category, "BLOCKED")

    def test_orphan_rows_prevent_ready(self):
        self.source.tables["lfm_seed_ord_line"].append(dict(ord_no=99, variant_no=1, qty=1))
        self.assertEqual(self.decision(policy=verified_policy()).category, "NOT_VERIFIED")

    def test_result_is_deterministic_and_private(self):
        first = analyze_historical_source(self.source, diagnostic_assume_utc=True).summary
        for rows in self.source.tables.values():
            rows.reverse()
        second = analyze_historical_source(self.source, diagnostic_assume_utc=True).summary
        self.assertEqual(first, second)
        rendered = json.dumps(first, default=str)
        for number in range(1, 9):
            self.assertNotIn(uid(number), rendered)
        self.assertEqual(first["approved_real_writes"], 0)


class IdempotencyTests(unittest.TestCase):
    def setUp(self):
        self.e = evidence()
        self.row = dict(id=self.e.order_id, source_event_id=self.e.source_event_id,
            evidence_hash=self.e.evidence_hash, lines=[line.as_dict() for line in self.e.lines])

    def test_first_simulation_and_rerun_skip(self):
        self.assertEqual(simulate_replay([self.e], []), dict(first={"INSERT": 1}, rerun={"SKIP": 1}))

    def test_same_hash_skip(self):
        self.assertEqual(simulation_action(self.e, {self.row["id"]: self.row}), "SKIP")

    def test_changed_hash_conflicts(self):
        self.row["evidence_hash"] = "0" * 64
        self.assertEqual(simulation_action(self.e, {self.row["id"]: self.row}), "CONFLICT")

    def test_same_hash_changed_source_identity_conflicts(self):
        self.row["source_event_id"] = uid(99)
        self.assertEqual(simulation_action(self.e, {self.row["id"]: self.row}), "IDENTITY_CONFLICT")

    def test_source_event_collision(self):
        self.row["id"] = uid(99)
        self.assertEqual(simulation_action(self.e, {self.row["id"]: self.row}), "REJECT_SOURCE_EVENT_COLLISION")

    def test_source_line_collision(self):
        self.row.update(id=uid(99), source_event_id=uid(98))
        self.assertEqual(simulation_action(self.e, {self.row["id"]: self.row}), "REJECT_SOURCE_LINE_COLLISION")

    def test_target_conflict_classifies_blocked(self):
        source = fixture()
        source.tables["report_purchase_orders"] = [dict(self.row, evidence_hash="0" * 64)]
        decision = analyze_historical_source(source, diagnostic_assume_utc=True).decisions[0]
        self.assertEqual(decision.category, "BLOCKED")
        self.assertEqual(decision.reasons, ("conflict",))


class FakeCursor:
    def __init__(self, source):
        self.source, self.calls, self.last = source, [], ""
        self.wrong_db = False
        self.fail_table = None
        self.bad_engine = False
        self.incomplete = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def execute(self, sql, parameters=None):
        self.calls.append((sql, parameters))
        self.last = sql
        if not sql.startswith(("SELECT ", "SET SESSION ", "START TRANSACTION ")):
            raise AssertionError("write attempted")
        if self.fail_table and "FROM " + self.fail_table in sql:
            raise RuntimeError("secret fixture credential must not be exposed")

    def fetchall(self):
        sql = self.last
        if sql.startswith("SELECT DATABASE()"):
            return [dict(db="wrong" if self.wrong_db else "recommend_app", read_only=1)]
        if "information_schema.TABLES" in sql:
            return [dict(name=name, engine="MyISAM" if self.bad_engine else "InnoDB") for name in PROJECTIONS]
        if "information_schema.COLUMNS" in sql:
            return [dict(table_name="lfm_seed_ord", column_name="event_time", datetime_precision=0)]
        table = sql.split(" FROM ")[1].split()[0]
        rows = self.source.tables.get(table, [])
        if "COUNT(*)" in sql:
            return [dict(n=len(rows))]
        return copy.deepcopy(rows[:-1] if self.incomplete else rows)


class ReaderSafetyTests(unittest.TestCase):
    def setUp(self):
        self.cursor = FakeCursor(fixture())
        self.connection = MagicMock()
        self.connection.cursor.return_value = self.cursor
        self.connect = MagicMock(return_value=self.connection)
        self.settings = SimpleNamespace(mysql_database="recommend_app", mysql_host="unused", mysql_port=1,
            mysql_user="fixture", mysql_password=SimpleNamespace(get_secret_value=lambda: "fixture"))

    def read(self, **kwargs):
        return read_historical_source(self.settings, connect=self.connect, **kwargs)

    def test_select_only_repeatable_read_snapshot_and_rollback(self):
        result = self.read()
        self.assertEqual(result.metadata["counts"]["lfm_seed_ord"], 1)
        commands = [sql for sql, _ in self.cursor.calls]
        self.assertEqual(commands[:4], ["SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ",
            "SET SESSION TRANSACTION READ ONLY", "SET SESSION MAX_EXECUTION_TIME=%s",
            "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"])
        self.assertTrue(all("SELECT *" not in sql for sql in commands))
        self.connection.commit.assert_not_called()
        self.connection.rollback.assert_called_once()
        self.connection.close.assert_called_once()

    def test_reader_metadata_flows_through_adapter_and_workbook(self):
        source = self.read()
        run = analyze_historical_source(source, diagnostic_assume_utc=True)
        self.assertEqual(run.summary["timestamp_findings"]["source_datetime_precision"], 0)
        result = verify_sales_only_workbook([d.evidence for d in run.decisions if d.evidence])
        self.assertEqual(result["periods"][0]["variant_units"], 5)

    def test_wrong_configuration_never_connects(self):
        self.settings.mysql_database = "other"
        with self.assertRaises(SourceReadError):
            self.read()
        self.connect.assert_not_called()

    def test_wrong_runtime_identity_rolls_back_and_closes(self):
        self.cursor.wrong_db = True
        with self.assertRaises(SourceReadError):
            self.read()
        self.connection.rollback.assert_called_once()
        self.connection.close.assert_called_once()

    def test_row_bound_fails_closed(self):
        with self.assertRaisesRegex(SourceReadError, "source_row_bound_exceeded"):
            self.read(max_rows=1)
        self.connection.commit.assert_not_called()

    def test_incomplete_snapshot_rejected(self):
        self.cursor.incomplete = True
        with self.assertRaisesRegex(SourceReadError, "incomplete_source_snapshot"):
            self.read()

    def test_nontransactional_engine_rejected(self):
        self.cursor.bad_engine = True
        with self.assertRaisesRegex(SourceReadError, "consistent_snapshot_requires_innodb"):
            self.read()

    def test_driver_errors_are_sanitized_and_connection_closed(self):
        self.cursor.fail_table = "lfm_seed_ord"
        with self.assertRaises(SourceReadError) as error:
            self.read()
        self.assertNotIn("secret", str(error.exception))
        self.connection.rollback.assert_called_once()
        self.connection.close.assert_called_once()


class WorkbookAndCliSafetyTests(unittest.TestCase):
    def test_standalone_pure_import_needs_no_settings_or_kafka(self):
        environment = {key: value for key, value in os.environ.items() if not key.startswith("MYSQL_")}
        script = "import sys; from app.service import completed_purchase_evidence, offline_purchase_adapter, offline_purchase_workbook; assert 'app.core.config' not in sys.modules; assert not any(n.startswith('app.kafka') for n in sys.modules)"
        subprocess.run([sys.executable, "-B", "-c", script], cwd=Path(__file__).resolve().parents[1],
                       env=environment, check=True, capture_output=True)

    def test_sales_only_exact_units_empty_recommendations(self):
        result = verify_sales_only_workbook([evidence()])
        self.assertEqual(result["engine"], "sqlite:///:memory:")
        self.assertEqual(result["snapshots"], 0)
        self.assertEqual(result["periods"][0]["kpis"], [0, 0, 5, 0])
        self.assertEqual(result["periods"][0]["product_rows"], 1)
        self.assertEqual(result["periods"][0]["recommendation_detail_rows"], 0)

    def test_empty_strict_cohort_workbook(self):
        result = verify_sales_only_workbook([])
        self.assertEqual(result["inserted_orders"], 0)
        self.assertEqual(result["periods"][0]["kpis"], [0, 0, 0, 0])

    def cli(self):
        path = Path(__file__).resolve().parents[1] / "scripts/import_historical_report_purchases.py"
        spec = importlib.util.spec_from_file_location("offline_cli_fixture", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_cli_has_no_write_or_bypass_flags(self):
        cli = self.cli()
        for flag in ("--write", "--commit", "--force", "--allow-unverified", "--diagnostic"):
            with self.subTest(flag=flag), patch("sys.stderr", new=StringIO()), self.assertRaises(SystemExit):
                cli.parser().parse_args([flag])
        self.assertFalse(cli.parser().parse_args([]).diagnostic_assume_utc)

    def test_cli_zero_ready_is_success_and_report_has_no_ids(self):
        cli = self.cli()
        output = StringIO()
        with patch("app.core.settings.Settings"), \
                patch("app.repositories.historical_purchase_source.read_historical_source", return_value=fixture()), \
                patch.object(cli, "save_result", return_value=Path("external-report.md")), \
                patch("sys.stdout", new=output):
            self.assertEqual(cli.main([]), 0)
        result = json.loads(output.getvalue())
        self.assertEqual(result["summary"]["eligibility"]["READY"]["orders"], 0)
        self.assertEqual(result["summary"]["converted_payloads"], 0)
        self.assertNotIn(uid(2), output.getvalue())

    def test_diagnostic_warning_and_eligibility(self):
        cli = self.cli()
        output, errors = StringIO(), StringIO()
        with patch("app.core.settings.Settings"), \
                patch("app.repositories.historical_purchase_source.read_historical_source", return_value=fixture()), \
                patch.object(cli, "save_result", return_value=Path("external-report.md")), \
                patch("sys.stdout", new=output), patch("sys.stderr", new=errors):
            self.assertEqual(cli.main(["--diagnostic-assume-utc"]), 0)
        self.assertIn(DIAGNOSTIC_WARNING, errors.getvalue())
        self.assertEqual(json.loads(output.getvalue())["summary"]["eligibility"]["READY"]["orders"], 0)


if __name__ == "__main__":
    unittest.main()
