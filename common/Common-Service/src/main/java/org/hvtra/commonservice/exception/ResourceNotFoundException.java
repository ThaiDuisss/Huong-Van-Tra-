package org.hvtra.commonservice.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException{
    protected ResourceNotFoundException(String code, String message, HttpStatus status) {
        super("RESOURCE_NOT_FOUND", message, status);
    }
}
