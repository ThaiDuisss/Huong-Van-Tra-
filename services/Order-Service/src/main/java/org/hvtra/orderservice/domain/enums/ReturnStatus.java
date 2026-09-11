package org.hvtra.orderservice.domain.enums;

public enum ReturnStatus {
    CREATED,
    RECEIVED,
    // Reserved for the full return flow after QC/inventory handling and refund.
    COMPLETED,
    CANCELLED
}
