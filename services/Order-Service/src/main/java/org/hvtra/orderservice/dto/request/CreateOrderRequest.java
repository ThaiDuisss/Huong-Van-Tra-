package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
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

    @DecimalMin(value = "0.00", message = "discountAmount phải lớn hơn hoặc bằng 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Valid
    @NotEmpty(message = "items không được rỗng")
    private List<CreateOrderItemRequest> items;
}
