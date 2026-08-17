package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;

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
@Schema(description = "Response lịch sử/phiên sử dụng coupon")
public class CouponUsageResponse {

    @Schema(description = "ID phiên sử dụng coupon")
    UUID id;

    @Schema(description = "ID coupon")
    UUID couponId;

    @Schema(description = "Mã coupon", example = "TET2027")
    String couponCode;

    @Schema(description = "ID đơn hàng")
    UUID orderId;

    @Schema(description = "ID khách hàng")
    UUID customerId;

    @Schema(description = "Số tiền được giảm")
    BigDecimal discountAmount;

    @Schema(description = "Trạng thái phiên sử dụng coupon", example = "RESERVED", implementation = CouponUsageStatus.class)
    CouponUsageStatus status;

    @Schema(description = "Token giữ chỗ coupon")
    String reservationToken;

    @Schema(description = "Thời điểm giữ coupon")
    LocalDateTime reservedAt;

    @Schema(description = "Thời điểm hết hạn giữ coupon")
    LocalDateTime reservationExpiresAt;

    @Schema(description = "Thời điểm coupon được áp dụng thành công")
    LocalDateTime usedAt;

    @Schema(description = "Thời điểm coupon được nhả")
    LocalDateTime releasedAt;

    @Schema(description = "ID chi nhánh")
    UUID branchId;

    @Schema(description = "ID thu ngân/nhân viên xử lý")
    UUID cashierId;

    @Schema(description = "Kênh bán hàng", example = "POS", implementation = PromotionChannel.class)
    PromotionChannel channel;

    @Schema(description = "Đánh dấu bản ghi đã bị xóa mềm hay chưa")
    Boolean isDeleted;

    @Schema(description = "ID người tạo")
    UUID createdBy;

    @Schema(description = "ID người cập nhật gần nhất")
    UUID updatedBy;

    @Schema(description = "Thời gian tạo")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật")
    Instant updatedAt;

    @Schema(description = "Version dùng cho optimistic locking")
    Long version;
}
