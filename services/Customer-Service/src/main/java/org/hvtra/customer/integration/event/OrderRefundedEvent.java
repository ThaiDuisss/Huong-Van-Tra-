package org.hvtra.customer.integration.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload ORDER_REFUNDED (topic customer.order-refunded) do Order-Service phát khi một phiếu hoàn tiền xong.
 * fullRefund = true khi lần hoàn này làm đơn chuyển sang hoàn toàn bộ.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRefundedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    UUID orderId;
    String orderCode;
    UUID customerId;
    UUID refundId;
    String refundCode;
    BigDecimal refundAmount;
    BigDecimal orderFinalAmount;
    boolean fullRefund;
    Instant refundedAt;
}
