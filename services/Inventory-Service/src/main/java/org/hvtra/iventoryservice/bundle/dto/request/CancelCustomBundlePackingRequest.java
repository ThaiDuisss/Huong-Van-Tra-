package org.hvtra.iventoryservice.bundle.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Order-Service huỷ mọi yêu cầu đóng gói chưa đóng của đơn khi huỷ đơn. */
public record CancelCustomBundlePackingRequest(
        @NotNull UUID orderId,
        @Size(max = 500) String reason
) {
}
