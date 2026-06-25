package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;

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
@Schema(description = "Response thông tin mã giảm giá")
public class CouponResponse {

    @Schema(description = "ID mã giảm giá", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;

    @Schema(description = "ID khuyến mãi mà mã giảm giá thuộc về", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(description = "Tên khuyến mãi", example = "Giảm 20k cho đơn từ 100k")
    String promotionName;

    @Schema(description = "ID khách hàng được gán mã, null nếu mã dùng chung", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID customerId;

    @Schema(description = "Mã giảm giá", example = "TET2027")
    String code;

    @Schema(description = "Tổng số lần mã được sử dụng", example = "100")
    Integer usageLimit;

    @Schema(description = "Số lần mã đã được sử dụng", example = "10")
    Integer usedCount;

    @Schema(description = "Số lượt còn lại", example = "90")
    Integer remainingUsage;

    @Schema(description = "Thời gian hết hạn", example = "2027-02-10T23:59:59")
    LocalDateTime expiresAt;

    @Schema(description = "Trạng thái mã giảm giá", example = "DRAFT")
    CouponStatus status;

    @Schema(description = "Đã bị xóa mềm hay chưa", example = "false")
    Boolean isDeleted;

    @Schema(description = "Thời gian tạo")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất")
    Instant updatedAt;
}
