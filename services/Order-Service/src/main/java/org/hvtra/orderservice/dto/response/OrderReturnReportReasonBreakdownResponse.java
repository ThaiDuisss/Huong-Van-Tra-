package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnReason;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class OrderReturnReportReasonBreakdownResponse {
    private ReturnReason reason;
    private Long itemCount;
    private BigDecimal quantity;
}
