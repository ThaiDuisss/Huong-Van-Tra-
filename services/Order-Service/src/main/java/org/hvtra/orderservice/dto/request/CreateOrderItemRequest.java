package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderItemRequest {

    @NotNull(message = "productVariantId không được null")
    private UUID productVariantId;

    @NotNull(message = "quantity không được null")
    @Positive(message = "quantity phải lớn hơn 0")
    private BigDecimal quantity;

    private UUID bomVersionId;

    private String note;

    @Valid
    private List<CreateDiscountRequest> itemDiscounts =
            new ArrayList<>();
}
