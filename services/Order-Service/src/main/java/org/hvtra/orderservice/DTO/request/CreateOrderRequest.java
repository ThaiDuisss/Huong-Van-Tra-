package org.hvtra.orderservice.DTO.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    private UUID tenantId;
    private UUID branchId;
    private UUID terminalId;
    private UUID shiftId;
    private UUID customerId;

    private String orderType = "POS";

    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal shippingFee = BigDecimal.ZERO;

    private List<CreateOrderItemRequest> items;
}