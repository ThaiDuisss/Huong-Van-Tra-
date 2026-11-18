"""Pure receipt policy tests: no SQLAlchemy engine, Kafka client, or network."""
from dataclasses import replace
import json
import unittest

from app.kafka.receipt_canonical import Digest, ReceiptPolicyError
from app.kafka.receipt_hashes import (
    BUSINESS_POLICY,
    WIRE_POLICY,
    BusinessClaim,
    EventIdentity,
    approved_payload_hash,
    event_integrity,
    wire_fingerprint,
)
from app.kafka.receipt_projection import ALLOWLIST_POLICY, ApprovedReceipt
from app.kafka.receipt_policy import (
    AckEvidence,
    AckPolicyConfig,
    AckScenario,
    HmacKeyRing,
    HmacPurpose,
    IdentityDisposition,
    KeyReference,
    PayloadPolicyConfig,
    ReceiptFingerprint,
    ReceiptStatus,
    TransportIdentity,
    compare_receipt,
    decide_ack,
    transition_status,
    validate_digest,
    validate_payload,
    verify_approved_payload,
    verify_hmac,
)


EVENT_ID = "4a572f91-94ab-4ce7-82ee-1c74f8595622"
ORDER_ID = "312ee12b-67d9-46a9-af52-22159b75c82e"
KEY_ID = KeyReference("recommendation", "k2")
OLD_KEY_ID = KeyReference("recommendation", "k1")
# Test-only byte vectors; runtime must receive real key material from an operator.
CURRENT_SECRET = b"unit-test-current-hmac-secret-32b"
OLD_SECRET = b"unit-test-previous-hmac-secret-32b"


def payload(**updates):
    value = {
        "eventId": EVENT_ID,
        "eventType": "ORDER_COMPLETED",
        "eventVersion": 1,
        "sourceService": "order-service",
    }
    value.update(updates)
    return json.dumps(value, separators=(",", ":")).encode("utf-8")


def policy_config(max_payload_bytes=4096):
    return PayloadPolicyConfig(
        max_payload_bytes=max_payload_bytes,
        accepted_policy_versions=frozenset({"receipt-policy-v1"}),
        supported_event_versions=frozenset({1}),
        supported_event_types=frozenset({"ORDER_COMPLETED", "RETURN_COMPLETED"}),
        require_explicit_event_metadata=True,
    )


def keyring(*, include_old=True, previous=(OLD_KEY_ID,)):
    keys = {KEY_ID: CURRENT_SECRET}
    if include_old:
        keys[OLD_KEY_ID] = OLD_SECRET
    return HmacKeyRing(current=KEY_ID, previous=previous, secrets=keys)


def fingerprint(*, topic="order.completed", partition=0, offset=1,
                event_id=EVENT_ID, wire="1", event="2", business="3",
                key_version="k1", business_id=ORDER_ID):
    return ReceiptFingerprint(
        transport=TransportIdentity("dev-stream", topic, partition, offset),
        wire_key_id="recommendation",
        wire_digest=Digest(wire * 64, WIRE_POLICY, key_version),
        event=EventIdentity("dev-stream", "order-service", event_id,
                            "ORDER_COMPLETED", 1, False, False),
        event_key_id="recommendation",
        event_digest=Digest(event * 64, "hvtra-json-lexical-v1", key_version),
        business_claims=(BusinessClaim("dev-stream", "order-service",
                                       "ORDER_COMPLETION", business_id,
                                       business * 64, BUSINESS_POLICY),),
    )


