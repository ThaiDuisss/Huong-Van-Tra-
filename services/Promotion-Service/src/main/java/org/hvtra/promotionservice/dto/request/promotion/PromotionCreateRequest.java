package org.hvtra.promotionservice.dto.request.promotion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.hvtra.promotionservice.validation.annotation.ValidTimeRange;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ValidTimeRange
@Schema(description = "Request tạo mới promotion")
public class PromotionCreateRequest {

    @Schema(
            description = "ID của event/campaign mà promotion thuộc về",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID eventId;

    @Schema(description = "Tên promotion", example = "Giảm 20% cho đơn hàng từ 500K")
    @NotBlank(message = "Promotion name is required")
    @Size(max = 255, message = "Promotion name must not exceed 255 characters")
    String name;

    @Schema(description = "Mô tả promotion", example = "Áp dụng cho khách mua online trong dịp Tết")
    @Size(max = 2000, message = "Promotion description must not exceed 2000 characters")
    String description;

    @Schema(description = "Mục tiêu kinh doanh của promotion", example = "Tăng doanh số nhóm trà cao cấp trong mùa Tết")
    @Size(max = 2000, message = "Promotion objective must not exceed 2000 characters")
    String objective;

    @Schema(
            description = "Loại promotion",
            example = "PERCENT_DISCOUNT",
            allowableValues = {
                    "PERCENT_DISCOUNT",
                    "FIXED_DISCOUNT",
                    "FREE_SHIPPING",
                    "BUY_X_GET_Y",
                    "FREE_PRODUCT"
            }
    )
    @NotNull(message = "Promotion type is required")
    PromotionType type;

    @Schema(
            description = "Cách áp dụng promotion",
            example = "COUPON_CODE",
            allowableValues = {
                    "AUTO",
                    "COUPON_CODE",
                    "DIRECT_DISCOUNT"
            }
    )
    @NotNull(message = "Promotion apply method is required")
    PromotionApplyMethod applyMethod;

    @Schema(description = "Thời gian bắt đầu promotion", example = "2027-02-01T00:00:00")
    @NotNull(message = "Promotion start time is required")
    @FutureOrPresent(message = "Promotion start time must be now or in the future")
    LocalDateTime startAt;

    @Schema(description = "Thời gian kết thúc promotion", example = "2027-02-10T23:59:59")
    @NotNull(message = "Promotion end time is required")
    LocalDateTime endAt;

    @Schema(description = "Promotion có được dùng chung với promotion khác không", example = "false")
    Boolean stackable;

    @Schema(description = "Độ ưu tiên. Số càng lớn thì ưu tiên càng cao.", example = "10")
    @Min(value = 0, message = "Promotion priority must be greater than or equal to 0")
    Integer priority;
}