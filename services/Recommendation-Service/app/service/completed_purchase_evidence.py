"""Pure, transport-neutral evidence for the dry-run importer.

The existing Kafka recorder is deliberately unchanged. Its fingerprint, quantity
format and line ordering are reused here and checked by parity fixtures.
"""
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from uuid import UUID

from app.service.evidence_fingerprint import fingerprint


def require_identity(value):
    """Offline source keys must be canonical UUIDs; never manufacture a key."""
    try:
        if not isinstance(value, str) or str(UUID(value)) != value:
            raise ValueError
    except (ValueError, TypeError, AttributeError):
        raise ValueError("invalid_identity") from None
    return value


def normalized_quantity(value):
    try:
        quantity = Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        raise ValueError("invalid_quantity") from None
    if not quantity.is_finite() or quantity <= 0:
        raise ValueError("invalid_quantity")
    return format(quantity.normalize(), "f")


@dataclass(frozen=True, repr=False)
class PurchaseLine:
    order_item_id: str
    product_id: str
    product_variant_id: str
    quantity: str

    def as_dict(self):
        return dict(order_item_id=self.order_item_id, product_id=self.product_id,
                    product_variant_id=self.product_variant_id, quantity=self.quantity)


def canonical_lines(lines):
    if not lines:
        raise ValueError("empty_order")
    required = {"order_item_id", "product_id", "product_variant_id", "quantity"}
    if any(not isinstance(line, dict) or not required.issubset(line) for line in lines):
        raise ValueError("invalid_identity")
    result = tuple(sorted((PurchaseLine(
        require_identity(line["order_item_id"]), require_identity(line["product_id"]),
        require_identity(line["product_variant_id"]), normalized_quantity(line["quantity"]))
        for line in lines), key=lambda line: line.order_item_id))
    if len({line.order_item_id for line in result}) != len(result):
        raise ValueError("duplicate_line_id")
    return result


@dataclass(frozen=True, repr=False)
class PurchaseEvidence:
    order_id: str
    source_event_id: str
    customer_id: str | None
    occurred_at: datetime
    lines: tuple[PurchaseLine, ...]
    evidence_hash: str

    def semantic_value(self):
        return dict(customer_id=self.customer_id, occurred_at=self.occurred_at.isoformat(),
                    lines=[line.as_dict() for line in self.lines])


def build_completed_purchase(*, order_id, source_event_id, customer_id, status,
                             payment_status, occurred_at, lines):
    """Build evidence only: no session, clock, persistence or Kafka envelope.

    Mapping, provenance and source completeness belong to the offline adapter.
    Positive fractions remain valid here, as in ingestion; the offline workbook
    compatibility policy separately requires integer variant units.
    """
    if (status, payment_status) != ("COMPLETED", "PAID"):
        raise ValueError("ineligible_status")
    require_identity(order_id)
    require_identity(source_event_id)
    if customer_id is not None:
        require_identity(customer_id)
    if not isinstance(occurred_at, datetime) or occurred_at.tzinfo is None or occurred_at.utcoffset() is None:
        raise ValueError("timestamp_requires_timezone")
    occurred = occurred_at.astimezone(timezone.utc).replace(tzinfo=None)
    values = canonical_lines(lines)
    semantic = dict(customer_id=customer_id, occurred_at=occurred.isoformat(),
                    lines=[line.as_dict() for line in values])
    return PurchaseEvidence(order_id, source_event_id, customer_id, occurred, values, fingerprint(semantic))
