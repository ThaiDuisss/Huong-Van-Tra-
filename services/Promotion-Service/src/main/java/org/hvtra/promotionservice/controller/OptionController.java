package org.hvtra.promotionservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.promotionservice.constant.ApiPath;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.hvtra.promotionservice.enums.event.EventAudience;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.event.EventType;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionChannel;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping(ApiPath.EVENTS)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(
        name = "Promotion options",
        description = "APIs used by FE to render dropdowns, filters and badges from Promotion-Service enums."
)
public class OptionController {

    @Operation(
            summary = "Get all enum options",
            description = "Return every Promotion-Service enum in one payload so FE can cache options once."
    )
    @GetMapping("/options")
    public ApiResponse<Map<String, List<String>>> getAllOptions() {
        Map<String, List<String>> options = Map.ofEntries(
                Map.entry("eventTypes", values(EventType.class)),
                Map.entry("eventAudiences", values(EventAudience.class)),
                Map.entry("eventStatuses", values(EventStatus.class)),
                Map.entry("promotionTypes", values(PromotionType.class)),
                Map.entry("promotionApplyMethods", values(PromotionApplyMethod.class)),
                Map.entry("promotionStatuses", values(PromotionStatus.class)),
                Map.entry("promotionChannels", values(PromotionChannel.class)),
                Map.entry("promotionTargetTypes", values(PromotionTargetType.class)),
                Map.entry("benefitTypes", values(BenefitType.class)),
                Map.entry("conditionTypes", values(ConditionType.class)),
                Map.entry("conditionOperators", values(ConditionOperator.class)),
                Map.entry("couponStatuses", values(CouponStatus.class)),
                Map.entry("couponScopes", values(CouponScope.class)),
                Map.entry("couponIssueReasons", values(CouponIssueReason.class)),
                Map.entry("couponUsageStatuses", values(CouponUsageStatus.class))
        );

        return ApiResponse.success("Options fetched successfully", options);
    }

    @Operation(summary = "Get event types")
    @GetMapping("/event-types")
    public ApiResponse<List<String>> getEventTypes() {
        return ApiResponse.success("Event types fetched successfully", values(EventType.class));
    }

    @Operation(summary = "Get event audiences")
    @GetMapping("/event-audiences")
    public ApiResponse<List<String>> getEventAudiences() {
        return ApiResponse.success("Event audiences fetched successfully", values(EventAudience.class));
    }

    @Operation(summary = "Get event statuses")
    @GetMapping("/event-statuses")
    public ApiResponse<List<String>> getEventStatuses() {
        return ApiResponse.success("Event statuses fetched successfully", values(EventStatus.class));
    }

    @Operation(summary = "Get promotion types")
    @GetMapping("/promotion-types")
    public ApiResponse<List<String>> getPromotionTypes() {
        return ApiResponse.success("Promotion types fetched successfully", values(PromotionType.class));
    }

    @Operation(summary = "Get promotion apply methods")
    @GetMapping("/promotion-apply-methods")
    public ApiResponse<List<String>> getPromotionApplyMethods() {
        return ApiResponse.success("Promotion apply methods fetched successfully", values(PromotionApplyMethod.class));
    }

    @Operation(summary = "Get promotion statuses")
    @GetMapping("/promotion-statuses")
    public ApiResponse<List<String>> getPromotionStatuses() {
        return ApiResponse.success("Promotion statuses fetched successfully", values(PromotionStatus.class));
    }

    @Operation(summary = "Get promotion channels")
    @GetMapping("/promotion-channels")
    public ApiResponse<List<String>> getPromotionChannels() {
        return ApiResponse.success("Promotion channels fetched successfully", values(PromotionChannel.class));
    }

    @Operation(summary = "Get promotion target types")
    @GetMapping("/promotion-target-types")
    public ApiResponse<List<String>> getPromotionTargetTypes() {
        return ApiResponse.success("Promotion target types fetched successfully", values(PromotionTargetType.class));
    }

    @Operation(summary = "Get benefit types")
    @GetMapping("/benefit-types")
    public ApiResponse<List<String>> getBenefitTypes() {
        return ApiResponse.success("Benefit types fetched successfully", values(BenefitType.class));
    }

    @Operation(summary = "Get condition types")
    @GetMapping("/condition-types")
    public ApiResponse<List<String>> getConditionTypes() {
        return ApiResponse.success("Condition types fetched successfully", values(ConditionType.class));
    }

    @Operation(summary = "Get condition operators")
    @GetMapping("/condition-operators")
    public ApiResponse<List<String>> getConditionOperators() {
        return ApiResponse.success("Condition operators fetched successfully", values(ConditionOperator.class));
    }

    @Operation(summary = "Get coupon statuses")
    @GetMapping("/coupon-statuses")
    public ApiResponse<List<String>> getCouponStatuses() {
        return ApiResponse.success("Coupon statuses fetched successfully", values(CouponStatus.class));
    }

    @Operation(summary = "Get coupon scopes")
    @GetMapping("/coupon-scopes")
    public ApiResponse<List<String>> getCouponScopes() {
        return ApiResponse.success("Coupon scopes fetched successfully", values(CouponScope.class));
    }

    @Operation(summary = "Get coupon issue reasons")
    @GetMapping("/coupon-issue-reasons")
    public ApiResponse<List<String>> getCouponIssueReasons() {
        return ApiResponse.success("Coupon issue reasons fetched successfully", values(CouponIssueReason.class));
    }

    @Operation(summary = "Get coupon usage statuses")
    @GetMapping("/coupon-usage-statuses")
    public ApiResponse<List<String>> getCouponUsageStatuses() {
        return ApiResponse.success("Coupon usage statuses fetched successfully", values(CouponUsageStatus.class));
    }

    private <E extends Enum<E>> List<String> values(Class<E> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .toList();
    }
}
