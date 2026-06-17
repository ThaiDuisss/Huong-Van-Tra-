package org.hvtra.customer.exception;

import org.hvtra.commonservice.exception.BaseException;
import org.springframework.http.HttpStatus;

public class CustomerServiceException extends BaseException {
    public CustomerServiceException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
