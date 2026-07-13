package org.hvtra.promotionservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PromotionPreviewRequest {

    @NotNull
    @DecimalMin(value = "0.0")
    @Schema(description = "Tổng tiền hàng của đơn POS", example = "500000")
    BigDecimal orderAmount;

    @Schema(description = "ID khach hang dang duoc chon tren POS", example = "550e8400-e29b-41d4-a716-446655440002")
    UUID customerId;

    @Schema(description = "ID chi nhanh POS", example = "550e8400-e29b-41d4-a716-446655440003")
    UUID branchId;

    @Schema(description = "Kenh ban hang", example = "POS", implementation = PromotionChannel.class)
    PromotionChannel channel;

    @Schema(description = "Ma coupon khach nhap neu muon preview theo ma cu the", example = "TET2027")
    String couponCode;
}
