package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderReturnItemResponse {
    private UUID id;
    private UUID orderItemId;
    private BigDecimal quantity;
    private ReturnReason reason;
    private ReturnCondition condition;
    private String note;
    private String skuSnapshot;
    private String productNameSnapshot;
    private String variantNameSnapshot;
}
