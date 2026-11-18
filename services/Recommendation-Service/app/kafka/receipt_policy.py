"""Offline, fail-closed policy for future durable Kafka receipts.

This module is deliberately not imported by the Kafka consumers. It defines
pure decisions only; it does not persist receipts, ACK Kafka records, or claim
exactly-once processing. Production limits and ACK dispositions must be passed
explicitly by a later reviewed contract.
"""
from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field
from enum import Enum
from hmac import compare_digest
import re
from uuid import UUID

from app.kafka.payload_decoder import KafkaPayloadDecodeError, decode_kafka_payload
from app.kafka.receipt_canonical import Digest, ReceiptPolicyError, policy_name
from app.kafka.receipt_hashes import (
    BUSINESS_POLICY,
    CANONICAL_POLICY,
    WIRE_POLICY,
    EventIdentity,
    BusinessClaim,
    event_identity,
    event_integrity,
    approved_payload_hash,
    wire_fingerprint,
)
from app.kafka.receipt_projection import ApprovedReceipt

_HEX_SHA256 = re.compile(r"[0-9a-f]{64}")
_SUPPORTED_EVENT_TYPES = frozenset({"ORDER_COMPLETED", "RETURN_COMPLETED"})


@dataclass(frozen=True)
class PayloadPolicyConfig:
    """Caller-supplied constraints; ``None`` values are intentionally invalid."""

    max_payload_bytes: int | None
    accepted_policy_versions: frozenset[str] | None
    supported_event_versions: frozenset[int] | None
    supported_event_types: frozenset[str] | None
    require_explicit_event_metadata: bool | None


@dataclass(frozen=True)
class ValidatedPayload:
    byte_length: int
    identity: EventIdentity
    policy_version: str


def _validate_payload_config(config: PayloadPolicyConfig) -> None:
    if not isinstance(config, PayloadPolicyConfig):
        raise ReceiptPolicyError("MISSING_PAYLOAD_POLICY_CONFIG")
    if type(config.max_payload_bytes) is not int or config.max_payload_bytes <= 0:
        raise ReceiptPolicyError("MISSING_OR_INVALID_MAX_PAYLOAD_BYTES")
    if not isinstance(config.accepted_policy_versions, (set, frozenset)) \
            or not config.accepted_policy_versions:
        raise ReceiptPolicyError("MISSING_SUPPORTED_POLICY_VERSIONS")
    if not isinstance(config.supported_event_versions, (set, frozenset)) \
            or not config.supported_event_versions:
        raise ReceiptPolicyError("MISSING_SUPPORTED_EVENT_VERSIONS")
    if not isinstance(config.supported_event_types, (set, frozenset)) \
            or not config.supported_event_types:
        raise ReceiptPolicyError("MISSING_SUPPORTED_EVENT_TYPES")
    if type(config.require_explicit_event_metadata) is not bool:
        raise ReceiptPolicyError("MISSING_EXPLICIT_METADATA_POLICY")
    if any(type(version) is not int or version <= 0
           for version in config.supported_event_versions):
        raise ReceiptPolicyError("INVALID_SUPPORTED_EVENT_VERSIONS")
    if any(not isinstance(event_type, str) for event_type in config.supported_event_types) \
            or not config.supported_event_types <= _SUPPORTED_EVENT_TYPES:
        raise ReceiptPolicyError("UNSUPPORTED_CONFIGURED_EVENT_TYPE")
    for version in config.accepted_policy_versions:
        policy_name(version)


