package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.LoyaltyTransactionRequest;
import org.hvtra.customer.dto.response.LoyaltyTransactionResponse;
import org.hvtra.customer.service.LoyaltyTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.LOYALTY_TRANSACTIONS)
@Tag(name = "Loyalty Transactions", description = "CRUD giao dịch tích điểm")
public class LoyaltyTransactionController {
    private final LoyaltyTransactionService loyaltyTransactionService;

    @GetMapping
    @Operation(summary = "Lấy danh sách giao dịch tích điểm")
    public List<LoyaltyTransactionResponse> getAll(
            @Parameter(description = "Lọc theo ID tài khoản tích điểm") @RequestParam(name = ApiParam.LOYALTY_ACCOUNT_ID, required = false) UUID loyaltyAccountId) {
        return loyaltyTransactionService.getAll(loyaltyAccountId);
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết giao dịch tích điểm")
    public LoyaltyTransactionResponse getById(
            @Parameter(description = "ID giao dịch tích điểm") @PathVariable(ApiParam.ID) UUID id) {
        return loyaltyTransactionService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo giao dịch tích điểm")
    public LoyaltyTransactionResponse create(@Valid @RequestBody LoyaltyTransactionRequest request) {
        return loyaltyTransactionService.create(request);
    }

    @PutMapping(ApiPath.ID)
    @Operation(summary = "Cập nhật giao dịch tích điểm")
    public LoyaltyTransactionResponse update(
            @Parameter(description = "ID giao dịch tích điểm") @PathVariable(ApiParam.ID) UUID id,
            @Valid @RequestBody LoyaltyTransactionRequest request) {
        return loyaltyTransactionService.update(id, request);
    }

    @DeleteMapping(ApiPath.ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm giao dịch tích điểm")
    public void delete(@Parameter(description = "ID giao dịch tích điểm") @PathVariable(ApiParam.ID) UUID id) {
        loyaltyTransactionService.delete(id);
    }
}
