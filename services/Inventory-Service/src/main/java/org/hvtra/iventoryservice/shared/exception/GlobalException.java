package org.hvtra.iventoryservice.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class GlobalException extends BaseGlobalExceptionHandler {
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