def validate_payload(raw_value: bytes | str, *, namespace: str,
                     policy_version: str,
                     config: PayloadPolicyConfig) -> ValidatedPayload:
    """Validate size and the currently supported event identity contract.

    ``eventVersion`` here is the application event contract version, not a
    Kafka Connect schema-registry version. No production maximum is supplied.
    """
    _validate_payload_config(config)
    policy_name(namespace)
    policy_name(policy_version)
    if policy_version not in config.accepted_policy_versions:
        raise ReceiptPolicyError("UNSUPPORTED_POLICY_VERSION")
    if isinstance(raw_value, bytes):
        byte_length = len(raw_value)
    elif isinstance(raw_value, str):
        try:
            byte_length = len(raw_value.encode("utf-8", errors="strict"))
        except UnicodeEncodeError:
            raise ReceiptPolicyError("INVALID_PAYLOAD_ENCODING") from None
    else:
        raise ReceiptPolicyError("INVALID_PAYLOAD_TYPE")
    if byte_length > config.max_payload_bytes:
        raise ReceiptPolicyError("PAYLOAD_TOO_LARGE")
    try:
        body = decode_kafka_payload(raw_value)
    except KafkaPayloadDecodeError:
        raise ReceiptPolicyError("INVALID_PAYLOAD") from None

    event_type = body.get("eventType")
    if not isinstance(event_type, str) or event_type not in config.supported_event_types:
        raise ReceiptPolicyError("UNSUPPORTED_EVENT_TYPE")
    if config.require_explicit_event_metadata:
        required = ("eventId", "eventType", "eventVersion", "sourceService")
        if any(field_name not in body for field_name in required):
            raise ReceiptPolicyError("MISSING_REQUIRED_IDENTITY")
    version = body.get("eventVersion", 1)
    if type(version) is not int or version not in config.supported_event_versions:
        raise ReceiptPolicyError("UNSUPPORTED_SCHEMA_VERSION")
    try:
        identity = event_identity(raw_value, namespace=namespace)
    except (KafkaPayloadDecodeError, ReceiptPolicyError):
        # Keep malformed or missing identity details out of logs and responses.
        raise ReceiptPolicyError("MISSING_OR_INVALID_EVENT_IDENTITY") from None
    return ValidatedPayload(byte_length, identity, policy_version)


def validate_digest(value: Digest | str, *, expected_policy_version: str,
                    expected_key_version: str | None = None) -> str:
    """Validate digest syntax and its policy/key metadata before comparison."""
    policy_name(expected_policy_version)
    if isinstance(value, Digest):
        digest_value = value.value
        if value.policy_version != expected_policy_version:
            raise ReceiptPolicyError("DIGEST_POLICY_MISMATCH")
        if expected_key_version is not None and value.key_version != expected_key_version:
            raise ReceiptPolicyError("DIGEST_KEY_VERSION_MISMATCH")
    elif isinstance(value, str):
        digest_value = value
        if expected_key_version is not None:
            raise ReceiptPolicyError("DIGEST_KEY_VERSION_REQUIRED")
    else:
        raise ReceiptPolicyError("INVALID_DIGEST")
    if not _HEX_SHA256.fullmatch(digest_value):
        raise ReceiptPolicyError("INVALID_DIGEST")
    return digest_value


def verify_approved_payload(receipt: ApprovedReceipt, expected_digest: Digest | str) -> Digest:
    """Verify the SHA256 of the approved projection; never hashes raw as a claim."""
    actual = approved_payload_hash(receipt)
    expected = validate_digest(expected_digest,
                               expected_policy_version=actual.policy_version)
    if not compare_digest(actual.value, expected):
        raise ReceiptPolicyError("APPROVED_PAYLOAD_DIGEST_MISMATCH")
    return actual


@dataclass(frozen=True)
class KeyReference:
    key_id: str
    version: str


@dataclass(frozen=True)
class HmacKeyRing:
    """Explicit current/previous key metadata and secret material.

    The secret mapping is hidden from repr. An empty ``previous`` tuple means
    the operator explicitly configured no prior keys; ``None`` is missing
    rotation policy. Key versions must be unique because existing hash helpers
    domain-separate by version but do not take a key ID parameter.
    """

    current: KeyReference | None
    previous: tuple[KeyReference, ...] | None
    secrets: Mapping[KeyReference, bytes] = field(repr=False)


class HmacPurpose(str, Enum):
    WIRE = "WIRE"
    EVENT = "EVENT"


