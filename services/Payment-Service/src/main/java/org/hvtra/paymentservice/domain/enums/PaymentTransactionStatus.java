package org.hvtra.paymentservice.domain.enums;

public enum PaymentTransactionStatus {
    RECEIVED,
    MATCHED,
    UNMATCHED,
    NEED_REVIEW,
    DUPLICATE,
    IGNORED,
    SUCCEEDED,
    FAILED
}
