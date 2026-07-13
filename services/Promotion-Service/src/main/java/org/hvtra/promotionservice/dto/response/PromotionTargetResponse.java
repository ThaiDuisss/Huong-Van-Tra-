package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;

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
@Schema(description = "Response phạm vi áp dụng khuyến mãi")
public class PromotionTargetResponse {

    @Schema(description = "ID target khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID id;

    @Schema(description = "ID khuyến mãi chứa target này", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(
            description = """
                    Loại đối tượng áp dụng.
                    PRODUCT: sản phẩm cụ thể.
                    CATEGORY: danh mục sản phẩm.
                    BRAND: thương hiệu hoặc nhà cung cấp.
                    """,
            example = "PRODUCT",
            implementation = PromotionTargetType.class
    )
    PromotionTargetType targetType;

    @Schema(description = "ID của sản phẩm, danh mục hoặc thương hiệu", example = "550e8400-e29b-41d4-a716-446655440010")
    UUID targetId;

    @Schema(description = "ID batch import Excel nếu target được tạo từ file import", example = "550e8400-e29b-41d4-a716-446655440099")
    UUID importBatchId;

    @Schema(description = "Có loại trừ target này khỏi khuyến mãi hay không", example = "false")
    Boolean excluded;

    @Schema(description = "Loại quyền lợi override riêng cho target này", example = "PERCENT_DISCOUNT", implementation = BenefitType.class)
    BenefitType overrideBenefitType;

    @Schema(description = "Giá trị giảm override riêng cho target này", example = "15")
    BigDecimal overrideValue;

    @Schema(description = "Mức giảm tối đa override riêng cho target này", example = "50000")
    BigDecimal overrideMaxDiscount;

    @Schema(description = "Lý do áp dụng hoặc loại trừ target này")
    String reason;

    @Schema(description = "Đánh dấu bản ghi đã bị xóa mềm hay chưa", example = "false")
    Boolean isDeleted;

    @Schema(description = "ID người tạo")
    UUID createdBy;

    @Schema(description = "ID người cập nhật gần nhất")
    UUID updatedBy;

    @Schema(description = "Thời gian tạo", example = "2027-01-15T10:30:00Z")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất", example = "2027-01-20T14:45:00Z")
    Instant updatedAt;

    @Schema(description = "Version dùng cho optimistic locking", example = "0")
    Long version;
}
