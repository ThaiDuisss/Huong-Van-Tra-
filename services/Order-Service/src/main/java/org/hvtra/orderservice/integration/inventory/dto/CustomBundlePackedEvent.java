package org.hvtra.orderservice.integration.inventory.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

/** Inventory đã đóng gói và xuất nguyên liệu cho một dòng bundle. */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomBundlePackedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String packingId;
    String packingCode;
    String orderId;
    String orderCode;
    String orderItemId;
    String issueTransactionCode;
    String packedAt;
}