def _resolve_key(keyring: HmacKeyRing | None, key_id: str,
                 key_version: str) -> bytes:
    if not isinstance(keyring, HmacKeyRing):
        raise ReceiptPolicyError("MISSING_HMAC_KEY_POLICY")
    if keyring.current is None:
        raise ReceiptPolicyError("MISSING_CURRENT_KEY_POLICY")
    if keyring.previous is None:
        raise ReceiptPolicyError("MISSING_PREVIOUS_KEY_POLICY")
    if not isinstance(keyring.previous, tuple) or not isinstance(keyring.secrets, Mapping):
        raise ReceiptPolicyError("INVALID_HMAC_KEY_POLICY")
    if not isinstance(keyring.current, KeyReference) \
            or any(not isinstance(ref, KeyReference) for ref in keyring.previous):
        raise ReceiptPolicyError("INVALID_HMAC_KEY_POLICY")
    policy_name(key_id)
    policy_name(key_version)
    refs = (keyring.current,) + keyring.previous
    if len(set(refs)) != len(refs):
        raise ReceiptPolicyError("DUPLICATE_KEY_REFERENCE")
    if len({ref.version for ref in refs}) != len(refs):
        raise ReceiptPolicyError("AMBIGUOUS_KEY_VERSION")
    for ref in refs:
        policy_name(ref.key_id)
        policy_name(ref.version)
    current_secret = keyring.secrets.get(keyring.current)
    if current_secret is None:
        raise ReceiptPolicyError("MISSING_CURRENT_KEY")
    if not isinstance(current_secret, bytes) or len(current_secret) < 16:
        raise ReceiptPolicyError("INVALID_HMAC_KEY")
    matching_id = tuple(ref for ref in refs if ref.key_id == key_id)
    if not matching_id:
        raise ReceiptPolicyError("UNKNOWN_KEY_ID")
    selected = next((ref for ref in matching_id if ref.version == key_version), None)
    if selected is None:
        raise ReceiptPolicyError("UNKNOWN_KEY_VERSION")
    secret = keyring.secrets.get(selected)
    if secret is None:
        code = "MISSING_CURRENT_KEY" if selected == keyring.current else "MISSING_OLD_KEY"
        raise ReceiptPolicyError(code)
    if not isinstance(secret, bytes) or len(secret) < 16:
        raise ReceiptPolicyError("INVALID_HMAC_KEY")
    return secret


def verify_hmac(purpose: HmacPurpose, raw_value: bytes | str, signature: str, *,
                key_id: str, key_version: str, keyring: HmacKeyRing | None,
                record_key: bytes | str | None = None, headers=None) -> Digest:
    """Verify a wire or event HMAC using only an explicitly selected key."""
    try:
        purpose = HmacPurpose(purpose)
    except (TypeError, ValueError):
        raise ReceiptPolicyError("UNSUPPORTED_HMAC_PURPOSE") from None
    if not isinstance(signature, str) or not _HEX_SHA256.fullmatch(signature):
        raise ReceiptPolicyError("INVALID_SIGNATURE")
    secret = _resolve_key(keyring, key_id, key_version)
    keys = {key_version: secret}
    try:
        if purpose is HmacPurpose.WIRE:
            actual = wire_fingerprint(raw_value, record_key=record_key,
                                      headers=headers, keys=keys,
                                      key_version=key_version)
        else:
            actual = event_integrity(raw_value, headers=headers, keys=keys,
                                     key_version=key_version)
    except (KafkaPayloadDecodeError, TypeError, ValueError):
        raise ReceiptPolicyError("INVALID_HMAC_INPUT") from None
    if not compare_digest(actual.value, signature):
        raise ReceiptPolicyError("INVALID_SIGNATURE")
    return actual


@dataclass(frozen=True)
class TransportIdentity:
    """Physical log location, distinct from event and business identity."""

    namespace: str
    topic: str
    partition: int
    offset: int

    @property
    def key(self) -> tuple[str, str, int, int]:
        return self.namespace, self.topic, self.partition, self.offset


@dataclass(frozen=True)
class ReceiptFingerprint:
    """In-memory comparison input; this is not a persisted receipt row."""

    transport: TransportIdentity
    wire_key_id: str
    wire_digest: Digest
    event: EventIdentity
    event_key_id: str
    event_digest: Digest
    business_claims: tuple[BusinessClaim, ...]


class IdentityDisposition(str, Enum):
    NEW = "NEW"
    SAME_TRANSPORT_SAME_CONTENT = "SAME_TRANSPORT_SAME_CONTENT"
    SAME_EVENT_SAME_CONTENT = "SAME_EVENT_SAME_CONTENT"
    SAME_BUSINESS_NEW_EVENT = "SAME_BUSINESS_NEW_EVENT"
    CONFLICT = "CONFLICT"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"


