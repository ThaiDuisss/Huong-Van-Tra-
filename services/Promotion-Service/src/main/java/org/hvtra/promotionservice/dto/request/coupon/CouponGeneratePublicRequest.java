package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mã coupon public")
public class CouponGeneratePublicRequest {

    @Schema(description = "Tiền tố mã coupon", example = "TET2027")
    String prefix;

    @Schema(description = "Số lần được phép sử dụng", example = "100")
    @Min(value = 1, message = "Usage limit must be greater than or equal to 1")
    Integer usageLimit;

    @Schema(description = "Lý do phát hành coupon", example = "MANUAL")
    CouponIssueReason issueReason;

    @Schema(description = "Thời điểm hết hạn coupon", example = "2027-02-15T23:59:59")
    @FutureOrPresent(message = "Coupon expiration time must be now or in the future")
    LocalDateTime expiresAt;
}