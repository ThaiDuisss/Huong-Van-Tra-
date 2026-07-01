package org.hvtra.promotionservice.dto.request.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.validation.annotation.ValidTimeRange;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ValidTimeRange
@Schema(description = "Request tạo mới event/campaign khuyến mãi")
public class EventCreateRequest {

    @Schema(
            description = "Tên event/campaign khuyến mãi",
            example = "Tet Holiday Campaign 2027"
    )
    @NotBlank(message = "Event name is required")
    @Size(max = 255, message = "Event name must not exceed 255 characters")
    String name;

    @Schema(
            description = "Mô tả chi tiết event/campaign",
            example = "Chiến dịch khuyến mãi Tết cho toàn bộ khách hàng"
    )
    @Size(max = 2000, message = "Event description must not exceed 2000 characters")
    String description;

    @Schema(
            description = "Thời gian bắt đầu event",
            example = "2027-02-01T00:00:00"
    )
    @NotNull(message = "Start time is required")
    @FutureOrPresent(message = "Start time must be now or in the future")
    LocalDateTime startAt;

    @Schema(
            description = "Thời gian kết thúc event",
            example = "2027-02-15T23:59:59"
    )
    @NotNull(message = "End time is required")
    @FutureOrPresent(message = "End time must be now or in the future")
    LocalDateTime endAt;

    @Schema(
            description = "Loại event/campaign",
            example = "TIME_BASED",
            allowableValues = {
                    "TIME_BASED",
                    "ORDER_AMOUNT_BASED",
                    "ORDER_COUNT_BASED",
                    "ITEM_QUANTITY_BASED",
                    "LOYALTY_POINT_BASED",
                    "MANUAL_CAMPAIGN"
            }
    )
    @NotNull(message = "Event type is required")
    EventType eventType;

    @Schema(
            description = "Nhóm khách hàng áp dụng event",
            example = "ALL_CUSTOMERS",
            allowableValues = {
                    "ALL_CUSTOMERS",
                    "CUSTOMER_SEGMENT",
                    "SPECIFIC_CUSTOMERS"
            }
    )
    @NotNull(message = "Event audience is required")
    EventAudience audience;
}