@dataclass(frozen=True)
class IdentityDecision:
    disposition: IdentityDisposition
    preserve_first_receipt: bool


def _identity_content_match(old: Digest, new: Digest, old_key_id: str,
                            new_key_id: str) -> bool | None:
    old_value = validate_digest(old, expected_policy_version=old.policy_version,
                                expected_key_version=old.key_version)
    new_value = validate_digest(new, expected_policy_version=new.policy_version,
                                expected_key_version=new.key_version)
    if (old.policy_version != new.policy_version
            or old.key_version != new.key_version or old_key_id != new_key_id):
        return None
    return compare_digest(old_value, new_value)


def compare_receipt(candidate: ReceiptFingerprint,
                    existing: Iterable[ReceiptFingerprint]) -> IdentityDecision:
    """Classify collisions without mutating or replacing any prior evidence."""
    if not isinstance(candidate, ReceiptFingerprint):
        raise ReceiptPolicyError("INVALID_RECEIPT_FINGERPRINT")
    _validate_fingerprint(candidate)
    business_by_key = {}
    for claim in candidate.business_claims:
        _validate_business_claim(claim)
        if claim.key in business_by_key:
            raise ReceiptPolicyError("DUPLICATE_BUSINESS_IDENTITY")
        business_by_key[claim.key] = claim

    matches: set[IdentityDisposition] = set()
    inconclusive = False
    for prior in existing:
        if not isinstance(prior, ReceiptFingerprint):
            raise ReceiptPolicyError("INVALID_PRIOR_RECEIPT_FINGERPRINT")
        _validate_fingerprint(prior)
        if prior.transport.key == candidate.transport.key:
            match = _identity_content_match(prior.wire_digest, candidate.wire_digest,
                                            prior.wire_key_id, candidate.wire_key_id)
            if match is False:
                return IdentityDecision(IdentityDisposition.CONFLICT, True)
            if match is None:
                inconclusive = True
            else:
                matches.add(IdentityDisposition.SAME_TRANSPORT_SAME_CONTENT)
        if prior.event.key == candidate.event.key:
            match = _identity_content_match(prior.event_digest, candidate.event_digest,
                                            prior.event_key_id, candidate.event_key_id)
            if match is False:
                return IdentityDecision(IdentityDisposition.CONFLICT, True)
            if match is None:
                inconclusive = True
            else:
                matches.add(IdentityDisposition.SAME_EVENT_SAME_CONTENT)
        prior_business = {claim.key: claim for claim in prior.business_claims}
        for key in business_by_key.keys() & prior_business.keys():
            old_claim = prior_business[key]
            new_claim = business_by_key[key]
            if old_claim.policy_version != new_claim.policy_version:
                inconclusive = True
            elif not _HEX_SHA256.fullmatch(old_claim.content_hash) \
                    or not _HEX_SHA256.fullmatch(new_claim.content_hash):
                raise ReceiptPolicyError("INVALID_BUSINESS_CONTENT_HASH")
            elif not compare_digest(old_claim.content_hash, new_claim.content_hash):
                return IdentityDecision(IdentityDisposition.CONFLICT, True)
            else:
                matches.add(IdentityDisposition.SAME_BUSINESS_NEW_EVENT)
    if inconclusive:
        return IdentityDecision(IdentityDisposition.REVIEW_REQUIRED, False)
    for disposition in (IdentityDisposition.SAME_TRANSPORT_SAME_CONTENT,
                        IdentityDisposition.SAME_EVENT_SAME_CONTENT,
                        IdentityDisposition.SAME_BUSINESS_NEW_EVENT):
        if disposition in matches:
            return IdentityDecision(disposition, False)
    return IdentityDecision(IdentityDisposition.NEW, False)


