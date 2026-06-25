package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thông tin quyền lợi khuyến mãi")
public class PromotionBenefitResponse {

    @Schema(
            description = "ID quyền lợi khuyến mãi",
            example = "550e8400-e29b-41d4-a716-446655440001"
    )
    UUID id;

    @Schema(
            description = "ID khuyến mãi",
            example = "550e8400-e29b-41d4-a716-446655440000"
    )
    UUID promotionId;

    @Schema(
            description = "Loại quyền lợi khuyến mãi",
            example = "PERCENT_DISCOUNT"
    )
    BenefitType benefitType;

    @Schema(
            description = "Giá trị giảm",
            example = "20"
    )
    BigDecimal value;

    @Schema(
            description = "Mức giảm tối đa",
            example = "100000"
    )
    BigDecimal maxDiscount;

    @Schema(
            description = "Thời gian tạo"
    )
    Instant createdAt;

    @Schema(
            description = "Thời gian cập nhật gần nhất"
    )
    Instant updatedAt;
}
