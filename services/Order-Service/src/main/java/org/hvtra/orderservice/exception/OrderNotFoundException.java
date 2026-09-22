package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends BaseException {

    public OrderNotFoundException(String message) {
        super("ORDER_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}