def _validate_business_claim(claim: BusinessClaim) -> None:
    if not isinstance(claim, BusinessClaim):
        raise ReceiptPolicyError("INVALID_BUSINESS_CLAIM")
    policy_name(claim.namespace)
    if claim.source != "order-service" or claim.kind not in {
            "PURCHASE_LINE", "RETURN_LINE", "ORDER_COMPLETION", "RETURN_COMPLETION"}:
        raise ReceiptPolicyError("INVALID_BUSINESS_IDENTITY")
    try:
        if str(UUID(claim.identity)) != claim.identity:
            raise ValueError
    except (TypeError, ValueError):
        raise ReceiptPolicyError("INVALID_BUSINESS_IDENTITY") from None
    if not _HEX_SHA256.fullmatch(claim.content_hash):
        raise ReceiptPolicyError("INVALID_BUSINESS_CONTENT_HASH")
    if claim.policy_version != BUSINESS_POLICY:
        raise ReceiptPolicyError("UNSUPPORTED_BUSINESS_POLICY")


def _validate_fingerprint(fingerprint: ReceiptFingerprint) -> None:
    if not isinstance(fingerprint.transport, TransportIdentity) \
            or not isinstance(fingerprint.event, EventIdentity) \
            or not isinstance(fingerprint.wire_digest, Digest) \
            or not isinstance(fingerprint.event_digest, Digest) \
            or not isinstance(fingerprint.business_claims, tuple):
        raise ReceiptPolicyError("INVALID_RECEIPT_FINGERPRINT")
    policy_name(fingerprint.transport.namespace)
    if (not isinstance(fingerprint.transport.topic, str) or not fingerprint.transport.topic
            or type(fingerprint.transport.partition) is not int
            or fingerprint.transport.partition < 0 or type(fingerprint.transport.offset) is not int
            or fingerprint.transport.offset < 0):
        raise ReceiptPolicyError("INVALID_TRANSPORT_IDENTITY")
    if (fingerprint.event.namespace != fingerprint.transport.namespace
            or fingerprint.event.source != "order-service"
            or not isinstance(fingerprint.event.event_type, str)
            or fingerprint.event.event_type not in _SUPPORTED_EVENT_TYPES
            or type(fingerprint.event.version) is not int or fingerprint.event.version != 1):
        raise ReceiptPolicyError("INVALID_EVENT_IDENTITY")
    try:
        if str(UUID(fingerprint.event.event_id)) != fingerprint.event.event_id:
            raise ValueError
    except (TypeError, ValueError):
        raise ReceiptPolicyError("INVALID_EVENT_IDENTITY") from None
    policy_name(fingerprint.wire_key_id)
    policy_name(fingerprint.event_key_id)
    if (fingerprint.wire_digest.policy_version != WIRE_POLICY
            or not fingerprint.wire_digest.key_version):
        raise ReceiptPolicyError("UNSUPPORTED_WIRE_DIGEST_POLICY")
    if (fingerprint.event_digest.policy_version != CANONICAL_POLICY
            or not fingerprint.event_digest.key_version):
        raise ReceiptPolicyError("UNSUPPORTED_EVENT_DIGEST_POLICY")
    validate_digest(fingerprint.wire_digest,
                    expected_policy_version=fingerprint.wire_digest.policy_version,
                    expected_key_version=fingerprint.wire_digest.key_version)
    validate_digest(fingerprint.event_digest,
                    expected_policy_version=fingerprint.event_digest.policy_version,
                    expected_key_version=fingerprint.event_digest.key_version)
    seen_business_keys = set()
    for claim in fingerprint.business_claims:
        _validate_business_claim(claim)
        if claim.namespace != fingerprint.event.namespace or claim.key in seen_business_keys:
            raise ReceiptPolicyError("DUPLICATE_OR_MISMATCHED_BUSINESS_IDENTITY")
        seen_business_keys.add(claim.key)


class ReceiptStatus(str, Enum):
    RECEIVED = "RECEIVED"
    PROCESSING = "PROCESSING"
    PROCESSED = "PROCESSED"
    DIAGNOSTIC_ONLY = "DIAGNOSTIC_ONLY"
    RETRYABLE = "RETRYABLE"
    QUARANTINED = "QUARANTINED"
    CONFLICT = "CONFLICT"


