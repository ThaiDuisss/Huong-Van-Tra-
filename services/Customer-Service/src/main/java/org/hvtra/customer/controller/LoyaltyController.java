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
import org.hvtra.customer.dto.request.LoyaltyAdjustmentRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.service.LoyaltyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(ApiPath.CUSTOMER_BASE + ApiPath.CUSTOMER_ID)
@Tag(name = "Loyalty", description = "Điểm tích lũy của khách: lịch sử và điều chỉnh thủ công")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping(ApiPath.LOYALTY_TRANSACTIONS)
    @Operation(summary = "Lịch sử điểm của khách", description = "Mới nhất trước.")
    public PageResponse<LoyaltyTransactionResponse> getTransactions(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @RequestParam(name = ApiParam.PAGE, required = false, defaultValue = "0") @Min(0) Integer page,
            @RequestParam(name = ApiParam.SIZE, required = false, defaultValue = "10") @Min(1) @Max(100) Integer size
    ) {
        return loyaltyService.getTransactions(
                customerId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")))
        );
    }

    @UserAction(value = "LOYALTY_POINT_ADJUST", entityType = "Customer", entityId = "#customerId", detail = "#request.points")
    @PostMapping(ApiPath.LOYALTY_ADJUSTMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Điều chỉnh điểm thủ công", description = "points dương là cộng, âm là trừ; không được trừ quá số dư.")
    public LoyaltyTransactionResponse adjust(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId,
            @Valid @RequestBody LoyaltyAdjustmentRequest request
    ) {
        return loyaltyService.adjust(customerId, request);
    }
}
