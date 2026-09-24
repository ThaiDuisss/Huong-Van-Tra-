from datetime import datetime, timezone
from decimal import Decimal
from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator

from app.models.interaction_event import InteractionEventType


class CanonicalInteraction(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source_event_id: str = Field(
        ...,
        validation_alias=AliasChoices(
            "source_event_id",
            "sourceEventId",
            "event_id",
            "eventId",
        ),
        description="Upstream event / message identifier",
    )
    order_item_id: str = Field(
        ...,
        validation_alias=AliasChoices("order_item_id", "orderItemId"),
        description="Order line item identifier",
    )
    return_item_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("return_item_id", "returnItemId"),
        description="Return line item identifier (for return interactions)",
    )
    customer_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("customer_id", "customerId"),
        description="Customer identifier (null for guest checkout)",
    )
    product_variant_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices(
            "product_variant_id",
            "productVariantId",
            "variant_id",
            "variantId",
        ),
        description="External product variant identifier",
    )
    sku: str | None = Field(
        default=None,
        validation_alias=AliasChoices("sku"),
        description="Product variant SKU",
    )
    product_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("product_id", "productId"),
        description="Pre-resolved local product identifier (optional)",
    )
    event_type: str = Field(
        ...,
        validation_alias=AliasChoices("event_type", "eventType"),
        description="Type of interaction: PURCHASE or RETURN",
    )
    quantity: Decimal = Field(
        default=Decimal("1.000"),
        validation_alias=AliasChoices("quantity"),
        description="Quantity of product transacted",
    )
    unit_price: Decimal | None = Field(
        default=None,
        validation_alias=AliasChoices("unit_price", "unitPrice"),
        description="Unit price at transaction time",
    )
    order_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("order_id", "orderId"),
        description="Order identifier",
    )
    return_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("return_id", "returnId"),
        description="Return identifier",
    )
    return_reason: str | None = Field(
        default=None,
        validation_alias=AliasChoices("return_reason", "returnReason"),
        description="Normalized return reason",
    )
    event_time: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        validation_alias=AliasChoices(
            "event_time",
            "eventTime",
            "occurred_at",
            "occurredAt",
        ),
        description="Timestamp when event occurred",
    )
    weight: Decimal | None = Field(
        default=None,
        validation_alias=AliasChoices("weight"),
        description="Optional pre-computed preference signal weight",
    )
    metadata: dict[str, Any] = Field(
        default_factory=dict,
        validation_alias=AliasChoices("metadata"),
        description="Arbitrary additional context metadata",
    )

    @field_validator("event_type")
    @classmethod
    def validate_event_type(cls, value: str) -> str:
        upper = value.strip().upper()
        valid = {InteractionEventType.PURCHASE.value, InteractionEventType.RETURN.value}
        if upper not in valid:
            raise ValueError(
                f"Invalid event_type '{value}'. Must be one of: {valid}"
            )
        return upper

    @field_validator("quantity")
    @classmethod
    def validate_quantity(cls, value: Decimal) -> Decimal:
        if value <= 0:
            raise ValueError(f"Quantity must be positive, got {value}")
        return value