_TRANSITIONS = {
    ReceiptStatus.RECEIVED: frozenset({ReceiptStatus.PROCESSING,
                                       ReceiptStatus.DIAGNOSTIC_ONLY,
                                       ReceiptStatus.QUARANTINED,
                                       ReceiptStatus.CONFLICT}),
    ReceiptStatus.PROCESSING: frozenset({ReceiptStatus.PROCESSED,
                                         ReceiptStatus.RETRYABLE,
                                         ReceiptStatus.QUARANTINED,
                                         ReceiptStatus.CONFLICT}),
    ReceiptStatus.RETRYABLE: frozenset({ReceiptStatus.PROCESSING,
                                        ReceiptStatus.QUARANTINED,
                                        ReceiptStatus.CONFLICT}),
    ReceiptStatus.QUARANTINED: frozenset(),
    ReceiptStatus.PROCESSED: frozenset(),
    ReceiptStatus.DIAGNOSTIC_ONLY: frozenset(),
    ReceiptStatus.CONFLICT: frozenset(),
}


def transition_status(current: ReceiptStatus, target: ReceiptStatus, *,
                      replay_authorized: bool = False) -> ReceiptStatus:
    """Pure state transition; quarantine replay requires explicit authority."""
    try:
        current = ReceiptStatus(current)
        target = ReceiptStatus(target)
    except (TypeError, ValueError):
        raise ReceiptPolicyError("UNKNOWN_RECEIPT_STATUS") from None
    if current is ReceiptStatus.QUARANTINED and target is ReceiptStatus.RECEIVED:
        if replay_authorized:
            return target
        raise ReceiptPolicyError("QUARANTINE_REPLAY_NOT_AUTHORIZED")
    if target not in _TRANSITIONS[current]:
        raise ReceiptPolicyError("INVALID_RECEIPT_STATE_TRANSITION")
    return target


class AckScenario(str, Enum):
    VALID_PURCHASE = "VALID_PURCHASE"
    PREFERENCE_RETURN = "PREFERENCE_RETURN"
    OPERATIONAL_ONLY_RETURN = "OPERATIONAL_ONLY_RETURN"
    DUPLICATE = "DUPLICATE"
    CONFLICTING_EVENT = "CONFLICTING_EVENT"
    INVALID_SCHEMA = "INVALID_SCHEMA"
    MISSING_HMAC_KEY = "MISSING_HMAC_KEY"
    PROCESSING_FAILURE = "PROCESSING_FAILURE"
    DATABASE_UNAVAILABLE = "DATABASE_UNAVAILABLE"
    TRANSACTION_ROLLBACK = "TRANSACTION_ROLLBACK"
    POISON_EVENT = "POISON_EVENT"


@dataclass(frozen=True)
class AckPolicyConfig:
    """Unapproved dispositions remain ``None`` and therefore fail closed."""

    conflict_ack_authorized: bool | None
    poison_ack_authorized: bool | None


@dataclass(frozen=True)
class AckEvidence:
    status: ReceiptStatus | None = None
    durable_evidence: bool = False
    business_effects_committed: bool = False
    duplicate_verified: bool = False
    existing_status: ReceiptStatus | None = None
    existing_evidence_durable: bool = False
    replay_source_durable: bool = False
    operator_replay_authorized: bool = False


@dataclass(frozen=True)
class AckDecision:
    acknowledge: bool
    reason: str
    retryable: bool
    replayable: bool
    durable_evidence_required: bool


