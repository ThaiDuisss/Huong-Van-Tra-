package org.hvtra.customer.service;

import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderRefundedEvent;

/** Cập nhật chi tiêu, số đơn, hạng và điểm của khách từ sự kiện đơn hàng. */
public interface CustomerPurchaseService {

    /**
     * Đơn hoàn tất: tích điểm theo hạng hiện tại, cộng chi tiêu và số đơn, rồi nâng hạng nếu đủ.
     *
     * @return false nếu event bị bỏ qua (thiếu dữ liệu, đơn không có khách hoặc khách không còn tồn tại)
     */
    boolean onOrderCompleted(OrderCompletedEvent event);

    /**
     * Đơn được hoàn tiền: trừ chi tiêu (hoàn toàn bộ thì trừ thêm một đơn) và trừ lại điểm. Hạng giữ nguyên.
     *
     * @return false nếu event bị bỏ qua
     */
    boolean onOrderRefunded(OrderRefundedEvent event);
}
