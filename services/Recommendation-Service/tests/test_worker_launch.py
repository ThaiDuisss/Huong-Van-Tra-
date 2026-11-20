"""K2 launch boundary: no app import, process start, broker or DB required."""
from datetime import datetime, timezone
import base64
import io
import json
import logging
import os
from pathlib import Path
import stat
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, Mock, patch

SERVICE = Path(__file__).resolve().parents[1]
ROOT = SERVICE.parents[1]
sys.path.insert(0, str(SERVICE / "scripts"))
import run_kafka_worker as launcher
import worker_launch_guard as guard

NOW = datetime(2026, 11, 11, 6, tzinfo=timezone.utc)
SECRET = "fixture-credential-DO-NOT-PRINT-4821"
ORDER_KEYS = json.dumps({"fixture-order": {"1": base64.b64encode(bytes(range(32))).decode()}})


def target():
    # Fictional unit-test metadata only, never an approval shipped for activation.
    return dict(environment="test", writer_role="recommendation-kafka-worker",
                binding_revision="fixture-r1", kafka_cluster_id="fixture-cluster",
                kafka_security_protocol="PLAINTEXT", order_stream_id="fixture-order-epoch",
                return_stream_id="fixture-return-epoch", mysql_server_id="fixture-db-id",
                kafka_bootstrap_servers="broker.example.invalid:19092",
                kafka_group_id="fixture-existing-group", kafka_order_completed_topic="order.completed",
                kafka_return_completed_topic="notification.return.completed",
                kafka_auto_offset_reset="earliest", mysql_host="db.example.invalid", mysql_port="3306",
                mysql_database="fixture_rec", mysql_user="fixture_account")


def binding():
    return {"schema_version": 1, "target": target(), "approval": {
        "status": "APPROVED", "approval_id": "fixture-approval", "approved_by": "fixture-owner",
        "not_before": "2026-11-11T00:00:00Z", "expires_at": "2026-11-12T00:00:00Z",
        "gate_evidence": {key: "fixture-evidence-" + key for key in guard.GATES},
    }}


def environment():
    values = target()
    return {"HVTRA_WORKER_ENABLED": "true", "KAFKA_ENABLE_AUTO_COMMIT": "false",
            **{name: values[key] for key, name in guard.TARGET_FIELDS.items()}}


def reader(document=None, secret=SECRET.encode(), order_keys=ORDER_KEYS.encode()):
    wire = json.dumps(binding() if document is None else document).encode()
    files = {guard.APPROVAL_FILE: wire, guard.PASSWORD_FILE: secret, guard.ORDER_KEYRING_FILE: order_keys}
    return Mock(side_effect=lambda path, limit: files[path])


