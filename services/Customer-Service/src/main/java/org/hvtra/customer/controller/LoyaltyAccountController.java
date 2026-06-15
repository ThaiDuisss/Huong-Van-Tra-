package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.LoyaltyAccountRequest;
import org.hvtra.customer.dto.response.LoyaltyAccountResponse;
import org.hvtra.customer.service.LoyaltyAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.LOYALTY_ACCOUNTS)
@Tag(name = "Loyalty Accounts", description = "CRUD tài khoản tích điểm")
public class LoyaltyAccountController {
    private final LoyaltyAccountService loyaltyAccountService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tài khoản tích điểm")
    public List<LoyaltyAccountResponse> getAll() {
        return loyaltyAccountService.getAll();
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết tài khoản tích điểm")
    public LoyaltyAccountResponse getById(@Parameter(description = "ID tài khoản tích điểm") @PathVariable(ApiParam.ID) UUID id) {
        return loyaltyAccountService.getById(id);
    }

    @GetMapping(ApiPath.CUSTOMER_ID)
    @Operation(summary = "Lấy tài khoản tích điểm theo khách hàng")
    public LoyaltyAccountResponse getByCustomerId(
            @Parameter(description = "ID khách hàng") @PathVariable(ApiParam.CUSTOMER_ID) UUID customerId) {
        return loyaltyAccountService.getByCustomerId(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản tích điểm")
    public LoyaltyAccountResponse create(@Valid @RequestBody LoyaltyAccountRequest request) {
        return loyaltyAccountService.create(request);
    }

    @PutMapping(ApiPath.ID)
    @Operation(summary = "Cập nhật tài khoản tích điểm")
    public LoyaltyAccountResponse update(@Parameter(description = "ID tài khoản tích điểm") @PathVariable(ApiParam.ID) UUID id,
                                         @Valid @RequestBody LoyaltyAccountRequest request) {
        return loyaltyAccountService.update(id, request);
    }

    @DeleteMapping(ApiPath.ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm tài khoản tích điểm")
    public void delete(@Parameter(description = "ID tài khoản tích điểm") @PathVariable(ApiParam.ID) UUID id) {
        loyaltyAccountService.delete(id);
    }
}
