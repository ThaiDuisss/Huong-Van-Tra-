package org.hvtra.deliveryservice.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.springframework.http.HttpStatus;

public class GhnIntegrationException extends BaseException {
    public GhnIntegrationException(String message) {
        super("808",message, HttpStatus.NOT_FOUND);
    }

}
