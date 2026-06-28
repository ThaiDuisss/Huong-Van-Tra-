package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderStatsResponse {
    private long total;
    private long pending;
    private long processing;
    private long shipping;
    private long completed;
    private long returnedOrRefunded;
}
