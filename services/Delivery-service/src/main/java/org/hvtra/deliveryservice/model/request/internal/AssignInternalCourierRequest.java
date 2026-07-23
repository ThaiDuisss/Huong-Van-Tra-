package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

//gắn request người chuyển phát
public record AssignInternalCourierRequest(
        @NotNull UUID courierEmployeeId,
        @NotBlank String courierName
) {
}
