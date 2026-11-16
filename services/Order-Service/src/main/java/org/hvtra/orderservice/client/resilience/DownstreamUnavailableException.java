package org.hvtra.orderservice.client.resilience;

import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.springframework.http.HttpStatus;

/**
 * The call was refused locally, before any request was sent: the service's circuit breaker is open
 * or its bulkhead is full. Nothing happened downstream, so the operation can be attempted again later.
 *
 * <p>It extends {@link FeignClientException} with status 503 on purpose: call sites that already
 * translate or degrade on a downstream 503 treat a refused call the same way, and
 * {@code OrderGlobalExceptionHandler} answers 503 when it reaches the controller layer.
 */
public class DownstreamUnavailableException extends FeignClientException {

    public enum Reason {
        CIRCUIT_OPEN,
        BULKHEAD_FULL
    }

    private final DownstreamService service;
    private final Reason reason;

    public DownstreamUnavailableException(
            DownstreamService service,
            Reason reason,
            String methodKey,
            Throwable cause
    ) {
        super(HttpStatus.SERVICE_UNAVAILABLE.value(), methodKey, message(service, reason), null);
        this.service = service;
        this.reason = reason;
        initCause(cause);
    }

    public DownstreamService getService() {
        return service;
    }

    public Reason getReason() {
        return reason;
    }

    private static String message(DownstreamService service, Reason reason) {
        return reason == Reason.CIRCUIT_OPEN
                ? "Dịch vụ " + service.displayName() + " đang tạm ngắt do lỗi liên tục. Vui lòng thử lại sau ít phút."
                : "Dịch vụ " + service.displayName() + " đang quá tải. Vui lòng thử lại sau ít phút.";
    }
}
