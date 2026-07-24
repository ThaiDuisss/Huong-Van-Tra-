package org.hvtra.promotionservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionPreviewLineRequest {
    @NotNull
    UUID productId;
    UUID categoryId;
    @NotNull
    @Min(1)
    Integer quantity;
    @NotNull
    @DecimalMin("0.0")
    BigDecimal lineAmount;
}
