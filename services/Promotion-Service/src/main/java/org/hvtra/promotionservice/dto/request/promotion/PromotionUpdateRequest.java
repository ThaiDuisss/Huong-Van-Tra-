package org.hvtra.promotionservice.dto.request.promotion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.validation.annotation.ValidTimeRange;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật khuyến mãi độc lập")
@ValidTimeRange
public class PromotionUpdateRequest {
    @Schema(
            description = "Tên khuyến mãi",
            example = "Giảm 20% cho đơn hàng từ 500K"
    )
    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Promotion name must not exceed 255 characters")
    String name;

    @Schema(
            description = "Loại khuyến mãi",
            example = "PERCENT_DISCOUNT"
    )
    @NotNull(message = "Promotion type is required")
    PromotionType type;

    @Schema(
            description = "Cách áp dụng khuyến mãi",
            example = "COUPON_CODE"
    )
    @NotNull(message = "Promotion apply method is required")
    PromotionApplyMethod applyMethod;

    @Schema(
            description = "Thời gian bắt đầu khuyến mãi",
            example = "2027-02-01T00:00:00"
    )
    @NotNull(message = "Promotion start time is required")
    @FutureOrPresent(message = "Promotion start time must be in present or future")
    LocalDateTime startAt;

    @Schema(
            description = "Thời gian kết thúc khuyến mãi",
            example = "2027-02-10T23:59:59"
    )
    @NotNull(message = "Promotion end time is required")
    LocalDateTime endAt;

    @Schema(
            description = "Có được dùng chung với khuyến mãi khác hay không",
            example = "false"
    )
    Boolean stackable;

    @Schema(
            description = "Độ ưu tiên áp dụng khuyến mãi. Số càng lớn thì ưu tiên càng cao.",
            example = "0"
    )
    @Min(value = 0, message = "Promotion priority must be greater than or equal to 0")
    Integer priority;
}
