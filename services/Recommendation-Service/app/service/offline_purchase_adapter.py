"""Conservative eligibility and deterministic simulation; no persistence API."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone, tzinfo
from decimal import Decimal
import json
from uuid import UUID

from app.service.completed_purchase_evidence import (
    PurchaseEvidence, build_completed_purchase, canonical_lines, require_identity,
)
from app.service.evidence_fingerprint import fingerprint

DIAGNOSTIC_WARNING = "DIAGNOSTIC ONLY — UTC ASSUMPTION IS NOT VERIFIED — NO PRODUCTION IMPORT APPROVED"
CATEGORIES = ("READY", "NEEDS_REVIEW", "BLOCKED", "NOT_VERIFIED")


@dataclass(frozen=True)
class VerificationPolicy:
    """Evidence references supplied by a trusted caller, never CLI bypass flags.

    The CLI always uses the empty policy. No persistence is implemented even when
    isolated fixtures supply verified references to exercise the READY branch.
    """
    source_timezone: tzinfo | None = None
    timezone_evidence: str = ""
    provenance_evidence: str = ""
    unit_contract_evidence: str = ""
    usage_approval: str = ""

    def missing(self):
        reasons = []
        if self.source_timezone is None or not self.timezone_evidence:
            reasons.append("timezone_not_verified")
        if not self.provenance_evidence:
            reasons.append("provenance_not_verified")
        if not self.unit_contract_evidence:
            reasons.append("unit_contract_not_approved")
        if not self.usage_approval:
            reasons.append("usage_not_approved")
        return reasons


@dataclass(repr=False)
class OrderDecision:
    order_id: str
    category: str
    reasons: tuple[str, ...]
    line_count: int
    quantity: Decimal
    evidence: PurchaseEvidence | None = None
    action: str = "NOT_EVALUATED"


@dataclass(repr=False)
class HistoricalDryRun:
    decisions: list[OrderDecision]
    summary: dict


def _uuid(hex_value):
    try:
        return str(UUID(hex=hex_value))
    except (ValueError, TypeError, AttributeError):
        raise ValueError("invalid_source_uuid") from None


def _index(rows, key):
    result = {}
    for row in rows:
        value = row[key]
        if value in result:
            raise ValueError("duplicate_source_mapping")
        result[value] = row
    return result


def simulation_action(evidence, existing):
    """Compare identities as well as the existing semantic hash (which omits IDs)."""
    previous = existing.get(evidence.order_id)
    if previous:
        if previous["evidence_hash"] != evidence.evidence_hash:
            return "CONFLICT"
        if previous["source_event_id"] != evidence.source_event_id:
            return "IDENTITY_CONFLICT"
        return "SKIP"
    if isinstance(existing, _SimulationIndex):
        if existing.source_ids.get(evidence.source_event_id, set()) - {evidence.order_id}:
            return "REJECT_SOURCE_EVENT_COLLISION"
        if any(existing.line_ids.get(line.order_item_id, set()) - {evidence.order_id} for line in evidence.lines):
            return "REJECT_SOURCE_LINE_COLLISION"
        return "INSERT"
    for row in existing.values():
        if row["source_event_id"] == evidence.source_event_id:
            return "REJECT_SOURCE_EVENT_COLLISION"
        previous_lines = {line.get("order_item_id") for line in row.get("lines", [])}
        if previous_lines.intersection(line.order_item_id for line in evidence.lines):
            return "REJECT_SOURCE_LINE_COLLISION"
    return "INSERT"


class _SimulationIndex(dict):
    def __init__(self, rows):
        super().__init__()
        self.source_ids, self.line_ids = defaultdict(set), defaultdict(set)
        for row in rows:
            self.remember(row)

    def remember(self, row):
        self[row["id"]] = row
        self.source_ids[row["source_event_id"]].add(row["id"])
        for line in row.get("lines", []):
            self.line_ids[line.get("order_item_id")].add(row["id"])


def simulate_replay(evidences, target_rows):
    """An in-memory map only; no session, database argument or upsert path."""
    current = _SimulationIndex(target_rows)
    first, second = Counter(), Counter()
    for evidence in sorted(evidences, key=lambda e: e.order_id):
        action = simulation_action(evidence, current)
        first[action] += 1
        if action == "INSERT":
            current.remember(dict(id=evidence.order_id,
                source_event_id=evidence.source_event_id, evidence_hash=evidence.evidence_hash,
                lines=[line.as_dict() for line in evidence.lines]))
    for evidence in sorted(evidences, key=lambda e: e.order_id):
        second[simulation_action(evidence, current)] += 1
    return dict(first=dict(sorted(first.items())), rerun=dict(sorted(second.items())))


def analyze_historical_source(source, *, diagnostic_assume_utc=False, policy=None):
    policy = policy or VerificationPolicy()
    tables = source.tables
    headers = _index(tables["lfm_seed_ord"], "ord_no")
    customers = _index(tables["lfm_seed_cust"], "cust_no")
    variants = _index(tables["lfm_seed_variant"], "variant_no")
    products = _index(tables["products"], "id")
    catalog = _index(tables["product_variants"], "id")
    targets = _index(tables["report_purchase_orders"], "id")
    source_lines, events = defaultdict(list), defaultdict(list)
    event_owners, line_owners = defaultdict(set), defaultdict(set)
    for line in tables["lfm_seed_ord_line"]:
        source_lines[line["ord_no"]].append(line)
    purchases = [e for e in tables["interaction_events"] if e["event_type"] == "PURCHASE"]
    for event in purchases:
        events[event["order_id"]].append(event)
        event_owners[event["source_event_id"]].add(event["order_id"])
        line_owners[event["order_item_id"]].add(event["order_id"])
    for row in targets.values():
        event_owners[row["source_event_id"]].add(row["id"])
        for line in row["lines"]:
            line_owners[line.get("order_item_id")].add(row["id"])
    known_orders = set()
    for header in headers.values():
        try:
            known_orders.add(_uuid(header["id_hex"]))
        except ValueError:
            pass  # Classified as BLOCKED below, never substituted with a new UUID.
    orphan_orders = set(events) - known_orders
    orphan_lines = sum(len(lines) for no, lines in source_lines.items() if no not in headers)
    decisions, histogram = [], Counter()
    status_groups = defaultdict(lambda: dict(orders=0, lines=0, quantity=Decimal(0)))
    packs = defaultdict(lambda: dict(lines=0, quantity=Decimal(0)))
    for header in sorted(headers.values(), key=lambda h: h["ord_no"]):
        invalid_header_identity = False
        try:
            oid = _uuid(header["id_hex"])
        except ValueError:
            oid, invalid_header_identity = "invalid-header-" + str(header["ord_no"]), True
        lines, interactions = source_lines[header["ord_no"]], events[oid]
        try:
            quantity = sum((Decimal(str(line["qty"])) for line in lines), Decimal(0))
            if not quantity.is_finite():
                raise ValueError
        except (ValueError, ArithmeticError):
            quantity = Decimal(0)
        status = status_groups[header["status"] + "/" + header["payment_status"]]
        status["orders"] += 1
        status["lines"] += len(lines)
        status["quantity"] += quantity
        category, reasons, evidence = "NOT_VERIFIED", [], None
        if (header["status"], header["payment_status"]) != ("COMPLETED", "PAID"):
            category, reasons = "BLOCKED", ["ineligible_status"]
        else:
            histogram[(header["n_lines"], len(lines))] += 1
            for line in lines:
                pack = packs[variants.get(line["variant_no"], {}).get("pack_key", "UNKNOWN")]
                pack["lines"] += 1
                try:
                    amount = Decimal(str(line["qty"]))
                    if amount.is_finite():
                        pack["quantity"] += amount
                except ArithmeticError:
                    pass
            try:
                # Hard identity/collision failures outrank uncertain completeness.
                if invalid_header_identity:
                    raise ValueError("invalid_source_uuid")
                if any(len(event_owners[e["source_event_id"]]) > 1 for e in interactions):
                    raise ValueError("source_event_collision")
                if any(len(line_owners[e["order_item_id"]]) > 1 for e in interactions):
                    raise ValueError("source_line_collision")
                customer = None
                if header["cust_no"] is not None:
                    if header["cust_no"] not in customers:
                        raise ValueError("missing_customer_mapping")
                    customer = _uuid(customers[header["cust_no"]]["id_hex"])
                if not lines or len(lines) != len(interactions):
                    raise LookupError("partial_order")
                if len({line["variant_no"] for line in lines}) != len(lines):
                    raise LookupError("duplicate_source_variant")
                if len({e["product_variant_id"] for e in interactions}) != len(interactions):
                    raise LookupError("duplicate_interaction_variant")
                by_variant = {e["product_variant_id"]: e for e in interactions}
                source_ids = {e["source_event_id"] for e in interactions}
                if len(source_ids) != 1:
                    raise ValueError("multiple_source_events")
                source_id = require_identity(next(iter(source_ids)))
                raw_time = header["event_time"]
                if not isinstance(raw_time, datetime) or raw_time.tzinfo is not None:
                    raise ValueError("invalid_source_timestamp")
                canonical = []
                for line in lines:
                    variant = variants.get(line["variant_no"])
                    if variant is None:
                        raise ValueError("missing_variant_mapping")
                    vid, pid = _uuid(variant["id_hex"]), _uuid(variant["product_hex"])
                    if pid not in products or vid not in catalog or catalog[vid]["product_id"] != pid:
                        raise ValueError("canonical_mapping_conflict")
                    event = by_variant.get(vid)
                    if event is None:
                        raise ValueError("missing_interaction_variant")
                    if (event["order_id"], event["customer_id"], event["product_id"], event["event_time"]) != (oid, customer, pid, raw_time):
                        raise ValueError("source_evidence_conflict")
                    amount = Decimal(str(event["quantity"]))
                    if not amount.is_finite() or amount <= 0 or amount != amount.to_integral_value():
                        raise ValueError("invalid_integer_variant_units")
                    if amount != Decimal(str(line["qty"])):
                        raise ValueError("quantity_conflict")
                    canonical.append(dict(order_item_id=event["order_item_id"], product_id=pid,
                        product_variant_id=vid, quantity=amount))
                canonical_lines(canonical)  # Validate even when timezone is unknown.
                if not isinstance(header["n_lines"], int) or header["n_lines"] <= 0:
                    raise ValueError("invalid_declared_line_count")
                if header["n_lines"] != len(lines):
                    raise LookupError("declared_lines_mismatch")
                reasons = policy.missing()
                if orphan_orders or orphan_lines:
                    reasons.append("unmatched_source_rows")
                verified_zone = policy.source_timezone if "timezone_not_verified" not in reasons else None
                zone = verified_zone or (timezone.utc if diagnostic_assume_utc else None)
                if zone is not None:
                    evidence = build_completed_purchase(order_id=oid, source_event_id=source_id,
                        customer_id=customer, status=header["status"], payment_status=header["payment_status"],
                        occurred_at=raw_time.replace(tzinfo=zone), lines=canonical)
                    # Identity/hash conflicts still matter in diagnostic evidence;
                    # they must never be promoted to READY by an assumption.
                    action = simulation_action(evidence, targets)
                    if action not in ("INSERT", "SKIP"):
                        raise ValueError(action.lower())
                category = "NOT_VERIFIED" if reasons else "READY"
            except LookupError as exc:
                category, reasons, evidence = "NEEDS_REVIEW", [str(exc)], None
            except (ValueError, ArithmeticError) as error:
                # Only allow known, sanitized reason strings out of validation.
                reason = str(error) if isinstance(error, ValueError) else "invalid_quantity"
                allowed = {"source_event_collision", "source_line_collision", "missing_customer_mapping",
                    "missing_variant_mapping", "canonical_mapping_conflict", "missing_interaction_variant",
                    "source_evidence_conflict", "invalid_integer_variant_units", "quantity_conflict",
                    "invalid_source_timestamp", "invalid_declared_line_count", "invalid_source_uuid",
                    "invalid_identity", "duplicate_line_id", "multiple_source_events", "invalid_quantity",
                    "conflict", "identity_conflict", "reject_source_event_collision", "reject_source_line_collision"}
                category, reasons, evidence = "BLOCKED", [reason if reason in allowed else "invalid_source_evidence"], None
        action = simulation_action(evidence, targets) if evidence else "NOT_EVALUATED"
        decisions.append(OrderDecision(oid, category, tuple(reasons), len(lines), quantity, evidence, action))
    eligibility = {category: dict(orders=0, lines=0, quantity=Decimal(0)) for category in CATEGORIES}
    reason_counts = Counter()
    for decision in decisions:
        stats = eligibility[decision.category]
        stats["orders"] += 1
        stats["lines"] += decision.line_count
        stats["quantity"] += decision.quantity
        reason_counts.update(decision.reasons)
    diagnostic = [d.evidence for d in decisions if d.evidence and d.category in ("READY", "NOT_VERIFIED")]
    strict = [d.evidence for d in decisions if d.evidence and d.category == "READY"]
    # A single aggregate digest supports drift checks without exposing row IDs.
    source_digest = fingerprint({table: sorted(json.dumps(row, sort_keys=True, default=str)
        for row in rows) for table, rows in sorted(tables.items())})
    summary = dict(mode="DIAGNOSTIC" if diagnostic_assume_utc else "STRICT_DRY_RUN",
        warning=DIAGNOSTIC_WARNING if diagnostic_assume_utc else None,
        source_digest=source_digest, eligibility=eligibility, reasons=dict(sorted(reason_counts.items())),
        status_groups=dict(sorted(status_groups.items())), pack_units=dict(sorted(packs.items())),
        input_orders=len(headers), input_lines=sum(len(v) for v in source_lines.values()),
        purchase_lines=len(purchases), purchase_orders=len(events),
        customers=len({e["customer_id"] for e in purchases}), products=len({e["product_id"] for e in purchases}),
        variants=len({e["product_variant_id"] for e in purchases}),
        purchase_timestamps=len({e["event_time"] for e in purchases}),
        purchase_quantity=sum((Decimal(str(e["quantity"])) for e in purchases), Decimal(0)),
        orphan_purchase_orders=len(orphan_orders), orphan_staging_lines=orphan_lines,
        n_lines_histogram=[dict(declared=k[0], actual=k[1], orders=n) for k, n in sorted(histogram.items())],
        declared_line_difference=sum((a-b)*n for (a,b),n in histogram.items()),
        converted_payloads=len(diagnostic), diagnostic_replay=simulate_replay(diagnostic, list(targets.values())),
        strict_replay=simulate_replay(strict, list(targets.values())), approved_real_writes=0,
        target_source_event_unique_constraint=False)
    mismatch = [d for d in decisions if "declared_lines_mismatch" in d.reasons]
    summary["n_lines_mismatch"] = dict(orders=len(mismatch), lines=sum(d.line_count for d in mismatch),
        quantity=sum((d.quantity for d in mismatch), Decimal(0)))
    summary["timestamp_findings"] = dict(
        source_datetime_precision=next((c["datetime_precision"] for c in source.metadata.get("columns", [])
            if c["table_name"] == "lfm_seed_ord" and c["column_name"] == "event_time"), None),
        min_purchase_time=str(min((e["event_time"] for e in purchases), default=None)),
        max_purchase_time=str(max((e["event_time"] for e in purchases), default=None)),
        nonzero_purchase_microseconds=sum(isinstance(e["event_time"], datetime) and
            e["event_time"].microsecond != 0 for e in purchases),
        purchase_created_equals_event_time=sum(e.get("created_at") == e["event_time"] for e in purchases),
        source_timezone_verified=not bool("timezone_not_verified" in policy.missing()))
    summary["cross_order_collisions"] = dict(source_events=sum(len(v) > 1 for v in event_owners.values()),
                                            source_lines=sum(len(v) > 1 for v in line_owners.values()))
    summary["result_digest"] = fingerprint(json.loads(json.dumps(summary, default=str)))
    return HistoricalDryRun(decisions, summary)
