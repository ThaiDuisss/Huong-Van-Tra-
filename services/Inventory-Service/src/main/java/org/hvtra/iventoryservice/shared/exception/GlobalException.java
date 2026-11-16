package org.hvtra.iventoryservice.shared.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class GlobalException extends BaseGlobalExceptionHandler {

    private static final String DOWNSTREAM_RETRY_AFTER_SECONDS = "15";

    // Base handler trả 400 cho mọi AppException — lỗi khai báo trong ErrorCode thì trả đúng HTTP status của nó
    @Override
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(AppException exception) {
        returnError(exception);
        HttpStatus status = ErrorCode.fromCode(exception.getCode())
                .map(ErrorCode::getHttpStatus)
                .orElse(HttpStatus.BAD_REQUEST);
        ApiResponse<Object> response = ApiResponse.builder()
                .status("error")
                .code(exception.getCode())
                .message(exception.getMessage())
                .data(null)
                .build();
        return ResponseEntity.status(status).body(response);
    }

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

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ErrorResponse> handleOutOfStock(
            OutOfStockException exception, HttpServletRequest request) {
        log.warn("Out of stock at {}: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("OUT_OF_STOCK", exception.getMessage(), 409));
    }

    /**
     * Circuit breaker OPEN for a downstream service (Catalog / Order / Identity): fail fast with 503
     * instead of falling through to the generic 500. Same status the base handler already uses for
     * Feign transport failures ({@code RetryableException}).
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(
            CallNotPermittedException exception, HttpServletRequest request) {
        log.warn("Downstream circuit breaker '{}' is open at {}",
                exception.getCausingCircuitBreakerName(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, DOWNSTREAM_RETRY_AFTER_SECONDS)
                .body(new ErrorResponse(
                        "DOWNSTREAM_SERVICE_UNAVAILABLE",
                        "Dịch vụ phụ thuộc đang tạm ngắt do lỗi liên tục. Vui lòng thử lại sau.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    /** Too many concurrent calls to one downstream service: shed load with 503 rather than queue. */
    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ErrorResponse> handleBulkheadFull(
            BulkheadFullException exception, HttpServletRequest request) {
        log.warn("Downstream bulkhead rejected a call at {}: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, DOWNSTREAM_RETRY_AFTER_SECONDS)
                .body(new ErrorResponse(
                        "DOWNSTREAM_SERVICE_BUSY",
                        "Hệ thống đang có quá nhiều yêu cầu tới dịch vụ phụ thuộc. Vui lòng thử lại sau.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String requiredType = exception.getRequiredType() == null
                ? "valid value"
                : exception.getRequiredType().getSimpleName();
        String message = "Invalid value '%s' for parameter '%s'; expected %s"
                .formatted(exception.getValue(), exception.getName(), requiredType);
        log.warn("Invalid request parameter at {}: {}", request.getRequestURI(), message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_PARAMETER", message, 400));
    }

//    @ExceptionHandler(HttpMessageNotReadableException.class)
//    public ResponseEntity<ErrorResponse> handleUnreadableBody(
//            HttpMessageNotReadableException exception, HttpServletRequest request) {
//        log.warn("Invalid request body at {}: {}", request.getRequestURI(), exception.getMessage());
//        return ResponseEntity.badRequest()
//                .body(new ErrorResponse("INVALID_REQUEST_BODY", "Invalid request body", 400));
//    }

    @Override
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), exception);
        return super.handleUnexpectedException(exception, request);
    }
}
