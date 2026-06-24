package org.hvtra.promotionservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Response thông tin khuyến mãi")
public class PromotionResponse {

    @Schema(
            description = "ID của khuyến mãi",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID id;

    @Schema(
            description = "Tên khuyến mãi",
            example = "Giảm 20% cho đơn hàng từ 500K"
    )
    String name;

    @Schema(
            description = """
                    Loại khuyến mãi.
                    PERCENT_DISCOUNT: giảm theo phần trăm.
                    FIXED_DISCOUNT: giảm số tiền cố định.
                    FREE_SHIPPING: miễn phí vận chuyển.
                    BUY_X_GET_Y: mua X tặng Y.
                    FREE_PRODUCT: tặng sản phẩm.
                    """,
            example = "PERCENT_DISCOUNT"
    )
    PromotionType type;

    @Schema(
            description = """
                    Cách áp dụng khuyến mãi.
                    AUTO: hệ thống tự động áp dụng nếu đủ điều kiện.
                    COUPON_CODE: cần nhập mã khuyến mãi để áp dụng.
                    """,
            example = "COUPON_CODE"
    )
    PromotionApplyMethod applyMethod;

    @Schema(
            description = "Thời gian bắt đầu khuyến mãi",
            example = "2027-02-01T00:00:00"
    )
    LocalDateTime startAt;

    @Schema(
            description = "Thời gian kết thúc khuyến mãi",
            example = "2027-02-10T23:59:59"
    )
    LocalDateTime endAt;

    @Schema(
            description = "Có cho phép dùng chung với khuyến mãi khác hay không",
            example = "false"
    )
    Boolean stackable;

    @Schema(
            description = "Độ ưu tiên áp dụng khuyến mãi. Số càng lớn thì ưu tiên càng cao.",
            example = "10"
    )
    Integer priority;

    @Schema(
            description = """
                    Trạng thái của khuyến mãi.
                    ACTIVE: đang hoạt động.
                    INACTIVE: tạm khóa.
                    EXPIRED: đã hết hạn.
                    CANCELLED: đã bị hủy.
                    """,
            example = "ACTIVE"
    )
    PromotionStatus status;

    @Schema(
            description = "Thời gian tạo khuyến mãi",
            example = "2027-01-15T10:30:00Z"
    )
    Instant createdAt;

    @Schema(
            description = "Thời gian cập nhật khuyến mãi gần nhất",
            example = "2027-01-20T14:45:00Z"
    )
    Instant updatedAt;
}
