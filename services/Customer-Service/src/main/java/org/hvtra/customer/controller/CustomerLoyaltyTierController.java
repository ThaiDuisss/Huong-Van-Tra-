package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.dto.response.CustomerPurchaseTransactionResponse;
import org.hvtra.customer.dto.response.CustomerTierAssignmentResponse;
import org.hvtra.customer.dto.response.CustomerTierYearlyMetricResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.dto.response.TierProgressResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.service.LoyaltyTierService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(ApiPath.CUSTOMER_BASE + "/{customerId}")
@Tag(name = "Customer Loyalty Tiers", description = "Quản lý lịch sử, chỉ số và điều chỉnh hạng khách hàng")
public class CustomerLoyaltyTierController {
    private final LoyaltyTierService loyaltyTierService;
    private final CustomerMapper mapper;

    @GetMapping(ApiPath.TIER_HISTORY)
    @Operation(summary = "Lấy lịch sử hạng khách hàng")
    public List<CustomerTierAssignmentResponse> getTierHistory(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId) {
        return loyaltyTierService.getTierHistory(customerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping(ApiPath.TIER_METRICS)
    @Operation(summary = "Lấy chỉ số xét hạng theo năm")
    public CustomerTierYearlyMetricResponse getMetric(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Parameter(description = "Năm xét hạng; không truyền thì lấy năm hiện tại")
            @RequestParam(name = ApiParam.YEAR, required = false) Integer year) {
        int metricYear = year == null ? Year.now().getValue() : year;
        return mapper.toResponse(loyaltyTierService.getMetric(customerId, metricYear));
    }

    @GetMapping(ApiPath.PURCHASE_TRANSACTIONS)
    @Operation(summary = "Lấy lịch sử giao dịch mua hàng dùng để xét hạng")
    public PageResponse<CustomerPurchaseTransactionResponse> getPurchaseTransactions(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Parameter(description = "Trang bắt đầu từ 0")
            @RequestParam(name = ApiParam.PAGE, defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số phần tử mỗi trang")
            @RequestParam(name = ApiParam.SIZE, defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(loyaltyTierService.getPurchaseTransactions(customerId, pageable).map(mapper::toResponse));
    }

    @GetMapping(ApiPath.TIER_PROGRESS)
    @Operation(summary = "Lấy tiến độ lên hạng của khách hàng")
    public TierProgressResponse getProgress(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId) {
        return loyaltyTierService.getProgress(customerId);
    }

    @UserAction(value = "CUSTOMER_TIER_ADJUST", entityType = "Customer", entityId = "#customerId")
    @PostMapping(ApiPath.TIER_ADJUSTMENTS)
    @Operation(summary = "Điều chỉnh hạng khách hàng thủ công")
    public CustomerTierAssignmentResponse adjustTier(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Valid @RequestBody ManualTierAdjustmentRequest request) {
        return mapper.toResponse(loyaltyTierService.adjustTier(customerId, request));
    }
}
