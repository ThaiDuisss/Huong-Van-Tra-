package org.hvtra.promotionservice.dto.request.couponusage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request giữ chỗ mã giảm giá")
public class CouponUsageReserveRequest {

    @Schema(description = "Mã coupon", example = "TET2027")
    @NotBlank(message = "Coupon code is required")
    String couponCode;

    @Schema(description = "ID đơn hàng", example = "550e8400-e29b-41d4-a716-446655440001")
    @NotNull(message = "Order id is required")
    UUID orderId;

    @Schema(description = "ID khách hàng", example = "550e8400-e29b-41d4-a716-446655440002")
    @NotNull(message = "Customer id is required")
    UUID customerId;

    @Schema(description = "Số tiền được giảm", example = "50000")
    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount amount must be greater than 0")
    BigDecimal discountAmount;
}