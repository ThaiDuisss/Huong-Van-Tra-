package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderRefundStatsResponse {

    private long total;
    private long pendingRefund;
    private long refunded;
    private long notReady;
    private long failed;
}
