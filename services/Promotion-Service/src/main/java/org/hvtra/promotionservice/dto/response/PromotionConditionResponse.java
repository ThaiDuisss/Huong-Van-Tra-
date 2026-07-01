package org.hvtra.promotionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response điều kiện áp dụng khuyến mãi")
public class PromotionConditionResponse {

    @Schema(description = "ID điều kiện khuyến mãi", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID id;

    @Schema(description = "ID khuyến mãi chứa điều kiện này", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID promotionId;

    @Schema(
            description = """
                    Loại điều kiện áp dụng khuyến mãi.
                    MIN_ORDER_AMOUNT: giá trị đơn hàng tối thiểu.
                    MAX_ORDER_AMOUNT: giá trị đơn hàng tối đa.
                    MIN_ITEM_QUANTITY: tổng số lượng sản phẩm tối thiểu.
                    MIN_ORDER_COUNT: khách đã mua đủ số đơn.
                    MIN_TOTAL_SPENT: tổng chi tiêu tích lũy tối thiểu.
                    MIN_LOYALTY_POINT: điểm tích lũy tối thiểu.
                    FIRST_PURCHASE: khách mua lần đầu.
                    CUSTOMER_TIER: hạng khách hàng.
                    CUSTOMER_ID: khách hàng cụ thể.
                    CHANNEL: kênh bán hàng.
                    BRANCH: chi nhánh.
                    PAYMENT_METHOD: phương thức thanh toán.
                    """,
            example = "MIN_ORDER_AMOUNT"
    )
    ConditionType conditionType;

    @Schema(
            description = """
                    Toán tử so sánh điều kiện.
                    EQ: bằng.
                    NE: khác.
                    GT: lớn hơn.
                    GTE: lớn hơn hoặc bằng.
                    LT: nhỏ hơn.
                    LTE: nhỏ hơn hoặc bằng.
                    IN: nằm trong danh sách.
                    NOT_IN: không nằm trong danh sách.
                    """,
            example = "GTE"
    )
    ConditionOperator operator;

    @Schema(
            description = "Giá trị điều kiện ở dạng JSON string",
            example = "{\"amount\":500000}"
    )
    String conditionValue;

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