package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddDraftOrderItemRequest {
    private UUID orderId;
    private UUID tenantId;
    private UUID branchId;
    private UUID terminalId;
    private UUID shiftId;
    private UUID cashierId;
    private String cashierNameSnapshot;
    private String branchNameSnapshot;
    private String terminalNameSnapshot;
    private String shiftCodeSnapshot;
    private UUID customerId;
    private String customerNameSnapshot;
    private String customerPhoneSnapshot;
    @NotNull
    private UUID productVariantId;
    @Positive
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private UUID bomVersionId;
    private UUID inventoryLocationId;
    private String note;
}
