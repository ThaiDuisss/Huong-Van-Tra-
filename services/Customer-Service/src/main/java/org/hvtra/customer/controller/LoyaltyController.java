package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.LoyaltyPointRuleRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.dto.response.LoyaltyPointRuleResponse;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.service.LoyaltyAccountService;
import org.hvtra.customer.service.LoyaltyPointRuleService;
import org.hvtra.customer.service.LoyaltyTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Loyalty", description = "Quản lý tích điểm: tài khoản, lịch sử giao dịch và quy tắc tính điểm")
public class LoyaltyController {

    private final LoyaltyAccountService loyaltyAccountService;
    private final LoyaltyTransactionService loyaltyTransactionService;
    private final LoyaltyPointRuleService loyaltyPointRuleService;

    // =========================================================================
    // Loyalty Account — chỉ read, auto-tạo khi tạo Customer
    // =========================================================================

    @GetMapping(ApiPath.LOYALTY_ACCOUNTS + "/customer/{customerId}")
    @Operation(summary = "Lấy tài khoản tích điểm theo khách hàng")
    public LoyaltyAccountResponse getAccountByCustomerId(
            @Parameter(description = "ID khách hàng") @PathVariable("customerId") UUID customerId) {
        return loyaltyAccountService.getByCustomerId(customerId);
    }

    // =========================================================================
    // Loyalty Transaction — chỉ read, tạo tự động qua luồng nghiệp vụ
    // =========================================================================

    @GetMapping(ApiPath.LOYALTY_TRANSACTIONS)
    @Operation(summary = "Lấy lịch sử giao dịch tích điểm",
               description = "Truyền loyaltyAccountId để lọc theo tài khoản cụ thể")
    public List<LoyaltyTransactionResponse> getAllTransactions(
            @Parameter(description = "ID tài khoản tích điểm")
            @RequestParam(name = ApiParam.LOYALTY_ACCOUNT_ID, required = false) UUID loyaltyAccountId) {
        return loyaltyTransactionService.getAll(loyaltyAccountId);
    }

    @GetMapping(ApiPath.LOYALTY_TRANSACTIONS + "/{transactionId}")
    @Operation(summary = "Lấy chi tiết giao dịch tích điểm")
    public LoyaltyTransactionResponse getTransactionById(
            @Parameter(description = "ID giao dịch") @PathVariable("transactionId") UUID id) {
        return loyaltyTransactionService.getById(id);
    }

    // =========================================================================
    // Loyalty Point Rule — full CRUD cho admin quản lý quy tắc tích điểm
    // =========================================================================

    @GetMapping(ApiPath.LOYALTY_POINT_RULES)
    @Operation(summary = "Lấy danh sách quy tắc tích điểm")
    public List<LoyaltyPointRuleResponse> getAllPointRules() {
        return loyaltyPointRuleService.getAll();
    }

    @GetMapping(ApiPath.LOYALTY_POINT_RULES + "/{ruleId}")
    @Operation(summary = "Lấy chi tiết quy tắc tích điểm")
    public LoyaltyPointRuleResponse getPointRuleById(
            @Parameter(description = "ID quy tắc") @PathVariable("ruleId") UUID id) {
        return loyaltyPointRuleService.getById(id);
    }

    @UserAction(value = "LOYALTY_POINT_RULE_CREATE", entityType = "LoyaltyPointRule", entityId = "#result.id")
    @PostMapping(ApiPath.LOYALTY_POINT_RULES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo quy tắc tích điểm")
    public LoyaltyPointRuleResponse createPointRule(@Valid @RequestBody LoyaltyPointRuleRequest request) {
        return loyaltyPointRuleService.create(request);
    }

    @UserAction(value = "LOYALTY_POINT_RULE_UPDATE", entityType = "LoyaltyPointRule", entityId = "#id")
    @PutMapping(ApiPath.LOYALTY_POINT_RULES + "/{ruleId}")
    @Operation(summary = "Cập nhật quy tắc tích điểm")
    public LoyaltyPointRuleResponse updatePointRule(
            @Parameter(description = "ID quy tắc") @PathVariable("ruleId") UUID id,
            @Valid @RequestBody LoyaltyPointRuleRequest request) {
        return loyaltyPointRuleService.update(id, request);
    }

    @UserAction(value = "LOYALTY_POINT_RULE_DELETE", entityType = "LoyaltyPointRule", entityId = "#id")
    @DeleteMapping(ApiPath.LOYALTY_POINT_RULES + "/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm quy tắc tích điểm")
    public void deletePointRule(
            @Parameter(description = "ID quy tắc") @PathVariable("ruleId") UUID id) {
        loyaltyPointRuleService.delete(id);
    }
}
