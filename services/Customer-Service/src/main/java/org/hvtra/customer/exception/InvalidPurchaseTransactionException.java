package org.hvtra.customer.exception;

import org.hvtra.customer.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidPurchaseTransactionException extends CustomerServiceException {
    public InvalidPurchaseTransactionException(String message) {
        super(ErrorCode.CUSTOMER_INVALID_REQUEST, message, HttpStatus.BAD_REQUEST);
    }
}
