package org.hvtra.customer.exception;

import org.hvtra.commonservice.common_core.exception.BaseException;
import org.hvtra.customer.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class CustomerServiceException extends BaseException {
    public CustomerServiceException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static CustomerServiceException notFound(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    public static CustomerServiceException conflict(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_CONFLICT, message, HttpStatus.CONFLICT);
    }

    public static CustomerServiceException invalidRequest(String message) {
        return new CustomerServiceException(ErrorCode.CUSTOMER_INVALID_REQUEST, message, HttpStatus.BAD_REQUEST);
    }
}
