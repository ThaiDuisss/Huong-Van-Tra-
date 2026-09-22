package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class OutOfStockException extends BaseException {
    public OutOfStockException(String message) {
        super("OUT_OF_STOCK", message, HttpStatus.CONFLICT);
    }
}
