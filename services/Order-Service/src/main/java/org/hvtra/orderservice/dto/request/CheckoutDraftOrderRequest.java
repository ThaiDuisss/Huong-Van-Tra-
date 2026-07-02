package org.hvtra.orderservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CheckoutDraftOrderRequest {
    private BigDecimal paidAmount;
    private String paymentMethod;
    private String paymentCode;
    private UUID changedBy;
}
