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
@Schema(description = "Request cập nhật quyền lợi/hành động giảm giá của khuyến mãi")
public class PromotionBenefitUpdateRequest {

    @Schema(description = "Loại quyền lợi khuyến mãi", example = "PERCENT_DISCOUNT", implementation = BenefitType.class)
    @NotNull(message = "Promotion benefit type is required")
    BenefitType benefitType;

    @Schema(description = "Giá trị giảm", example = "15")
    @NotNull(message = "Promotion benefit value is required")
    @DecimalMin(value = "0.0", message = "Promotion benefit value must be greater than or equal to 0")
    BigDecimal value;

    @Schema(description = "Mức giảm tối đa", example = "80000")
    @DecimalMin(value = "0.0", message = "Promotion max discount must be greater than or equal to 0")
    BigDecimal maxDiscount;

    @Schema(description = "ID sản phẩm được tặng", example = "550e8400-e29b-41d4-a716-446655440010")
    UUID giftProductId;

    @Schema(description = "Số lượng cần mua", example = "2")
    @Min(value = 1, message = "Buy quantity must be greater than or equal to 1")
    Integer buyQuantity;

    @Schema(description = "Số lượng được tặng", example = "1")
    @Min(value = 1, message = "Get quantity must be greater than or equal to 1")
    Integer getQuantity;

    @Schema(description = "Lý do cấu hình mức giảm", example = "Giảm còn 15% để đảm bảo biên lợi nhuận")
    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    String reason;

    @Schema(description = "Biên lợi nhuận tối thiểu cho phép", example = "15.00")
    @DecimalMin(value = "0.0", message = "Minimum margin percent must be greater than or equal to 0")
    BigDecimal minMarginPercent;

    @Schema(description = "Biên lợi nhuận kỳ vọng sau khi áp dụng khuyến mãi", example = "25.00")
    @DecimalMin(value = "0.0", message = "Expected margin percent must be greater than or equal to 0")
    BigDecimal expectedMarginPercent;
}
