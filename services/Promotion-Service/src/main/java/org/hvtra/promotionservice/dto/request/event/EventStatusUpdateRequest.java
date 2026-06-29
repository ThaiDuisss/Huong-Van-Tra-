package org.hvtra.promotionservice.dto.request.event;

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
@Schema(description = "Request cập nhật trạng thái event/campaign")
public class EventStatusUpdateRequest {

    @Schema(
            description = "Trạng thái mới của event",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "ENDED", "CANCELLED"}
    )
    @NotNull(message = "Event status is required")
    EventStatus status;
}