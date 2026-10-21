package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderPaymentService {

    OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request);

    /** Thu ngân huỷ phiên chuyển khoản chưa có tiền: đơn quay về nháp để chọn lại phương thức. */
    OrderResponse cancelPosPayment(UUID orderId);
}
