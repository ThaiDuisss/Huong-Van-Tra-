package org.hvtra.deliveryservice.model.response.internal;

import java.util.UUID;

public record InternalPlanningRejection(
        UUID shipmentId,
        String shipmentCode,
        String reason
) {
}
