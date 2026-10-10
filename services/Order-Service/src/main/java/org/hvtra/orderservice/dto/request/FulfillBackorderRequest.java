package org.hvtra.orderservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FulfillBackorderRequest {
    /** Tiền mặt khách đưa để trả phần còn lại. Bỏ trống nếu đơn đã thu đủ. */
    private BigDecimal receivedAmount;
    private UUID changedBy;
}
