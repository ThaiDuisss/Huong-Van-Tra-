package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo hàng loạt mã coupon")
public class CouponBulkGenerateRequest {

    @Schema(description = "Phạm vi coupon cần tạo", example = "PUBLIC")
    @NotNull(message = "Coupon scope is required")
    CouponScope scope;

    @Schema(description = "Danh sách khách hàng. Bắt buộc nếu scope là PERSONAL")
    List<UUID> customerIds;

    @Schema(description = "Số lượng mã cần tạo. Dùng cho PUBLIC bulk", example = "100")
    @Min(value = 1, message = "Quantity must be greater than or equal to 1")
    @Max(value = 10000, message = "Quantity must not exceed 10000")
    Integer quantity;

    @Schema(description = "Tiền tố mã coupon", example = "BF2027")
    String prefix;

    @Schema(description = "Số lần mỗi coupon được phép sử dụng", example = "1")
    @Min(value = 1, message = "Usage limit must be greater than or equal to 1")
    Integer usageLimit;

    @Schema(description = "Lý do phát hành coupon", example = "MANUAL")
    CouponIssueReason issueReason;

    @Schema(description = "Thời điểm hết hạn coupon", example = "2027-12-01T23:59:59")
    @FutureOrPresent(message = "Coupon expiration time must be now or in the future")
    LocalDateTime expiresAt;
}