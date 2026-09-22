package org.hvtra.commonservice.common_core.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException{
    protected ResourceNotFoundException(String code, String message, HttpStatus status) {
        super("RESOURCE_NOT_FOUND", message, status);
    }
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
