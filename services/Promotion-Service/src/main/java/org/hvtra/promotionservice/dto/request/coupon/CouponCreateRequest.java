package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mới mã coupon")
public class CouponCreateRequest {

    @Schema(description = "ID khuyến mãi mà coupon thuộc về", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(description = "Phạm vi sử dụng coupon", example = "PUBLIC", implementation = CouponScope.class)
    @NotNull(message = "Coupon scope is required")
    CouponScope scope;

    @Schema(description = "ID khách hàng được gán coupon. Bắt buộc nếu scope là PERSONAL", example = "550e8400-e29b-41d4-a716-446655440010")
    UUID customerId;

    @Schema(description = "Mã coupon", example = "TET2027")
    @NotBlank(message = "Coupon code is required")
    @Size(max = 100, message = "Coupon code must not exceed 100 characters")
    String code;

    @Schema(description = "Lý do phát hành coupon", example = "MANUAL", implementation = CouponIssueReason.class)
    CouponIssueReason issueReason;

    @Schema(description = "ID đơn hàng nguồn phát sinh coupon nếu có", example = "550e8400-e29b-41d4-a716-446655440020")
    UUID sourceOrderId;

    @Schema(description = "Thời điểm phát hành coupon", example = "2027-01-15T10:30:00")
    LocalDateTime issuedAt;

    @Schema(description = "Thời điểm khách nhận/claim coupon", example = "2027-01-16T08:00:00")
    LocalDateTime claimedAt;

    @Schema(description = "Số lần coupon được phép sử dụng", example = "1")
    @Min(value = 1, message = "Coupon usage limit must be greater than or equal to 1")
    Integer usageLimit;

    @Schema(description = "So lan moi khach hang duoc phep su dung coupon", example = "1")
    @Min(value = 1, message = "Per customer limit must be greater than or equal to 1")
    Integer perCustomerLimit;

    @Schema(description = "Thời điểm hết hạn coupon", example = "2027-02-15T23:59:59")
    @FutureOrPresent(message = "Coupon expiration time must be now or in the future")
    LocalDateTime expiresAt;
}
