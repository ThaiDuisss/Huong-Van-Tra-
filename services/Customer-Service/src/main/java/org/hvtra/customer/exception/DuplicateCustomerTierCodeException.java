package org.hvtra.customer.exception;

import org.hvtra.customer.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class DuplicateCustomerTierCodeException extends CustomerServiceException {
    public DuplicateCustomerTierCodeException(String message) {
        super(ErrorCode.CUSTOMER_CONFLICT, message, HttpStatus.CONFLICT);
    }
}
