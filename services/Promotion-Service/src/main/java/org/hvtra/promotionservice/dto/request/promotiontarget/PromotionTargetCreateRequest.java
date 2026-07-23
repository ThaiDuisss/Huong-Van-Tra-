package org.hvtra.promotionservice.dto.request.promotiontarget;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mới phạm vi áp dụng khuyến mãi")
public class PromotionTargetCreateRequest {

    @Schema(
            description = "Loại đối tượng áp dụng khuyến mãi",
            example = "PRODUCT",
            implementation = PromotionTargetType.class
    )
    @NotNull(message = "Promotion target type is required")
    PromotionTargetType targetType;

    @Schema(
            description = "ID của sản phẩm, danh mục hoặc thương hiệu được áp dụng",
            example = "550e8400-e29b-41d4-a716-446655440010"
    )
    @NotNull(message = "Promotion target ID is required")
    UUID targetId;

    @Schema(
            description = "ID batch import Excel. Nếu target được tạo thủ công thì có thể để null",
            example = "550e8400-e29b-41d4-a716-446655440099"
    )
    UUID importBatchId;

    @Schema(
            description = "Đánh dấu loại trừ khỏi khuyến mãi. true nghĩa là không áp dụng cho target này",
            example = "false"
    )
    Boolean excluded;

    @Schema(
            description = "Loại quyền lợi override riêng cho target này",
            example = "PERCENT_DISCOUNT",
            implementation = BenefitType.class
    )
    BenefitType overrideBenefitType;

    @Schema(
            description = "Giá trị giảm override riêng cho target này",
            example = "15"
    )
    @DecimalMin(value = "0.0", message = "Override value must be greater than or equal to 0")
    BigDecimal overrideValue;

    @Schema(
            description = "Mức giảm tối đa override riêng cho target này",
            example = "50000"
    )
    @DecimalMin(value = "0.0", message = "Override max discount must be greater than or equal to 0")
    BigDecimal overrideMaxDiscount;

    @Schema(
            description = "Lý do áp dụng hoặc loại trừ target này",
            example = "Nhóm sản phẩm trà cao cấp có biên lợi nhuận tốt nên được giảm riêng 15%"
    )
    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    String reason;
}
