package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OrderAlreadyPaidException extends BaseException {

    public OrderAlreadyPaidException(String message) {
        super("ORDER_ALREADY_PAID", message, HttpStatus.CONFLICT);
    }
}
