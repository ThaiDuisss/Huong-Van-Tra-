package org.hvtra.paymentservice.gateway;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.paymentservice.client.OrderClient;
import org.hvtra.paymentservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.paymentservice.exception.OrderServiceUnavailableException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cửa duy nhất để Payment-Service gọi Order-Service qua Feign. Mọi lời gọi đi qua
 * Retry( CircuitBreaker( Bulkhead( Feign ) ) ) của instance {@code orderService}; KHÔNG inject
 * {@link OrderClient} trực tiếp ở nơi khác và không tự gọi các method này từ bên trong class
 * (self-invocation bỏ qua proxy AOP).
 *
 * <p>Không có fallback trả dữ liệu giả: trạng thái thanh toán/đơn hàng không được bịa. Khi breaker
 * OPEN hoặc bulkhead đầy, ném {@link OrderServiceUnavailableException} (HTTP 503); lỗi khác của
 * Feign được giữ nguyên để BaseGlobalExceptionHandler dịch như trước.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGateway {

    public static final String ORDER_SERVICE = "orderService";

    private final OrderClient orderClient;

    /** Đọc idempotent: được thử lại khi lỗi tạm thời (xem TransientFeignFailurePredicate). */
    @Retry(name = ORDER_SERVICE)
    @CircuitBreaker(name = ORDER_SERVICE, fallbackMethod = "rejectPaymentInfo")
    @Bulkhead(name = ORDER_SERVICE)
    public OrderPaymentInfoResponse getPaymentInfo(UUID orderId) {
        return orderClient.getPaymentInfo(orderId);
    }

    /**
     * Ghi KHÔNG idempotent: Order-Service đổi trạng thái đơn, ghi lịch sử và phát sự kiện ở mỗi lần gọi,
     * lần thứ hai bị validateMarkPaid từ chối. Vì vậy cố ý không có {@code @Retry}: timeout nghĩa là
     * "không rõ kết quả", bên gọi phải để giao dịch ở trạng thái chờ và đối soát lại.
     */
    @CircuitBreaker(name = ORDER_SERVICE, fallbackMethod = "rejectMarkOrderPaid")
    @Bulkhead(name = ORDER_SERVICE)
    public void markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        orderClient.markOrderPaid(orderId, request);
    }

    private OrderPaymentInfoResponse rejectPaymentInfo(UUID orderId, CallNotPermittedException exception) {
        throw circuitOpen("getPaymentInfo", orderId, exception);
    }

    private OrderPaymentInfoResponse rejectPaymentInfo(UUID orderId, BulkheadFullException exception) {
        throw bulkheadFull("getPaymentInfo", orderId, exception);
    }

    private void rejectMarkOrderPaid(UUID orderId, MarkOrderPaidRequest request, CallNotPermittedException exception) {
        throw circuitOpen("markOrderPaid", orderId, exception);
    }

    private void rejectMarkOrderPaid(UUID orderId, MarkOrderPaidRequest request, BulkheadFullException exception) {
        throw bulkheadFull("markOrderPaid", orderId, exception);
    }

    private OrderServiceUnavailableException circuitOpen(String operation, UUID orderId, Throwable cause) {
        log.warn("Order circuit breaker is OPEN, rejecting {} orderId={}", operation, orderId);
        return new OrderServiceUnavailableException(
                "Order Service đang tạm ngắt do lỗi liên tục, vui lòng thử lại sau.", cause);
    }

    private OrderServiceUnavailableException bulkheadFull(String operation, UUID orderId, Throwable cause) {
        log.warn("Order bulkhead is full, rejecting {} orderId={}", operation, orderId);
        return new OrderServiceUnavailableException(
                "Đang có quá nhiều yêu cầu tới Order Service, vui lòng thử lại sau.", cause);
    }
}