class LaunchDecisionTests(unittest.TestCase):
    def invoke(self, values=None, files=None, runtime=None):
        output = io.StringIO()
        worker = runtime if runtime is not None else Mock()
        with patch.object(launcher.importlib, "import_module") as imports:
            code = launcher.main(environment() if values is None else values,
                                 read_file=reader() if files is None else files,
                                 now=NOW, runtime=worker, stderr=output)
        imports.assert_not_called()
        self.assertNotIn(SECRET, output.getvalue())
        return code, output.getvalue(), worker

    def test_disabled_does_not_read_approval_secret_or_import_runtime(self):
        for values in ({}, {"HVTRA_WORKER_ENABLED": "false"}, {"HVTRA_WORKER_ENABLED": ""}):
            with self.subTest(values=values):
                files = Mock(side_effect=AssertionError("must not read"))
                code, text, worker = self.invoke(values, files)
                self.assertEqual((code, text), (0, "WORKER_DISABLED\n"))
                worker.assert_not_called()
                files.assert_not_called()

    def test_wrong_opt_in_is_not_truthy_authorization(self):
        for value in ("1", "yes", "TRUE", " true", True, 1):
            with self.subTest(value=value):
                files = Mock()
                code, text, worker = self.invoke({"HVTRA_WORKER_ENABLED": value}, files)
                self.assertEqual((code, text), (64, "OPT_IN_INVALID\n"))
                worker.assert_not_called()
                files.assert_not_called()

    def test_missing_binding_refuses_without_runtime_import(self):
        files = Mock(side_effect=guard.LaunchDenied(78, "PROVISIONED_FILE_UNAVAILABLE"))
        code, text, worker = self.invoke(files=files)
        self.assertEqual((code, text), (78, "PROVISIONED_FILE_UNAVAILABLE\n"))
        worker.assert_not_called()
        self.assertEqual(files.call_count, 1)

    def test_unexpected_configuration_error_does_not_echo_secret_or_start_runtime(self):
        code, text, worker = self.invoke(files=Mock(side_effect=RuntimeError(SECRET)))
        self.assertEqual((code, text), (78, "LAUNCH_CONFIGURATION_FAILURE\n"))
        worker.assert_not_called()

    def test_every_declared_identity_must_match_independent_binding(self):
        for key, variable in guard.TARGET_FIELDS.items():
            if key in {"writer_role", "mysql_port", "kafka_auto_offset_reset", "kafka_security_protocol"}:
                continue
            with self.subTest(identity=key):
                values = environment()
                values[variable] = "development" if key == "environment" else values[variable] + "-challenger"
                files = reader()
                code, text, worker = self.invoke(values, files)
                self.assertEqual((code, text), (77, "TARGET_NOT_AUTHORIZED\n"))
                worker.assert_not_called()
                self.assertEqual(files.call_count, 1)  # secret not read on mismatch

    def test_wrong_writer_role_is_denied(self):
        values = environment()
        values["HVTRA_WORKER_WRITER_ROLE"] = "api-process"
        code, _, worker = self.invoke(values)
        self.assertEqual(code, 77)
        worker.assert_not_called()

    def test_different_valid_db_port_or_reset_policy_is_denied(self):
        for key, value in (("MYSQL_PORT", "3307"), ("KAFKA_AUTO_OFFSET_RESET", "latest")):
            values = environment()
            values[key] = value
            code, _, worker = self.invoke(values)
            self.assertEqual(code, 77)
            worker.assert_not_called()

    def test_missing_each_required_configuration_is_denied(self):
        for key in (*guard.TARGET_FIELDS.values(), "KAFKA_ENABLE_AUTO_COMMIT"):
            with self.subTest(missing=key):
                values = environment()
                values.pop(key)
                code, _, worker = self.invoke(values)
                self.assertIn(code, (64, 78))
                worker.assert_not_called()

    def test_unsafe_manual_commit_or_unsupported_security_is_denied(self):
        for key, value in (("KAFKA_ENABLE_AUTO_COMMIT", "true"),
                           ("HVTRA_WORKER_KAFKA_SECURITY_PROTOCOL", "SASL_SSL"),
                           ("KAFKA_SASL_PASSWORD", SECRET), ("KAFKA_SECURITY_PROTOCOL", "SSL")):
            values = environment()
            values[key] = value
            code, _, worker = self.invoke(values)
            self.assertIn(code, (64, 78))
            worker.assert_not_called()

    def test_invalid_port_identical_topics_or_credential_endpoint_is_denied(self):
        for key, value in (("MYSQL_PORT", "0"), ("MYSQL_PORT", "65536"), ("MYSQL_PORT", "03306"),
                           ("KAFKA_RETURN_COMPLETED_TOPIC", "order.completed"),
                           ("MYSQL_HOST", "user:" + SECRET + "@db.example.invalid"),
                           ("KAFKA_BOOTSTRAP_SERVERS", "protocol://user:" + SECRET + "@host:9092")):
            with self.subTest(field=key):
                values = environment()
                values[key] = value
                code, _, worker = self.invoke(values)
                self.assertEqual(code, 64)
                worker.assert_not_called()

    def test_binding_approval_window_status_and_all_gates_required(self):
        documents = []
        for key, value in (("status", "PROPOSED"), ("expires_at", "2026-11-11T06:00:00Z"),
                           ("not_before", "2026-11-12T00:00:00Z"), ("approved_by", ""),
                           ("expires_at", "2026-11-12T00:00:00"), ("gate_evidence", {})):
            doc = binding()
            doc["approval"][key] = value
            documents.append(doc)
        for doc in documents:
            code, _, worker = self.invoke(files=reader(doc))
            self.assertIn(code, (77, 78))
            worker.assert_not_called()

    def test_binding_schema_unknown_fields_and_non_string_identity_rejected(self):
        for changed in (dict(schema_version=True), dict(schema_version=2), dict(extra=SECRET),
                        dict(target=[]), dict(approval=[])):
            doc = binding()
            doc.update(changed)
            code, _, worker = self.invoke(files=reader(doc))
            self.assertEqual(code, 78)
            worker.assert_not_called()

    def test_duplicate_binding_key_or_invalid_utf8_never_imports_runtime(self):
        for wire in (b'{"schema_version":1,"schema_version":1}', b'\xff', b'NaN', b'null'):
            files = Mock(return_value=wire)
            code, _, worker = self.invoke(files=files)
            self.assertEqual(code, 78)
            worker.assert_not_called()

    def test_missing_or_malformed_secret_refuses(self):
        for secret in (b"", b"\xff", b"line\nsecond", b"nul\x00inside"):
            code, _, worker = self.invoke(files=reader(secret=secret))
            self.assertEqual(code, 78)
            worker.assert_not_called()

    def test_invalid_order_kafka_keyring_file_denies_launch(self):
        for document in (b"", b"not-json", b"[]", b'{"fixture":{},"fixture":{}}'):
            code, reason, worker = self.invoke(files=reader(order_keys=document))
            self.assertEqual((code, reason), (78, "REQUIRED_SECRET_INVALID\n"))
            worker.assert_not_called()

    def test_valid_binding_only_reaches_mocked_entry_and_no_success_claim_on_stop(self):
        code, text, worker = self.invoke()
        worker.assert_called_once()
        plan = worker.call_args.args[0]
        self.assertEqual(plan.settings["MYSQL_PASSWORD"], SECRET)
        self.assertEqual(plan.settings["ORDER_KAFKA_VERIFICATION_KEYS"], ORDER_KEYS)
        self.assertEqual(plan.settings["KAFKA_GROUP_ID"], target()["kafka_group_id"])
        self.assertEqual(plan.settings["KAFKA_ENABLE_AUTO_COMMIT"], "false")
        self.assertEqual(plan.settings["MYSQL_ECHO_SQL"], "false")
        self.assertNotIn(SECRET, repr(plan))
        with self.assertRaises(TypeError):
            plan.settings["MYSQL_HOST"] = "different-target"
        self.assertEqual((code, text), (70, "WORKER_STOP_UNCLASSIFIED\n"))

    def test_runtime_exception_or_system_exit_zero_is_not_silent_success_or_secret_leak(self):
        for failure in (RuntimeError(SECRET), SystemExit(0), SystemExit(SECRET)):
            code, text, worker = self.invoke(runtime=Mock(side_effect=failure))
            self.assertEqual((code, text), (70, "WORKER_RUNTIME_FAILURE\n"))
            worker.assert_called_once()

    def test_keyboard_interrupt_is_a_distinct_stop(self):
        code, text, _ = self.invoke(runtime=Mock(side_effect=KeyboardInterrupt))
        self.assertEqual((code, text), (130, "WORKER_INTERRUPTED\n"))


