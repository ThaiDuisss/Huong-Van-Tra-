package org.hvtra.promotionservice.dto.request.couponusage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Bo loc danh sach phien su dung coupon")
public class CouponUsageFilterRequest {

    @Schema(description = "ID coupon", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID couponId;

    @Schema(description = "Ma coupon", example = "TET2027")
    String couponCode;

    @Schema(description = "ID don hang", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID orderId;

    @Schema(description = "ID khach hang", example = "550e8400-e29b-41d4-a716-446655440002")
    UUID customerId;

    @Schema(description = "Trang thai phien su dung coupon", example = "RESERVED", implementation = CouponUsageStatus.class)
    CouponUsageStatus status;

    @Schema(description = "Loc theo thoi diem giu coupon tu", example = "2027-01-01T00:00:00")
    LocalDateTime reservedFrom;

    @Schema(description = "Loc theo thoi diem giu coupon den", example = "2027-12-31T23:59:59")
    LocalDateTime reservedTo;
}
