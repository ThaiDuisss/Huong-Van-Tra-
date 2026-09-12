package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderReturnRefundPreviewResponse {

    private UUID returnId;
    private UUID orderId;
    private BigDecimal refundAmount;
    private String paymentMethod;
    private boolean eligible;
    private String reason;
}