def decide_ack(scenario: AckScenario, evidence: AckEvidence, *,
               policy: AckPolicyConfig | None = None) -> AckDecision:
    """Decide only ACK eligibility; does not commit Kafka offsets or imply EOS."""
    try:
        scenario = AckScenario(scenario)
    except (TypeError, ValueError):
        raise ReceiptPolicyError("UNKNOWN_ACK_SCENARIO") from None
    if not isinstance(evidence, AckEvidence):
        raise ReceiptPolicyError("MISSING_ACK_EVIDENCE")
    boolean_fields = (evidence.durable_evidence, evidence.business_effects_committed,
                      evidence.duplicate_verified, evidence.existing_evidence_durable,
                      evidence.replay_source_durable, evidence.operator_replay_authorized)
    if any(type(value) is not bool for value in boolean_fields):
        raise ReceiptPolicyError("INVALID_ACK_EVIDENCE")
    for status in (evidence.status, evidence.existing_status):
        if status is not None and not isinstance(status, ReceiptStatus):
            raise ReceiptPolicyError("INVALID_ACK_STATE")
    replayable = evidence.replay_source_durable and evidence.operator_replay_authorized

    if scenario in (AckScenario.VALID_PURCHASE, AckScenario.PREFERENCE_RETURN):
        if evidence.status is not ReceiptStatus.PROCESSED:
            reason = "PROCESSING_NOT_COMPLETE"
        elif not evidence.business_effects_committed:
            reason = "BUSINESS_EFFECTS_NOT_COMMITTED"
        elif not evidence.durable_evidence:
            reason = "DURABLE_RECEIPT_REQUIRED"
        else:
            return AckDecision(True, "PROCESSED_AND_DURABLE", False, False, True)
        retryable = (evidence.status is ReceiptStatus.RETRYABLE
                     and not evidence.business_effects_committed)
        return AckDecision(False, reason, retryable, replayable, True)

    if scenario is AckScenario.OPERATIONAL_ONLY_RETURN:
        if evidence.status is not ReceiptStatus.DIAGNOSTIC_ONLY:
            reason = "DIAGNOSTIC_DISPOSITION_REQUIRED"
        elif not evidence.durable_evidence:
            reason = "DURABLE_DIAGNOSTIC_REQUIRED"
        else:
            return AckDecision(True, "DIAGNOSTIC_DISPOSITION_DURABLE", False, False, True)
        return AckDecision(False, reason, True, replayable, True)

    if scenario is AckScenario.DUPLICATE:
        if not evidence.duplicate_verified:
            reason = "DUPLICATE_NOT_VERIFIED"
        elif evidence.existing_status not in (ReceiptStatus.PROCESSED,
                                               ReceiptStatus.DIAGNOSTIC_ONLY):
            reason = "PRIOR_RECEIPT_NOT_TERMINAL"
        elif not evidence.existing_evidence_durable:
            reason = "PRIOR_DURABLE_EVIDENCE_REQUIRED"
        else:
            return AckDecision(True, "DUPLICATE_OF_DURABLE_TERMINAL_RECEIPT",
                               False, False, True)
        return AckDecision(False, reason, False, replayable, True)

    if scenario is AckScenario.CONFLICTING_EVENT:
        if evidence.status is not ReceiptStatus.CONFLICT or not evidence.durable_evidence:
            return AckDecision(False, "DURABLE_CONFLICT_EVIDENCE_REQUIRED",
                               False, replayable, True)
        if policy is None or policy.conflict_ack_authorized is not True:
            return AckDecision(False, "CONFLICT_ACK_POLICY_NOT_AUTHORIZED",
                               False, replayable, True)
        return AckDecision(True, "DURABLE_CONFLICT_ACK_AUTHORIZED", False,
                           False, True)

    if scenario is AckScenario.MISSING_HMAC_KEY:
        return AckDecision(False, "HMAC_EVIDENCE_UNVERIFIABLE", False,
                           replayable, True)

    if scenario in (AckScenario.INVALID_SCHEMA, AckScenario.POISON_EVENT):
        if evidence.status is not ReceiptStatus.QUARANTINED or not evidence.durable_evidence:
            return AckDecision(False, "DURABLE_QUARANTINE_REQUIRED", False,
                               replayable, True)
        if policy is None or policy.poison_ack_authorized is not True:
            return AckDecision(False, "POISON_ACK_POLICY_NOT_AUTHORIZED", False,
                               replayable, True)
        return AckDecision(True, "DURABLE_POISON_ACK_AUTHORIZED", False,
                           False, True)

    # Transient failures deliberately leave the physical offset uncommitted.
    if evidence.status is not ReceiptStatus.RETRYABLE:
        return AckDecision(False, "RETRYABLE_FAILURE_STATE_REQUIRED", False,
                           replayable, True)
    if evidence.business_effects_committed:
        return AckDecision(False, "EFFECTS_COMMITTED_RECONCILIATION_REQUIRED", False,
                           replayable, True)
    reason = {
        AckScenario.PROCESSING_FAILURE: "PROCESSING_FAILURE_RETRY",
        AckScenario.DATABASE_UNAVAILABLE: "DATABASE_UNAVAILABLE_RETRY",
        AckScenario.TRANSACTION_ROLLBACK: "TRANSACTION_ROLLBACK_RETRY",
    }[scenario]
    return AckDecision(False, reason, True, replayable, True)

