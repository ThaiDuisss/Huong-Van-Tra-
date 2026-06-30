package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderStatus;

@Data
public class UpdateOrderStatusRequest {
    @NotNull(message = "status không được null")
    private OrderStatus status;

    private String note;
}