class PayloadPolicyTests(unittest.TestCase):
    def test_exact_maximum_size_is_accepted_and_one_byte_over_is_rejected(self):
        raw = payload()
        exact = validate_payload(raw, namespace="dev-stream",
                                 policy_version="receipt-policy-v1",
                                 config=policy_config(len(raw)))
        self.assertEqual(exact.byte_length, len(raw))
        with self.assertRaisesRegex(ReceiptPolicyError, "PAYLOAD_TOO_LARGE"):
            validate_payload(raw + b" ", namespace="dev-stream",
                              policy_version="receipt-policy-v1",
                              config=policy_config(len(raw)))

    def test_missing_size_or_version_configuration_fails_closed(self):
        raw = payload()
        with self.assertRaisesRegex(ReceiptPolicyError, "MAX_PAYLOAD_BYTES"):
            validate_payload(raw, namespace="dev-stream",
                              policy_version="receipt-policy-v1",
                              config=replace(policy_config(), max_payload_bytes=None))
        with self.assertRaisesRegex(ReceiptPolicyError, "SUPPORTED_POLICY_VERSIONS"):
            validate_payload(raw, namespace="dev-stream",
                              policy_version="receipt-policy-v1",
                              config=replace(policy_config(), accepted_policy_versions=None))
        with self.assertRaisesRegex(ReceiptPolicyError, "SUPPORTED_EVENT_VERSIONS"):
            validate_payload(raw, namespace="dev-stream",
                              policy_version="receipt-policy-v1",
                              config=replace(policy_config(), supported_event_versions=frozenset()))

    def test_invalid_json_and_invalid_type_are_rejected(self):
        with self.assertRaisesRegex(ReceiptPolicyError, "INVALID_PAYLOAD"):
            validate_payload(b"{broken", namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=policy_config())
        with self.assertRaisesRegex(ReceiptPolicyError, "INVALID_PAYLOAD_TYPE"):
            validate_payload(None, namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=policy_config())

    def test_unsupported_policy_version_is_rejected(self):
        with self.assertRaisesRegex(ReceiptPolicyError, "UNSUPPORTED_POLICY_VERSION"):
            validate_payload(payload(), namespace="dev-stream",
                             policy_version="unreviewed-v9", config=policy_config())

    def test_unsupported_event_version_is_rejected(self):
        with self.assertRaisesRegex(ReceiptPolicyError, "UNSUPPORTED_SCHEMA_VERSION"):
            validate_payload(payload(eventVersion=2), namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=policy_config())

    def test_missing_identity_and_unsupported_event_type_are_rejected(self):
        with self.assertRaisesRegex(ReceiptPolicyError, "MISSING_REQUIRED_IDENTITY"):
            validate_payload(payload_without("eventId"), namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=policy_config())
        with self.assertRaisesRegex(ReceiptPolicyError, "UNSUPPORTED_EVENT_TYPE"):
            validate_payload(payload(eventType="UNRECOGNIZED"), namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=policy_config())

    def test_missing_explicit_metadata_policy_fails_closed(self):
        config = replace(policy_config(), require_explicit_event_metadata=None)
        with self.assertRaisesRegex(ReceiptPolicyError, "EXPLICIT_METADATA_POLICY"):
            validate_payload(payload(), namespace="dev-stream",
                             policy_version="receipt-policy-v1", config=config)


def payload_without(field):
    value = json.loads(payload())
    del value[field]
    return json.dumps(value, separators=(",", ":")).encode("utf-8")


