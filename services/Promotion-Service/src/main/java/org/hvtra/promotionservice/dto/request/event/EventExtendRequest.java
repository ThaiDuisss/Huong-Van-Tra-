package org.hvtra.promotionservice.dto.request.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request gia hạn thời gian kết thúc sự kiện")
public class EventExtendRequest {

    @Schema(
            description = "Thời gian kết thúc mới của sự kiện",
            example = "2027-02-15T23:59:59"
    )
    @NotNull(message = "New end time is required")
    LocalDateTime endAt;
}
