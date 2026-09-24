package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidOrderReturnException extends BaseException {

    public InvalidOrderReturnException(String message) {
        super("INVALID_ORDER_RETURN", message, HttpStatus.BAD_REQUEST);
    }
}
