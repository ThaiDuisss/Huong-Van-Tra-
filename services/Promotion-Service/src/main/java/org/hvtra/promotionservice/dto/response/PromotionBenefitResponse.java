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
@Schema(description = "Response quyền lợi/hành động giảm giá của khuyến mãi")
public class PromotionBenefitResponse {

    @Schema(description = "ID quyền lợi khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID id;

    @Schema(description = "ID khuyến mãi chứa quyền lợi này", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(
            description = """
                    Loại quyền lợi khuyến mãi.
                    PERCENT_DISCOUNT: giảm theo phần trăm.
                    FIXED_DISCOUNT: giảm số tiền cố định.
                    FREE_SHIPPING: miễn phí vận chuyển.
                    FREE_PRODUCT: tặng sản phẩm.
                    BUY_X_GET_Y: mua X tặng Y.
                    """,
            example = "PERCENT_DISCOUNT",
            implementation = BenefitType.class
    )
    BenefitType benefitType;

    @Schema(description = "Giá trị giảm. Với PERCENT_DISCOUNT là %, FIXED_DISCOUNT là số tiền.", example = "20")
    BigDecimal value;

    @Schema(description = "Mức giảm tối đa, thường áp dụng cho giảm theo phần trăm", example = "100000")
    BigDecimal maxDiscount;

    @Schema(description = "ID sản phẩm được tặng. Dùng cho FREE_PRODUCT hoặc BUY_X_GET_Y", example = "550e8400-e29b-41d4-a716-446655440010")
    UUID giftProductId;

    @Schema(description = "Số lượng cần mua. Dùng cho BUY_X_GET_Y", example = "2")
    Integer buyQuantity;

    @Schema(description = "Số lượng được tặng. Dùng cho BUY_X_GET_Y hoặc FREE_PRODUCT", example = "1")
    Integer getQuantity;

    @Schema(description = "Lý do cấu hình mức giảm", example = "Sản phẩm có biên lợi nhuận cao, giảm 20% vẫn đảm bảo lợi nhuận tối thiểu")
    String reason;

    @Schema(description = "Biên lợi nhuận tối thiểu cho phép", example = "15.00")
    BigDecimal minMarginPercent;

    @Schema(description = "Biên lợi nhuận kỳ vọng sau khi áp dụng khuyến mãi", example = "25.00")
    BigDecimal expectedMarginPercent;

    @Schema(description = "Giá trị được đề xuất bởi AI hoặc hệ thống gợi ý", example = "18.00")
    BigDecimal recommendedValue;

    @Schema(description = "Lý do đề xuất bởi AI hoặc hệ thống gợi ý", example = "Dựa trên biên lợi nhuận và tốc độ bán của sản phẩm")
    String recommendReason;

    @Schema(description = "Độ tin cậy của đề xuất", example = "92.50")
    BigDecimal confidenceScore;

    @Schema(description = "Quyền lợi này có phải do AI/hệ thống gợi ý hay không", example = "false")
    Boolean aiRecommended;

    @Schema(description = "Đánh dấu bản ghi đã bị xóa mềm hay chưa", example = "false")
    Boolean isDeleted;

    @Schema(description = "ID người tạo", example = "550e8400-e29b-41d4-a716-446655440010")
    UUID createdBy;

    @Schema(description = "ID người cập nhật gần nhất", example = "550e8400-e29b-41d4-a716-446655440011")
    UUID updatedBy;

    @Schema(description = "Thời gian tạo", example = "2027-01-15T10:30:00Z")
    Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất", example = "2027-01-20T14:45:00Z")
    Instant updatedAt;

    @Schema(description = "Version dùng cho optimistic locking", example = "0")
    Long version;
}