class DigestAndHmacPolicyTests(unittest.TestCase):
    def test_digest_validation_checks_format_policy_and_key_version(self):
        digest = Digest("a" * 64, WIRE_POLICY, "k2")
        self.assertEqual(validate_digest(digest, expected_policy_version=WIRE_POLICY,
                                         expected_key_version="k2"), "a" * 64)
        for value, expected, key in (("not-a-digest", WIRE_POLICY, None),
                                     ("A" * 64, WIRE_POLICY, None),
                                     (Digest("a" * 64, "wrong", "k2"), WIRE_POLICY, "k2"),
                                     (digest, WIRE_POLICY, "k1")):
            with self.subTest(value=value, expected=expected, key=key):
                with self.assertRaises(ReceiptPolicyError):
                    validate_digest(value, expected_policy_version=expected,
                                    expected_key_version=key)

    def test_approved_payload_sha256_is_verified_against_approved_document(self):
        receipt = ApprovedReceipt({"policy_version": ALLOWLIST_POLICY,
                                   "replay_level": "DIAGNOSTIC_ONLY", "payload": None})
        expected = verify_approved_payload(receipt, approved_payload_hash(receipt))
        self.assertEqual(expected.policy_version, "hvtra-approved-hash-v1")
        with self.assertRaisesRegex(ReceiptPolicyError, "DIGEST_MISMATCH"):
            verify_approved_payload(receipt, "f" * 64)

    def test_current_wire_key_and_event_key_are_verified(self):
        raw = payload()
        wire = wire_fingerprint(raw, record_key=b"order-key", headers=[],
                                keys={"k2": CURRENT_SECRET}, key_version="k2")
        event = event_integrity(raw, headers=[], keys={"k2": CURRENT_SECRET},
                                key_version="k2")
        self.assertEqual(verify_hmac(HmacPurpose.WIRE, raw, wire.value,
                                     key_id="recommendation", key_version="k2",
                                     keyring=keyring(), record_key=b"order-key", headers=[]), wire)
        self.assertEqual(verify_hmac(HmacPurpose.EVENT, raw, event.value,
                                     key_id="recommendation", key_version="k2",
                                     keyring=keyring(), headers=[]), event)

    def test_previous_key_is_accepted_only_when_explicitly_retained(self):
        raw = payload()
        old = wire_fingerprint(raw, record_key=None, headers=None,
                               keys={"k1": OLD_SECRET}, key_version="k1")
        verified = verify_hmac(HmacPurpose.WIRE, raw, old.value,
                               key_id="recommendation", key_version="k1",
                               keyring=keyring(), headers=None)
        self.assertEqual(verified.key_version, "k1")

    def test_missing_old_key_and_unknown_key_metadata_fail_closed(self):
        raw = payload()
        with self.assertRaisesRegex(ReceiptPolicyError, "MISSING_OLD_KEY"):
            verify_hmac(HmacPurpose.EVENT, raw, "a" * 64, key_id="recommendation",
                        key_version="k1", keyring=keyring(include_old=False))
        with self.assertRaisesRegex(ReceiptPolicyError, "UNKNOWN_KEY_ID"):
            verify_hmac(HmacPurpose.EVENT, raw, "a" * 64, key_id="other",
                        key_version="k2", keyring=keyring())
        with self.assertRaisesRegex(ReceiptPolicyError, "UNKNOWN_KEY_VERSION"):
            verify_hmac(HmacPurpose.EVENT, raw, "a" * 64, key_id="recommendation",
                        key_version="k9", keyring=keyring())

    def test_missing_rotation_config_short_key_and_invalid_signature_fail_closed(self):
        raw = payload()
        with self.assertRaisesRegex(ReceiptPolicyError, "PREVIOUS_KEY_POLICY"):
            verify_hmac(HmacPurpose.WIRE, raw, "a" * 64, key_id="recommendation",
                        key_version="k2", keyring=HmacKeyRing(KEY_ID, None,
                                                              {KEY_ID: CURRENT_SECRET}))
        with self.assertRaisesRegex(ReceiptPolicyError, "INVALID_HMAC_KEY"):
            verify_hmac(HmacPurpose.WIRE, raw, "a" * 64, key_id="recommendation",
                        key_version="k2", keyring=HmacKeyRing(KEY_ID, (), {KEY_ID: b"short"}))
        with self.assertRaisesRegex(ReceiptPolicyError, "INVALID_SIGNATURE"):
            verify_hmac(HmacPurpose.WIRE, raw, "a" * 64, key_id="recommendation",
                        key_version="k2", keyring=keyring())
        with self.assertRaisesRegex(ReceiptPolicyError, "MISSING_HMAC_KEY_POLICY"):
            verify_hmac(HmacPurpose.WIRE, raw, "a" * 64, key_id="recommendation",
                        key_version="k2", keyring=None)
        with self.assertRaisesRegex(ReceiptPolicyError, "MISSING_CURRENT_KEY"):
            verify_hmac(HmacPurpose.EVENT, raw, "a" * 64, key_id="recommendation",
                        key_version="k1", keyring=HmacKeyRing(
                            KEY_ID, (OLD_KEY_ID,), {OLD_KEY_ID: OLD_SECRET}))


