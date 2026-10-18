package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;

import java.util.UUID;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;

public interface OrderPaymentEventService {

    void handlePaymentEvent(PaymentOrderEventPayload event);

    void handlePaymentSucceeded(PaymentOrderEventPayload event);

    void processPaidOrderInventory(Order order, PaymentOrderEventPayload event, boolean immediateConsume);

    /**
     * Đơn đã ở trạng thái "đã thanh toán" (PAYMENT_CONFIRMED, paymentStatus PAID): trừ tồn kho, rồi hoàn tất đơn
     * (COMPLETED với đơn bán tại quầy), ghi lịch sử, cộng điểm khách, phát sự kiện. Trừ kho thất bại thì đơn giữ
     * PAYMENT_CONFIRMED với trạng thái kho FAILED để scheduler thử lại.
     *
     * @return true nếu tồn kho đã xử lý xong
     */
    boolean finalizePaidOrder(Order order, PaymentOrderEventPayload event, OrderStatus statusBeforePayment,
                              UUID changedBy, String causationId);

    /** Phiên thanh toán kết thúc không có tiền: đơn về nháp, hạn nháp mới, ghi lịch sử. */
    Order returnOrderToDraft(Order order, String reason);

    /** Scheduler: thử trừ kho lại cho các đơn đã thanh toán nhưng tồn kho chưa xử lý được. */
    int retryFailedInventory();
}
