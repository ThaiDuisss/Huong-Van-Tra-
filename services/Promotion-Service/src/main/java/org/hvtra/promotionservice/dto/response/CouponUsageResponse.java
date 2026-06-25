package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thông tin lịch sử sử dụng coupon")
public class CouponUsageResponse {

    UUID id;

    UUID couponId;

    String couponCode;

    UUID orderId;

    UUID customerId;

    BigDecimal discountAmount;

    CouponUsageStatus status;

    String reservationToken;

    LocalDateTime reservedAt;

    LocalDateTime reservationExpiresAt;

    LocalDateTime usedAt;

    LocalDateTime releasedAt;

    Instant createdAt;

    Instant updatedAt;
}