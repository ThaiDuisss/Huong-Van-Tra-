package org.hvtra.promotionservice.dto.request.promotion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(
        name = "PromotionFilterRequest",
        description = "Thong tin loc danh sach chuong trinh khuyen mai"
)
public class PromotionFilterRequest {

    @Schema(description = "Tu khoa tim kiem theo ten, mo ta hoac muc tieu promotion", example = "summer sale", maxLength = 100, nullable = true)
    @Size(max = 100, message = "Promotion keyword must not exceed 100 characters")
    String keyword;

    @Schema(description = "ID event/campaign chua promotion", example = "550e8400-e29b-41d4-a716-446655440000", nullable = true)
    UUID eventId;

    @Schema(description = "Loai promotion", example = "PERCENT_DISCOUNT", implementation = PromotionType.class, nullable = true)
    PromotionType type;

    @Schema(description = "Cach ap dung promotion", example = "COUPON_CODE", implementation = PromotionApplyMethod.class, nullable = true)
    PromotionApplyMethod applyMethod;

    @Schema(description = "Trang thai promotion", example = "ACTIVE", implementation = PromotionStatus.class, nullable = true)
    PromotionStatus status;

    @Schema(description = "Loc promotion co thoi gian bat dau tu thoi diem nay tro di", example = "2026-07-01T00:00:00", type = "string", format = "date-time", nullable = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime startFrom;

    @Schema(description = "Loc promotion co thoi gian bat dau den thoi diem nay", example = "2026-07-31T23:59:59", type = "string", format = "date-time", nullable = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime startTo;

    @Schema(description = "Loc promotion co thoi gian ket thuc tu thoi diem nay tro di", example = "2026-07-01T00:00:00", type = "string", format = "date-time", nullable = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime endFrom;

    @Schema(description = "Loc promotion co thoi gian ket thuc den thoi diem nay", example = "2026-07-31T23:59:59", type = "string", format = "date-time", nullable = true)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime endTo;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Start from time must be before or equal to start to time")
    public boolean isValidStartRange() {
        return startFrom == null || startTo == null || !startFrom.isAfter(startTo);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "End from time must be before or equal to end to time")
    public boolean isValidEndRange() {
        return endFrom == null || endTo == null || !endFrom.isAfter(endTo);
    }

}
