package org.hvtra.iventoryservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.exception.BaseGlobalExceptionHandler;
import org.hvtra.commonservice.exception.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class GlobalException extends BaseGlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESOURCE_NOT_FOUND", exception.getMessage(), 404));
    }

    @ExceptionHandler({BadRequestException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(
            RuntimeException exception, HttpServletRequest request) {
        log.warn("Bad request at {}: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", exception.getMessage(), 400));
    }
}
