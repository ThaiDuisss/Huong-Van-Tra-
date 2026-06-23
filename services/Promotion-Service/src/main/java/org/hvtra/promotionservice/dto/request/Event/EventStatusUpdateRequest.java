package org.hvtra.promotionservice.dto.request.Event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.event.EventStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request cập nhật trạng thái sự kiện khuyến mãi")
public class EventStatusUpdateRequest {

    @Schema(
            description = "Trạng thái mới của sự kiện",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "ENDED", "CANCELLED"}
    )
    @NotNull(message = "Event status is required")
    EventStatus status;
}