class IdentityPolicyTests(unittest.TestCase):
    def test_new_transport_and_content_is_new(self):
        decision = compare_receipt(fingerprint(), [])
        self.assertEqual(decision.disposition, IdentityDisposition.NEW)
        self.assertFalse(decision.preserve_first_receipt)

    def test_same_transport_and_same_content_is_duplicate(self):
        prior = fingerprint()
        decision = compare_receipt(fingerprint(), [prior])
        self.assertEqual(decision.disposition,
                         IdentityDisposition.SAME_TRANSPORT_SAME_CONTENT)

    def test_same_transport_with_changed_content_is_conflict_and_preserves_first(self):
        prior = fingerprint()
        decision = compare_receipt(fingerprint(wire="4"), [prior])
        self.assertEqual(decision.disposition, IdentityDisposition.CONFLICT)
        self.assertTrue(decision.preserve_first_receipt)

    def test_same_event_and_same_content_at_new_transport_is_duplicate(self):
        prior = fingerprint()
        decision = compare_receipt(fingerprint(topic="return.completed", offset=9), [prior])
        self.assertEqual(decision.disposition, IdentityDisposition.SAME_EVENT_SAME_CONTENT)

    def test_same_event_with_changed_content_is_conflict(self):
        decision = compare_receipt(fingerprint(
            event="5", business_id="6f3b749c-1b0e-4d2b-8c59-790eac593612"),
                                   [fingerprint()])
        self.assertEqual(decision.disposition, IdentityDisposition.CONFLICT)
        self.assertTrue(decision.preserve_first_receipt)

    def test_same_business_claim_with_new_event_id_is_duplicate(self):
        other_event = "95dc2fc6-f48b-49d3-8a87-d6e5e695ac91"
        candidate = fingerprint(event_id=other_event, topic="return.completed",
                                offset=12, wire="4", event="5")
        decision = compare_receipt(candidate, [fingerprint()])
        self.assertEqual(decision.disposition,
                         IdentityDisposition.SAME_BUSINESS_NEW_EVENT)

    def test_same_business_identity_with_changed_content_is_conflict(self):
        other_event = "95dc2fc6-f48b-49d3-8a87-d6e5e695ac91"
        candidate = fingerprint(event_id=other_event, topic="return.completed",
                                offset=12, wire="4", event="5", business="6")
        decision = compare_receipt(candidate, [fingerprint()])
        self.assertEqual(decision.disposition, IdentityDisposition.CONFLICT)

    def test_same_identity_with_uncomparable_key_version_requires_review(self):
        candidate = fingerprint(key_version="k2")
        decision = compare_receipt(candidate, [fingerprint(key_version="k1")])
        self.assertEqual(decision.disposition, IdentityDisposition.REVIEW_REQUIRED)
        self.assertFalse(decision.preserve_first_receipt)


