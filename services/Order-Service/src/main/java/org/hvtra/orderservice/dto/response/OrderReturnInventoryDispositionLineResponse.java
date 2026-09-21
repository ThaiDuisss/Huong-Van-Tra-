package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderReturnInventoryDispositionLineResponse {
    private UUID returnItemId;
    private UUID orderItemId;
    private UUID productVariantId;
    private BigDecimal quantity;
    private ReturnInventoryDispositionResult result;
    private UUID destinationLocationId;
    private String note;
}
