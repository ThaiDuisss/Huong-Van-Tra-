package org.hvtra.promotionservice.dto.request.promotion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật trạng thái khuyến mãi")
public class PromotionStatusUpdateRequest {

    @Schema(
            description = "Trạng thái mới",
            example = "DRAFT",
            allowableValues = {"DRAFT", "ACTIVE", "INACTIVE", "EXPIRED", "CANCELLED"}
    )
    @NotNull(message = "Event status is required")
    PromotionStatus status;

}
