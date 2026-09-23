package org.hvtra.deliveryservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AhamoveIntegrationException extends BaseException {
    public AhamoveIntegrationException(String message) {
        super("8008", message, HttpStatus.BAD_REQUEST);
    }

    public AhamoveIntegrationException(String message, Throwable cause) {
        super("8008", message, HttpStatus.BAD_REQUEST, cause);
    }
}
