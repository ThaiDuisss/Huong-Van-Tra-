package org.hvtra.catalog.exception;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * File-Service was not called because its circuit breaker is open or its bulkhead is full.
 * Being a {@link BusinessException} with status 503, BaseGlobalExceptionHandler answers
 * {@code 503 FILE_SERVICE_UNAVAILABLE} without any extra handler.
 */
public class FileServiceUnavailableException extends BusinessException {

    public static final String CODE = "FILE_SERVICE_UNAVAILABLE";

    public FileServiceUnavailableException(String message, Throwable cause) {
        super(CODE, message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
