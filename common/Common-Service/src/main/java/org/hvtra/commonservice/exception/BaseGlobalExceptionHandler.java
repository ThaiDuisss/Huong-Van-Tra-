package org.hvtra.commonservice.exception;

import feign.FeignException;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
@Slf4j

public abstract class BaseGlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(AppException exception) {
        returnError(exception);
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .code(exception.getCode())
                .message(exception.getMessage())
                .data(null)
                .build();
        return ResponseEntity.badRequest().body(response);
    }
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                "BAD_REQUEST",
                "Request body không hợp lệ hoặc UUID sai định dạng",
                HttpStatus.BAD_REQUEST.value()
        );
        log.error("Bad request body at {}: {}", Instant.now(), exception.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String message = exception.getRequiredType() == UUID.class
                ? exception.getName() + " sai định dạng UUID"
                : exception.getName() + " không đúng định dạng";
        ErrorResponse response = new ErrorResponse(
                "BAD_REQUEST",
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        log.error("Argument type mismatch at {}: {}", Instant.now(), exception.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        log.error("Constraint violation at {}: {}", Instant.now(), message, exception);

        return ResponseEntity.badRequest().body(response);
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
    public void returnError(Exception e) {
        log.error("AppException: {}", e.getMessage());
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyExistsException(
            ResourceAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                "RESOURCE_ALREADY_EXISTS",
                exception.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );

        log.error(
                "Resource already exists at {}: path={} message={}",
                Instant.now(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}
