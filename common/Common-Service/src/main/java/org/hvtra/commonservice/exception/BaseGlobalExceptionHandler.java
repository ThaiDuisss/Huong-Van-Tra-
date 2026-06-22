package org.hvtra.commonservice.exception;

import feign.FeignException;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j

public abstract class BaseGlobalExceptionHandler {
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getCode(),
                exception.getMessage(),
                exception.getStatus().value()
        );
        log.error("Error occurred at {}: {} - {}", Instant.now(), exception.getCode(), exception.getMessage());

        return ResponseEntity
                .status(exception.getStatus())
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Đã xảy ra lỗi hệ thống",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        log.error("Unexpected error occurred at {}: {}", Instant.now(), exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        log.error("Validation error at {}: {}", Instant.now(), message, exception);

        return ResponseEntity.badRequest().body(response);
    }
    public ResponseEntity<ErrorResponse> handleFeignClientException(
            FeignClientException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus());

        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        /*
         * Nếu service khác lỗi 5xx, không nên trả nguyên 500 của nó.
         * Service hiện tại nên báo 502 Bad Gateway,
         * nghĩa là lỗi đến từ service phụ thuộc.
         */
        if (status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }


        String code = "REMOTE_SERVICE_ERROR";
        String message = ex.getMessage() != null ? ex.getMessage() : "Upstream service error";

        ErrorResponse response = new ErrorResponse(
                code,
                message,
                status.value()
        );

        log.error("Feign client error at {}: status={} path={} message={}", Instant.now(), status.value(), request.getRequestURI(), message, ex);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<?> handleRetryableException(
            RetryableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                Map.<String, Object>of(
                        "timestamp", LocalDateTime.now(),
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "error", "Service Unavailable",
                        "message", "Remote service is unavailable or timeout",
                        "path", request.getRequestURI()
                )
        );
    }

    /**
     * Phòng trường hợp FeignException bị lọt ra ngoài.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<?> handleFeignException(
            FeignException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                Map.<String, Object>of(
                        "timestamp", LocalDateTime.now(),
                        "status", HttpStatus.BAD_GATEWAY.value(),
                        "error", "Bad Gateway",
                        "message", ex.getMessage(),
                        "path", request.getRequestURI()
                )
        );
    }
}
