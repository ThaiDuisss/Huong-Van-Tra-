package org.hvtra.promotionservice.dto.request.promotionbenefit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mới quyền lợi/hành động giảm giá của khuyến mãi")
public class PromotionBenefitCreateRequest {

    @Schema(
            description = "Loại quyền lợi khuyến mãi",
            example = "PERCENT_DISCOUNT",
            implementation = BenefitType.class
    )
    @NotNull(message = "Promotion benefit type is required")
    BenefitType benefitType;

    @Schema(
            description = "Giá trị giảm. Với PERCENT_DISCOUNT là phần trăm, FIXED_DISCOUNT là số tiền. Với FREE_SHIPPING có thể để 0.",
            example = "20"
    )
    @NotNull(message = "Promotion benefit value is required")
    @DecimalMin(value = "0.0", message = "Promotion benefit value must be greater than or equal to 0")
    BigDecimal value;

    @Schema(
            description = "Mức giảm tối đa, thường áp dụng cho giảm theo phần trăm",
            example = "100000"
    )
    @DecimalMin(value = "0.0", message = "Promotion max discount must be greater than or equal to 0")
    BigDecimal maxDiscount;

    @Schema(
            description = "ID sản phẩm được tặng. Dùng cho FREE_PRODUCT hoặc BUY_X_GET_Y",
            example = "550e8400-e29b-41d4-a716-446655440010"
    )
    UUID giftProductId;

    @Schema(description = "Số lượng cần mua. Dùng cho BUY_X_GET_Y", example = "2")
    @Min(value = 1, message = "Buy quantity must be greater than or equal to 1")
    Integer buyQuantity;

    @Schema(description = "Số lượng được tặng. Dùng cho BUY_X_GET_Y hoặc FREE_PRODUCT", example = "1")
    @Min(value = 1, message = "Get quantity must be greater than or equal to 1")
    Integer getQuantity;

    @Schema(
            description = "Lý do cấu hình mức giảm, dùng để giải thích tại sao chọn con số này",
            example = "Sản phẩm có biên lợi nhuận cao, giảm 20% vẫn đảm bảo lợi nhuận tối thiểu"
    )
    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    String reason;

    @Schema(description = "Biên lợi nhuận tối thiểu cho phép", example = "15.00")
    @DecimalMin(value = "0.0", message = "Minimum margin percent must be greater than or equal to 0")
    BigDecimal minMarginPercent;

    @Schema(description = "Biên lợi nhuận kỳ vọng sau khi áp dụng khuyến mãi", example = "25.00")
    @DecimalMin(value = "0.0", message = "Expected margin percent must be greater than or equal to 0")
    BigDecimal expectedMarginPercent;
}
