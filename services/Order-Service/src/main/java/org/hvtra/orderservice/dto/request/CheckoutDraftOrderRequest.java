package org.hvtra.orderservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CheckoutDraftOrderRequest {
    private BigDecimal paidAmount;
    private BigDecimal discountAmount;
    private String paymentMethod;
    private String paymentCode;
    private UUID changedBy;
    /** Khách đồng ý chờ hàng: đơn thiếu hàng sẽ được ghi nhận là đơn chờ hàng thay vì bị từ chối. */
    private Boolean allowBackorder;
    /** Chỉ dùng cho đơn chờ hàng trả tiền mặt: thu trước tiền cọc, phần còn lại thu khi giao hàng. */
    private BigDecimal depositAmount;
}
