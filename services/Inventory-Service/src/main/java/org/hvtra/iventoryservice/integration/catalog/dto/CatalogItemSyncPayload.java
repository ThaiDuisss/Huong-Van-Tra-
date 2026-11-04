package org.hvtra.iventoryservice.integration.catalog.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CatalogItemSyncPayload implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String itemType;
    String referenceId;
    String referenceCode;
    String name;
    String unitId;
    String status;
    Integer reorderPoint;
    Boolean isDeleted;
    String occurredAt;
    String causationId;
}

