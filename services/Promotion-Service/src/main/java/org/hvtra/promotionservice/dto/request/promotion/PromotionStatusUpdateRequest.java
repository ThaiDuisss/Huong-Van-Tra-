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
@Schema(description = "Request cập nhật trạng thái promotion")
public class PromotionStatusUpdateRequest {

    @Schema(
            description = "Trạng thái mới của promotion",
            example = "ACTIVE",
            allowableValues = {
                    "DRAFT",
                    "SCHEDULED",
                    "ACTIVE",
                    "INACTIVE",
                    "EXPIRED",
                    "CANCELLED"
            }
    )
    @NotNull(message = "Promotion status is required")
    PromotionStatus status;
}