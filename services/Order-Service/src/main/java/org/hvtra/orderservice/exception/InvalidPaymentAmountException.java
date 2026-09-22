package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidPaymentAmountException extends BaseException {

    public InvalidPaymentAmountException(String message) {
        super("INVALID_PAYMENT_AMOUNT", message, HttpStatus.BAD_REQUEST);
    }
}
