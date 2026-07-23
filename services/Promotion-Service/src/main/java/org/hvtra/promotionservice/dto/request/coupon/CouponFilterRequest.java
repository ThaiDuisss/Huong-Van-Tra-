package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Bo loc danh sach coupon")
public class CouponFilterRequest {

    @Schema(description = "Tu khoa tim theo ma coupon hoac ten promotion", example = "TET")
    String keyword;

    @Schema(description = "ID promotion", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(description = "Trang thai coupon", example = "ACTIVE", implementation = CouponStatus.class)
    CouponStatus status;

    @Schema(description = "Pham vi su dung coupon", example = "PUBLIC", implementation = CouponScope.class)
    CouponScope scope;

    @Schema(description = "ID khach hang duoc gan coupon ca nhan", example = "550e8400-e29b-41d4-a716-446655440002")
    UUID customerId;

    @Schema(description = "Loc coupon het han tu thoi diem nay", example = "2027-01-01T00:00:00")
    LocalDateTime expiresFrom;

    @Schema(description = "Loc coupon het han den thoi diem nay", example = "2027-12-31T23:59:59")
    LocalDateTime expiresTo;
}
