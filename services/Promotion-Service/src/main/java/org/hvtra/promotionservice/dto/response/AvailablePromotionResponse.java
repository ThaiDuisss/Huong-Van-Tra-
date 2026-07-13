package org.hvtra.promotionservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePromotionResponse {

    UUID promotionId;

    String promotionName;

    @Schema(description = "Cách áp dụng khuyến mãi", example = "AUTO", implementation = PromotionApplyMethod.class)
    PromotionApplyMethod applyMethod;

    @Schema(description = "Loại quyền lợi khuyến mãi", example = "PERCENT_DISCOUNT", implementation = BenefitType.class)
    BenefitType benefitType;

    BigDecimal estimatedDiscount;

    Boolean stackable;

    @Schema(description = "Khuyen mai co can ma coupon de reserve truoc payment hay khong", example = "true")
    Boolean requiresCoupon;

    @Schema(description = "ID coupon phu hop voi khuyen mai va don hien tai")
    UUID couponId;

    @Schema(description = "Ma coupon can gui sang endpoint reserve", example = "TET2027")
    String couponCode;

    @Schema(description = "Pham vi coupon", example = "PUBLIC", implementation = CouponScope.class)
    CouponScope couponScope;

    @Schema(description = "Thoi diem coupon het han")
    LocalDateTime couponExpiresAt;
}
