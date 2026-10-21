package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateDraftOrderItemQuantityRequest {
    @NotNull
    @PositiveOrZero
    private BigDecimal quantity;
    /** Khách đồng ý chờ hàng: cho đặt vượt số lượng có thể bán. */
    private Boolean allowBackorder;
}
