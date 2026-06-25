package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mới mã giảm giá")
public class CouponCreateRequest {

    @Schema(
            description = "ID khách hàng được gán mã, null nếu mã dùng chung",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID customerId;

    @Schema(description = "Mã giảm giá", example = "TET2027")
    @NotBlank(message = "Coupon code is required")
    @Size(max = 100, message = "Coupon code must not exceed 100 characters")
    String code;

    @Schema(description = "Tổng số lần coupon được sử dụng", example = "100")
    @NotNull(message = "Usage limit is required")
    @Min(value = 1, message = "Usage limit must be greater than or equal to 1")
    Integer usageLimit;

    @Schema(description = "Số lần coupon đã được sử dụng", example = "0")
    @Min(value = 0, message = "Used count must be greater than or equal to 0")
    Integer usedCount;

    @Schema(description = "Thời gian hết hạn", example = "2027-02-10T23:59:59")
    LocalDateTime expiresAt;

}
