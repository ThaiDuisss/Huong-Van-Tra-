package org.hvtra.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BaseGlobalExceptionHandler;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice
@Slf4j(topic = "ERROR-EXCEPTION")
public class OrderGlobalExceptionHandler extends BaseGlobalExceptionHandler {
    // Xử lý AccessDeniedException (403) đã chuyển lên BaseGlobalExceptionHandler dùng chung.

    private static final String RETRY_AFTER_SECONDS = "10";

    /**
     * Circuit breaker đang mở hoặc bulkhead đã đầy: chưa có request nào được gửi đi, trả 503 để
     * client biết có thể thử lại. Handler này cụ thể hơn handler FeignClientException (502) của lớp cha.
     */
    @ExceptionHandler(DownstreamUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(DownstreamUnavailableException exception) {
        log.warn(
                "Downstream call refused: service={} reason={} method={}",
                exception.getService(),
                exception.getReason(),
                exception.getMethodKey()
        );
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(new ErrorResponse(
                        "DOWNSTREAM_SERVICE_UNAVAILABLE",
                        exception.getMessage(),
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }
}
