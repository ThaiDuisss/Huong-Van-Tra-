package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

    /**
     * Giá bán đã chốt với khách tại quầy. Chỉ dùng cho đơn offline, để đơn đồng bộ muộn
     * khớp đúng hoá đơn đã in dù giá catalog đã đổi.
     */
    @DecimalMin(value = "0.00", message = "unitPriceSnapshot phải lớn hơn hoặc bằng 0")
    private BigDecimal unitPriceSnapshot;

    private String note;

    @Valid
    private List<CreateDiscountRequest> itemDiscounts =
            new ArrayList<>();
}