class ReceiptStateMachineTests(unittest.TestCase):
    def test_normal_processing_and_retry_transitions(self):
        self.assertEqual(transition_status(ReceiptStatus.RECEIVED,
                                           ReceiptStatus.PROCESSING), ReceiptStatus.PROCESSING)
        self.assertEqual(transition_status(ReceiptStatus.PROCESSING,
                                           ReceiptStatus.PROCESSED), ReceiptStatus.PROCESSED)
        self.assertEqual(transition_status(ReceiptStatus.PROCESSING,
                                           ReceiptStatus.RETRYABLE), ReceiptStatus.RETRYABLE)
        self.assertEqual(transition_status(ReceiptStatus.RETRYABLE,
                                           ReceiptStatus.PROCESSING), ReceiptStatus.PROCESSING)

    def test_diagnostic_conflict_and_quarantine_are_valid_terminal_paths(self):
        self.assertEqual(transition_status(ReceiptStatus.RECEIVED,
                                           ReceiptStatus.DIAGNOSTIC_ONLY),
                         ReceiptStatus.DIAGNOSTIC_ONLY)
        self.assertEqual(transition_status(ReceiptStatus.RECEIVED,
                                           ReceiptStatus.QUARANTINED), ReceiptStatus.QUARANTINED)
        self.assertEqual(transition_status(ReceiptStatus.PROCESSING,
                                           ReceiptStatus.CONFLICT), ReceiptStatus.CONFLICT)

    def test_invalid_transition_and_terminal_reprocessing_are_rejected(self):
        for current, target in ((ReceiptStatus.RECEIVED, ReceiptStatus.PROCESSED),
                                (ReceiptStatus.PROCESSED, ReceiptStatus.PROCESSING),
                                (ReceiptStatus.DIAGNOSTIC_ONLY, ReceiptStatus.PROCESSING),
                                (ReceiptStatus.CONFLICT, ReceiptStatus.RECEIVED)):
            with self.subTest(current=current, target=target):
                with self.assertRaisesRegex(ReceiptPolicyError, "INVALID_RECEIPT_STATE"):
                    transition_status(current, target)

    def test_quarantine_replay_requires_explicit_authority(self):
        with self.assertRaisesRegex(ReceiptPolicyError, "REPLAY_NOT_AUTHORIZED"):
            transition_status(ReceiptStatus.QUARANTINED, ReceiptStatus.RECEIVED)
        self.assertEqual(transition_status(ReceiptStatus.QUARANTINED,
                                           ReceiptStatus.RECEIVED,
                                           replay_authorized=True), ReceiptStatus.RECEIVED)


