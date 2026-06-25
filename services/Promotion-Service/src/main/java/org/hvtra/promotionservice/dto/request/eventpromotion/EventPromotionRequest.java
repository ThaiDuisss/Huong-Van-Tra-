package org.hvtra.promotionservice.dto.request.eventpromotion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request gắn hoặc gỡ danh sách khuyến mãi khỏi sự kiện")
public class EventPromotionRequest {

    @Schema(
            description = "Danh sách ID khuyến mãi",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\"]"
    )
    @NotEmpty(message = "Promotion ids must not be empty")
    List<UUID> promotionIds;
}
