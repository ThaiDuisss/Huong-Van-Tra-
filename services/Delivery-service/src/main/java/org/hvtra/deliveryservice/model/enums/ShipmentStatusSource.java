package org.hvtra.deliveryservice.model.enums;

public enum ShipmentStatusSource {
    SYSTEM,
    ORDER_EVENT,
    COURIER,
    DISPATCHER,
    CARRIER_WEBHOOK,
    RECONCILIATION_JOB,
    CARRIER_API
}
