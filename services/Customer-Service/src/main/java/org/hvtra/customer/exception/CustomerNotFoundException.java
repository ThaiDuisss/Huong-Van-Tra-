package org.hvtra.customer.exception;

import org.hvtra.customer.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class CustomerNotFoundException extends CustomerServiceException {
    public CustomerNotFoundException(String message) {
        super(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }
}
