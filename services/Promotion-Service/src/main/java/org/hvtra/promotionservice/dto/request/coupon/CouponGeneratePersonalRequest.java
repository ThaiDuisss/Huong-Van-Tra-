package org.hvtra.promotionservice.dto.request.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mã coupon cá nhân")
public class CouponGeneratePersonalRequest {

    @Schema(description = "ID khách hàng được cấp mã", example = "550e8400-e29b-41d4-a716-446655440010")
    @NotNull(message = "Customer ID is required")
    UUID customerId;

    @Schema(description = "Tiền tố mã coupon", example = "VIP")
    String prefix;

    @Schema(description = "Số lần được phép sử dụng", example = "1")
    @Min(value = 1, message = "Usage limit must be greater than or equal to 1")
    Integer usageLimit;

    @Schema(description = "Lý do phát hành coupon", example = "ORDER_AMOUNT_REACHED")
    CouponIssueReason issueReason;

    @Schema(description = "ID đơn hàng nguồn phát sinh coupon", example = "550e8400-e29b-41d4-a716-446655440020")
    UUID sourceOrderId;

    @Schema(description = "Thời điểm hết hạn coupon", example = "2027-02-15T23:59:59")
    @FutureOrPresent(message = "Coupon expiration time must be now or in the future")
    LocalDateTime expiresAt;
}