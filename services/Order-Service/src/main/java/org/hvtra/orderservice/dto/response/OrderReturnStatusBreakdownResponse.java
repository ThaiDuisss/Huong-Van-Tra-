package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

@Data
@AllArgsConstructor
public class OrderReturnStatusBreakdownResponse {
    private ReturnStatus status;
    private Long count;
}
