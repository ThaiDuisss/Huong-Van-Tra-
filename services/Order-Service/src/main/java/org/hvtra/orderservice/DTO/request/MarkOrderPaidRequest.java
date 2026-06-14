package org.hvtra.orderservice.domain.DTO.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class MarkOrderPaidRequest {

    private BigDecimal paidAmount;
    private String paymentCode;
    private UUID changedBy;
}