package org.hvtra.orderservice.exception;

import org.hvtra.commonservice.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InventoryReservationFailedException extends BaseException {
    public InventoryReservationFailedException(String message) {
        super("INVENTORY_RESERVATION_FAILED", message, HttpStatus.BAD_GATEWAY);
    }
}
