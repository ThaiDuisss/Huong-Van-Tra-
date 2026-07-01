package org.hvtra.customer.exception;

import org.hvtra.customer.constant.ErrorCode;
import org.springframework.http.HttpStatus;

public class NoActiveCustomerTierConfiguredException extends CustomerServiceException {
    public NoActiveCustomerTierConfiguredException() {
        super(ErrorCode.CUSTOMER_INVALID_REQUEST, "No ACTIVE customer tier is configured", HttpStatus.CONFLICT);
    }
}