class ProvisioningTrustTests(unittest.TestCase):
    def test_root_or_non_posix_process_cannot_self_provision_approval(self):
        with patch.object(guard.os, "name", "nt"):
            with self.assertRaises(guard.LaunchDenied) as error:
                guard._read_owned_file(guard.APPROVAL_FILE, 64)
        self.assertEqual(error.exception.exit_code, 77)
        with patch.object(guard.os, "name", "posix"), patch.object(guard.os, "geteuid", return_value=0, create=True):
            with self.assertRaises(guard.LaunchDenied):
                guard._read_owned_file(guard.APPROVAL_FILE, 64)

    def test_owned_file_check_rejects_mutable_directory_before_open(self):
        fake = Mock()
        fake.parent.stat.return_value = SimpleNamespace(st_mode=stat.S_IFDIR | 0o777, st_uid=0)
        with patch.object(guard.os, "name", "posix"), patch.object(guard.os, "geteuid", return_value=65532, create=True), \
             patch.object(guard.os, "O_NOFOLLOW", 1, create=True), patch.object(guard.os, "open") as opened:
            with self.assertRaises(guard.LaunchDenied):
                guard._read_owned_file(fake, 64)
        opened.assert_not_called()

    def file_read(self, *, data=b"fixture", mode=stat.S_IFREG | 0o444, uid=0, limit=64):
        path = Mock()
        path.parent.stat.return_value = SimpleNamespace(st_mode=stat.S_IFDIR | 0o755, st_uid=0)
        stream = MagicMock()
        stream.__enter__.return_value = stream
        stream.read.return_value = data
        stream.fileno.return_value = 123
        with patch.object(guard.os, "name", "posix"), patch.object(guard.os, "geteuid", return_value=65532, create=True), \
             patch.object(guard.os, "O_NOFOLLOW", 8, create=True), patch.object(guard.os, "O_NONBLOCK", 16, create=True), \
             patch.object(guard.os, "open", return_value=123) as opened, \
             patch.object(guard.os, "fdopen", return_value=stream), \
             patch.object(guard.os, "fstat", return_value=SimpleNamespace(st_mode=mode, st_uid=uid)):
            value = guard._read_owned_file(path, limit)
        opened.assert_called_once_with(path, guard.os.O_RDONLY | 8 | 16)
        stream.read.assert_called_once_with(limit + 1)
        return value

    def test_read_only_owner_provisioned_file_is_read_with_a_size_budget(self):
        self.assertEqual(self.file_read(), b"fixture")

    def test_worker_owned_mutable_or_non_regular_file_cannot_be_an_approval(self):
        for values in (dict(uid=65532), dict(mode=stat.S_IFREG | 0o666), dict(mode=stat.S_IFLNK | 0o444)):
            with self.subTest(values=values), self.assertRaises(guard.LaunchDenied) as error:
                self.file_read(**values)
            self.assertEqual(error.exception.exit_code, 77)

    def test_empty_or_oversized_provisioned_file_is_denied(self):
        for data in (b"", b"12345"):
            with self.assertRaises(guard.LaunchDenied) as error:
                self.file_read(data=data, limit=4)
            self.assertEqual(error.exception.exit_code, 78)

    def test_file_system_errors_are_controlled_without_path_or_secret_text(self):
        path = Mock()
        path.parent.stat.side_effect = OSError(SECRET)
        with patch.object(guard.os, "name", "posix"), patch.object(guard.os, "geteuid", return_value=65532, create=True), \
             patch.object(guard.os, "O_NOFOLLOW", 8, create=True), self.assertRaises(guard.LaunchDenied) as error:
            guard._read_owned_file(path, 64)
        self.assertEqual(str(error.exception), "PROVISIONED_FILE_UNAVAILABLE")
        self.assertNotIn(SECRET, str(error.exception))


