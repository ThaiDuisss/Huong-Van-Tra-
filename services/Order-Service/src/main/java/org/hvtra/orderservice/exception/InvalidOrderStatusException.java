package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidOrderStatusException extends BaseException {
    public InvalidOrderStatusException(String message) {
        super("INVALID_ORDER_STATUS", message, HttpStatus.BAD_REQUEST);
    }
}
