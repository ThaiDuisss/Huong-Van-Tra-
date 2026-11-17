"""The shared ingestion client must never auto-commit past a failed DB batch."""
import os
import unittest
from unittest.mock import patch, sentinel

from app.core.settings import Settings
from app.kafka.purchase_consumer import create_kafka_consumer


class KafkaConsumerConfigSafetyTests(unittest.TestCase):
    def settings(self, **overrides):
        return Settings(
            _env_file=None,
            mysql_user="config-unit-test",
            mysql_password="config-unit-test-placeholder",
            kafka_bootstrap_servers="127.0.0.1:1",
            kafka_group_id="config-unit-test",
            **overrides,
        )

    def test_manual_commit_configuration_preserves_client_options(self):
        settings = self.settings(kafka_enable_auto_commit=False, kafka_auto_offset_reset="latest")
        with patch("app.kafka.purchase_consumer.Consumer", return_value=sentinel.consumer) as client:
            result = create_kafka_consumer(settings)
        self.assertIs(result, sentinel.consumer)
        client.assert_called_once_with({
            "bootstrap.servers": "127.0.0.1:1",
            "group.id": "config-unit-test",
            "auto.offset.reset": "latest",
            "enable.auto.commit": False,
            "enable.auto.offset.store": False,
        })

    def test_explicit_auto_commit_rejected_before_client_construction(self):
        with patch("app.kafka.purchase_consumer.Consumer") as client:
            with self.assertRaisesRegex(ValueError, "auto-commit must be disabled"):
                create_kafka_consumer(self.settings(kafka_enable_auto_commit=True))
        client.assert_not_called()

    def test_environment_true_values_cannot_enable_auto_commit(self):
        for value in ("true", "1", "yes"):
            with self.subTest(value=value), patch.dict(os.environ, {"KAFKA_ENABLE_AUTO_COMMIT": value}):
                settings = self.settings()
                self.assertIs(settings.kafka_enable_auto_commit, True)
                with patch("app.kafka.purchase_consumer.Consumer") as client:
                    with self.assertRaisesRegex(ValueError, "auto-commit must be disabled"):
                        create_kafka_consumer(settings)
                client.assert_not_called()

    def test_environment_false_values_keep_both_manual_offset_flags(self):
        for value in ("false", "0", "no"):
            with self.subTest(value=value), patch.dict(os.environ, {"KAFKA_ENABLE_AUTO_COMMIT": value}):
                with patch("app.kafka.purchase_consumer.Consumer") as client:
                    create_kafka_consumer(self.settings())
                options = client.call_args.args[0]
                self.assertIs(options["enable.auto.commit"], False)
                self.assertIs(options["enable.auto.offset.store"], False)

    def test_default_settings_path_cannot_bypass_auto_commit_guard(self):
        settings = self.settings(kafka_enable_auto_commit=True)
        with patch("app.kafka.purchase_consumer.get_settings", return_value=settings) as configured:
            with patch("app.kafka.purchase_consumer.Consumer") as client:
                with self.assertRaisesRegex(ValueError, "auto-commit must be disabled"):
                    create_kafka_consumer()
        configured.assert_called_once_with()
        client.assert_not_called()


if __name__ == "__main__":
    unittest.main()
