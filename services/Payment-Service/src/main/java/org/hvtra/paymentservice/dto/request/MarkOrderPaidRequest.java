package org.hvtra.paymentservice.dto.request;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MarkOrderPaidRequest {

    private BigDecimal paidAmount;
    private String paymentCode;
    private UUID changedBy;
}
