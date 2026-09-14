package org.hvtra.iventoryservice.integration.order.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnInventoryDispositionRequestedLine {
    String returnItemId;
    String orderItemId;
    String productVariantId;
    String quantity;
    String result;
    String destinationLocationId;
    String note;
}
