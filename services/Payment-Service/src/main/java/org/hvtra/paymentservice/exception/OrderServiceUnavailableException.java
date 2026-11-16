package org.hvtra.paymentservice.exception;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Order-Service đang bị ngắt mạch (circuit breaker OPEN) hoặc quá tải (bulkhead đầy) nên lời gọi
 * bị từ chối ngay, KHÔNG có request nào được gửi đi. BaseGlobalExceptionHandler trả HTTP 503 với mã
 * {@link #CODE}; bên gọi (kể cả webhook) phải coi là chưa xử lý và thử lại sau, không được coi là
 * thành công.
 */
public class OrderServiceUnavailableException extends BusinessException {

    public static final String CODE = "ORDER_SERVICE_UNAVAILABLE";

    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(CODE, message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
