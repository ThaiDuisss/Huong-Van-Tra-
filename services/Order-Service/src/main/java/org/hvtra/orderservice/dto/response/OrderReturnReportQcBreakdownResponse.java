package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class OrderReturnReportQcBreakdownResponse {
    private String result;
    private BigDecimal quantity;
    private BigDecimal percentage;
}