class RuntimeIsolationTests(unittest.TestCase):
    def test_actual_settings_use_only_approved_values_without_reading_any_dotenv(self):
        # Definitions/config only; the Kafka worker import remains a fake, so no
        # DB engine or Consumer is constructed. The external runner also blocks
        # all connection/process APIs for this test.
        plan = guard.authorize(environment(), read_file=reader(), now=NOW)
        real_import = launcher.importlib.import_module
        worker = SimpleNamespace(main=Mock())
        with patch.dict(sys.modules), patch.dict(os.environ, {
                "MYSQL_HOST": "unapproved-api-target", "PYDANTIC_SETTINGS_DEBUG": "true"}), \
             patch.object(sys, "path", sys.path.copy()), \
             patch.object(logging.getLogger(), "handlers", []), patch.object(logging.getLogger(), "level", logging.WARNING), \
             patch.object(logging, "raiseExceptions", True):
            provider = real_import("pydantic_settings.sources.providers.dotenv")

            def importing(name):
                if name == "app.core.settings":
                    return real_import(name)
                self.assertEqual(name, "app.kafka.worker")
                settings = real_import("app.core.config").get_settings()
                self.assertEqual(settings.mysql_host, target()["mysql_host"])
                self.assertEqual(settings.mysql_password.get_secret_value(), SECRET)
                self.assertEqual(settings.order_kafka_verification_keys.get_secret_value(), ORDER_KEYS)
                self.assertEqual(settings.kafka_group_id, target()["kafka_group_id"])
                self.assertFalse(settings.kafka_enable_auto_commit)
                self.assertFalse(settings.mysql_echo_sql)
                self.assertNotIn("app.database.session", sys.modules)
                return worker

            with patch.object(provider, "dotenv_values", side_effect=AssertionError("dotenv forbidden")) as dotenv, \
                 patch.object(launcher.importlib, "import_module", side_effect=importing):
                launcher._run_worker(plan)
            dotenv.assert_not_called()
        worker.main.assert_called_once_with()

    def test_runtime_disables_dotenv_before_importing_engine_or_consumer(self):
        plan = guard.authorize(environment(), read_file=reader(), now=NOW)
        definition = SimpleNamespace(Settings=SimpleNamespace(model_config={"env_file": ".env"}))
        worker = SimpleNamespace(main=Mock())
        seen = []

        def importing(name):
            seen.append(name)
            if name == "app.core.settings":
                return definition
            self.assertIsNone(definition.Settings.model_config["env_file"])
            self.assertEqual(os.environ["MYSQL_HOST"], target()["mysql_host"])
            self.assertNotIn("DATABASE_URL", os.environ)
            self.assertNotIn("PYDANTIC_SETTINGS_DEBUG", os.environ)
            return worker

        with patch.dict(sys.modules, clear=True), patch.dict(os.environ, {
                "DATABASE_URL": "mysql://" + SECRET, "PYDANTIC_SETTINGS_DEBUG": "true",
                "MYSQL_HOST": "wrong-ambient-target"}), patch.object(sys, "path", sys.path.copy()), \
             patch.object(launcher.importlib, "import_module", side_effect=importing), \
             patch.object(logging.getLogger(), "handlers", []), patch.object(logging.getLogger(), "level", logging.WARNING), \
             patch.object(logging, "raiseExceptions", True):
            launcher._run_worker(plan)
        self.assertEqual(seen, ["app.core.settings", "app.kafka.worker"])
        worker.main.assert_called_once_with()

    def test_reusing_api_process_is_refused_before_settings_or_environment_mutation(self):
        plan = guard.authorize(environment(), read_file=reader(), now=NOW)
        with patch.dict(sys.modules, {"app.core.config": Mock()}), patch.object(launcher.importlib, "import_module") as imports:
            with self.assertRaisesRegex(RuntimeError, "RUNTIME_ALREADY_IMPORTED"):
                launcher._run_worker(plan)
        imports.assert_not_called()

    def test_runtime_formatter_redacts_credentials_encoded_forms_and_uris(self):
        formatter = launcher._RedactingFormatter((SECRET, "p@ss word"))
        for content in (SECRET, "p%40ss%20word", "p%40ss+word", "mysql+pymysql://someone:other@host/db"):
            record = logging.LogRecord("fixture", logging.ERROR, "fixture.py", 1, "%s", (content,), None)
            rendered = formatter.format(record)
            self.assertNotIn(content, rendered)

    def test_runtime_formatter_redacts_exception_traceback(self):
        try:
            raise RuntimeError(SECRET)
        except RuntimeError:
            record = logging.LogRecord("fixture", logging.ERROR, "fixture.py", 1, "failure", (), sys.exc_info())
        self.assertNotIn(SECRET, launcher._RedactingFormatter((SECRET,)).format(record))


