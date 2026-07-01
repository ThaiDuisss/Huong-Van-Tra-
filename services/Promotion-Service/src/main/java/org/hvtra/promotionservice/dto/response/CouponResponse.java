package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;

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
@Schema(description = "Response thông tin coupon")
public class CouponResponse {

    @Schema(description = "ID coupon", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID id;

    @Schema(description = "ID khuyến mãi chứa coupon", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(description = "Tên khuyến mãi chứa coupon", example = "Giảm 20% cho đơn hàng từ 500K")
    String promotionName;

    @Schema(description = "Phạm vi sử dụng coupon", example = "PUBLIC")
    CouponScope scope;

    @Schema(description = "ID khách hàng được gán coupon nếu là mã cá nhân")
    UUID customerId;

    @Schema(description = "Mã coupon", example = "TET2027")
    String code;

    @Schema(description = "Lý do phát hành coupon", example = "MANUAL")
    CouponIssueReason issueReason;

    @Schema(description = "ID đơn hàng nguồn phát sinh coupon")
    UUID sourceOrderId;

    @Schema(description = "Thời điểm phát hành coupon")
    LocalDateTime issuedAt;

    @Schema(description = "Thời điểm khách nhận/claim coupon")
    LocalDateTime claimedAt;

    @Schema(description = "Số lần coupon được phép sử dụng", example = "1")
    Integer usageLimit;

    @Schema(description = "Số lần coupon đã sử dụng", example = "0")
    Integer usedCount;

    @Schema(description = "Thời điểm hết hạn coupon")
    LocalDateTime expiresAt;

    @Schema(description = "Trạng thái coupon", example = "ACTIVE")
    CouponStatus status;

    @Schema(description = "Snapshot tên khuyến mãi tại thời điểm phát hành")
    String snapshotPromotionName;

    @Schema(description = "Snapshot loại quyền lợi tại thời điểm phát hành")
    BenefitType snapshotBenefitType;

    @Schema(description = "Snapshot giá trị giảm tại thời điểm phát hành")
    BigDecimal snapshotDiscountValue;

    @Schema(description = "Snapshot mức giảm tối đa tại thời điểm phát hành")
    BigDecimal snapshotMaxDiscount;

    @Schema(description = "Đánh dấu bản ghi đã bị xóa mềm hay chưa")
    Boolean isDeleted;

    @Schema(description = "ID người tạo")
    UUID createdBy;

    @Schema(description = "ID người cập nhật gần nhất")
    UUID updatedBy;

    @Schema(description = "Thời gian tạo")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất")
    Instant updatedAt;

    @Schema(description = "Version dùng cho optimistic locking")
    Long version;
}