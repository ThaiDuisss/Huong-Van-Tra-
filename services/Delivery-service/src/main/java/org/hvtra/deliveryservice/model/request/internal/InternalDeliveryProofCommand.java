package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.constraints.NotNull;
import org.hvtra.deliveryservice.model.enums.DeliveryProofType;

import java.util.UUID;

public record InternalDeliveryProofCommand(
        @NotNull DeliveryProofType proofType,
        UUID fileId,
        boolean otpVerified
) {
}
