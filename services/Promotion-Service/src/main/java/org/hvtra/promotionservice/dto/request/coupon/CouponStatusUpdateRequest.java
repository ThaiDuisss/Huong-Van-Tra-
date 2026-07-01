package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật trạng thái coupon")
public class CouponStatusUpdateRequest {

    @Schema(
            description = "Trạng thái mới của coupon",
            example = "ACTIVE",
            allowableValues = {"DRAFT", "SCHEDULED", "ACTIVE", "PAUSED", "EXPIRED", "CANCELLED", "ARCHIVED"}
    )
    @NotNull(message = "Coupon status is required")
    CouponStatus status;
}