class AckPolicyTests(unittest.TestCase):
    def test_valid_purchase_and_preference_return_ack_only_after_durable_commit(self):
        for scenario in (AckScenario.VALID_PURCHASE, AckScenario.PREFERENCE_RETURN):
            decision = decide_ack(scenario, AckEvidence(
                status=ReceiptStatus.PROCESSED, durable_evidence=True,
                business_effects_committed=True))
            self.assertTrue(decision.acknowledge)
            self.assertTrue(decision.durable_evidence_required)
        for evidence in (AckEvidence(status=ReceiptStatus.PROCESSED,
                                     business_effects_committed=True),
                         AckEvidence(status=ReceiptStatus.PROCESSED,
                                     durable_evidence=True)):
            self.assertFalse(decide_ack(AckScenario.VALID_PURCHASE, evidence).acknowledge)

    def test_operational_return_needs_durable_diagnostic_disposition(self):
        self.assertFalse(decide_ack(AckScenario.OPERATIONAL_ONLY_RETURN,
                                    AckEvidence(status=ReceiptStatus.DIAGNOSTIC_ONLY)).acknowledge)
        self.assertTrue(decide_ack(AckScenario.OPERATIONAL_ONLY_RETURN,
                                   AckEvidence(status=ReceiptStatus.DIAGNOSTIC_ONLY,
                                               durable_evidence=True)).acknowledge)

    def test_duplicate_ack_requires_verified_prior_terminal_durable_receipt(self):
        good = AckEvidence(duplicate_verified=True,
                           existing_status=ReceiptStatus.PROCESSED,
                           existing_evidence_durable=True)
        self.assertTrue(decide_ack(AckScenario.DUPLICATE, good).acknowledge)
        for evidence in (AckEvidence(duplicate_verified=True),
                         replace(good, existing_evidence_durable=False),
                         replace(good, existing_status=ReceiptStatus.RETRYABLE)):
            self.assertFalse(decide_ack(AckScenario.DUPLICATE, evidence).acknowledge)

    def test_conflict_and_poison_ack_fail_closed_without_explicit_authority(self):
        conflict = AckEvidence(status=ReceiptStatus.CONFLICT, durable_evidence=True)
        poison = AckEvidence(status=ReceiptStatus.QUARANTINED, durable_evidence=True)
        self.assertFalse(decide_ack(AckScenario.CONFLICTING_EVENT, conflict).acknowledge)
        self.assertFalse(decide_ack(AckScenario.INVALID_SCHEMA, poison).acknowledge)
        self.assertFalse(decide_ack(AckScenario.POISON_EVENT, poison,
                                    policy=AckPolicyConfig(True, None)).acknowledge)
        self.assertFalse(decide_ack(AckScenario.CONFLICTING_EVENT, conflict,
                                    policy=AckPolicyConfig(None, True)).acknowledge)

    def test_conflict_and_poison_ack_need_durable_evidence_even_if_authorized(self):
        approved = AckPolicyConfig(conflict_ack_authorized=True,
                                   poison_ack_authorized=True)
        self.assertFalse(decide_ack(AckScenario.CONFLICTING_EVENT,
                                    AckEvidence(status=ReceiptStatus.CONFLICT),
                                    policy=approved).acknowledge)
        self.assertFalse(decide_ack(AckScenario.POISON_EVENT,
                                    AckEvidence(status=ReceiptStatus.QUARANTINED),
                                    policy=approved).acknowledge)
        self.assertTrue(decide_ack(AckScenario.CONFLICTING_EVENT,
                                   AckEvidence(status=ReceiptStatus.CONFLICT,
                                               durable_evidence=True),
                                   policy=approved).acknowledge)
        self.assertTrue(decide_ack(AckScenario.INVALID_SCHEMA,
                                   AckEvidence(status=ReceiptStatus.QUARANTINED,
                                               durable_evidence=True),
                                   policy=approved).acknowledge)

    def test_missing_hmac_key_never_acks_even_with_poison_policy(self):
        decision = decide_ack(AckScenario.MISSING_HMAC_KEY,
                              AckEvidence(status=ReceiptStatus.QUARANTINED,
                                          durable_evidence=True),
                              policy=AckPolicyConfig(True, True))
        self.assertFalse(decision.acknowledge)
        self.assertEqual(decision.reason, "HMAC_EVIDENCE_UNVERIFIABLE")

    def test_processing_database_and_rollback_failures_are_retryable_and_never_acked(self):
        for scenario in (AckScenario.PROCESSING_FAILURE, AckScenario.DATABASE_UNAVAILABLE,
                         AckScenario.TRANSACTION_ROLLBACK):
            decision = decide_ack(scenario,
                                  AckEvidence(status=ReceiptStatus.RETRYABLE))
            self.assertFalse(decision.acknowledge)
            self.assertTrue(decision.retryable)

    def test_failure_is_not_retryable_if_effect_state_is_unknown_or_already_committed(self):
        unknown = decide_ack(AckScenario.PROCESSING_FAILURE,
                             AckEvidence(status=ReceiptStatus.PROCESSING))
        committed = decide_ack(AckScenario.TRANSACTION_ROLLBACK,
                               AckEvidence(status=ReceiptStatus.RETRYABLE,
                                           business_effects_committed=True))
        self.assertFalse(unknown.retryable)
        self.assertFalse(committed.retryable)
        self.assertEqual(committed.reason,
                         "EFFECTS_COMMITTED_RECONCILIATION_REQUIRED")

    def test_replayability_requires_durable_source_and_operator_authority(self):
        source = AckEvidence(status=ReceiptStatus.QUARANTINED,
                             replay_source_durable=True)
        decision = decide_ack(AckScenario.POISON_EVENT, source)
        self.assertFalse(decision.replayable)
        authorized = replace(source, operator_replay_authorized=True)
        self.assertTrue(decide_ack(AckScenario.POISON_EVENT, authorized).replayable)


if __name__ == "__main__":
    unittest.main()
