package org.hvtra.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class OrderGlobalExceptionHandler extends BaseGlobalExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", exception.getMessage(), 403));
    }
}
