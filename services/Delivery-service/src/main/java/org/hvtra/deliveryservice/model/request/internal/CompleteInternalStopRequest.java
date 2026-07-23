package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record CompleteInternalStopRequest(
        @PositiveOrZero long collectedCodAmount,
        String receiverName,
        String receiverRelationship,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Valid List<InternalDeliveryProofCommand> proofs
) {
    public List<InternalDeliveryProofCommand> resolvedProofs() {
        return proofs == null ? List.of() : List.copyOf(proofs);
    }
}
