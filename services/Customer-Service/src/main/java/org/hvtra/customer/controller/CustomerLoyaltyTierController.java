package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.dto.response.CustomerPurchaseTransactionResponse;
import org.hvtra.customer.dto.response.CustomerTierAssignmentResponse;
import org.hvtra.customer.dto.response.CustomerTierYearlyMetricResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.dto.response.TierProgressResponse;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.LoyaltyTierService;
import org.hvtra.customer.service.TierQualificationService;
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

import java.math.BigDecimal;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(ApiPath.CUSTOMER_BASE + "/{customerId}")
@Tag(name = "Customer Loyalty Tiers", description = "Quản lý lịch sử, chỉ số và điều chỉnh hạng khách hàng")
public class CustomerLoyaltyTierController {
    private final LoyaltyTierService loyaltyTierService;
    private final CustomerTierAssignmentRepository assignmentRepository;
    private final CustomerTierRepository tierRepository;
    private final CustomerRepository customerRepository;
    private final TierQualificationService tierQualificationService;
    private final CustomerMapper mapper;

    @GetMapping(ApiPath.TIER_HISTORY)
    @Operation(summary = "Lấy lịch sử hạng khách hàng")
    public List<CustomerTierAssignmentResponse> getTierHistory(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId) {
        return assignmentRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping(ApiPath.TIER_METRICS)
    @Operation(summary = "Lấy chỉ số xét hạng theo năm")
    public CustomerTierYearlyMetricResponse getMetric(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Parameter(description = "Năm xét hạng; không truyền thì lấy năm hiện tại")
            @RequestParam(required = false) Integer year) {
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
        Customer customer = customerRepository.findByIdAndIsDeletedFalse(customerId)
                .orElseThrow(() -> new org.hvtra.customer.exception.CustomerNotFoundException("Customer not found"));
        CustomerTierYearlyMetric metric = loyaltyTierService.getCurrentYearMetric(customerId);
        CustomerTier qualifiedTier = metric.getQualifiedTier() == null
                ? tierQualificationService.resolveQualifiedTier(metric.getTotalSpent(), metric.getTotalOrderCount())
                : metric.getQualifiedTier();
        CustomerTier nextTier = tierRepository.findByStatusOrderByMinSpendingAscMinOrderCountAsc(org.hvtra.customer.model.enums.CustomerTierStatus.ACTIVE)
                .stream()
                .filter(tier -> isHigher(tier, qualifiedTier))
                .min(Comparator.comparing(CustomerTier::getMinSpending).thenComparing(CustomerTier::getMinOrderCount))
                .orElse(null);
        CustomerTierAssignment active = assignmentRepository.findByCustomerIdAndStatus(customerId, CustomerTierAssignmentStatus.ACTIVE).orElse(null);
        return new TierProgressResponse(
                customer.getTier() == null ? null : mapper.toResponse(customer.getTier()),
                mapper.toResponse(qualifiedTier),
                metric.getTierYear(),
                metric.getTotalSpent(),
                metric.getTotalOrderCount(),
                nextTier == null ? null : mapper.toResponse(nextTier),
                nextTier == null ? null : nonNegative(nextTier.getMinSpending().subtract(metric.getTotalSpent())),
                nextTier == null ? null : Math.max(nextTier.getMinOrderCount() - metric.getTotalOrderCount(), 0),
                active == null ? null : active.getEffectiveUntil()
        );
    }

    @PostMapping(ApiPath.TIER_ADJUSTMENTS)
    @Operation(summary = "Điều chỉnh hạng khách hàng thủ công")
    public CustomerTierAssignmentResponse adjustTier(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Valid @RequestBody ManualTierAdjustmentRequest request) {
        return mapper.toResponse(loyaltyTierService.adjustTier(customerId, request));
    }

    private boolean isHigher(CustomerTier candidate, CustomerTier current) {
        if (current == null) {
            return true;
        }
        int spendingCompare = candidate.getMinSpending().compareTo(current.getMinSpending());
        if (spendingCompare != 0) {
            return spendingCompare > 0;
        }
        return candidate.getMinOrderCount() > current.getMinOrderCount();
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