class LaunchProfileTests(unittest.TestCase):
    def test_default_api_cmd_and_lifecycle_still_only_launch_api(self):
        dockerfile = (SERVICE / "Dockerfile").read_text(encoding="utf-8")
        self.assertIn('CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]', dockerfile)
        self.assertNotIn("run_kafka_worker", dockerfile)
        self.assertNotIn("kafka.worker", (SERVICE / "app/main.py").read_text(encoding="utf-8"))

    def test_compose_worker_is_opt_in_no_restart_no_http_no_sidecar_or_env_file(self):
        profile = json.loads((ROOT / "deploy/compose.recommendation-worker.json").read_text(encoding="utf-8"))
        self.assertEqual(set(profile["services"]), {"recommendation-kafka-worker"})
        service = profile["services"]["recommendation-kafka-worker"]
        self.assertEqual(service["profiles"], ["recommendation-worker"])
        self.assertEqual(service["command"], ["python", "-B", "scripts/run_kafka_worker.py"])
        self.assertEqual(service["restart"], "no")
        self.assertEqual(service["environment"]["HVTRA_WORKER_ENABLED"], "${HVTRA_WORKER_ENABLED:-false}")
        for unsupported in ("ports", "expose", "env_file", "depends_on", "healthcheck", "deploy", "scale"):
            self.assertNotIn(unsupported, service)
        self.assertEqual(service["user"], "65532:65532")
        for volume in service["volumes"]:
            self.assertTrue(volume["read_only"])
            self.assertFalse(volume["bind"]["create_host_path"])
        self.assertNotIn("MYSQL_PASSWORD", service["environment"])

    def test_default_production_compose_and_ci_do_not_include_worker_profile(self):
        for path in (ROOT / "deploy/docker-compose.prod.yml", ROOT / ".github/workflows/migration-naming.yml"):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("compose.recommendation-worker", text)
            self.assertNotIn("run_kafka_worker", text)


if __name__ == "__main__":
    unittest.main()
