package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.ChangeTierStatusRequest;
import org.hvtra.customer.dto.request.CustomerTierRequest;
import org.hvtra.customer.dto.response.CustomerTierCountResponse;
import org.hvtra.customer.dto.response.CustomerTierResponse;
import org.hvtra.customer.service.CustomerTierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping(ApiPath.CUSTOMER_TIERS)
@Tag(name = "Customer Tiers", description = "CRUD hạng khách hàng")
public class CustomerTierController {

    private final CustomerTierService customerTierService;

    @GetMapping
    @Operation(summary = "Lấy danh sách hạng khách hàng")
    public List<CustomerTierResponse> getAll() {
        return customerTierService.getAll();
    }

    @GetMapping("/counts")
    @Operation(summary = "Lấy danh sách hạng khách hàng kèm số lượng khách")
    public List<CustomerTierCountResponse> getCounts() {
        return customerTierService.getCounts();
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết hạng khách hàng")
    public CustomerTierResponse getById(
            @Parameter(description = "ID hạng khách hàng") @PathVariable(ApiParam.ID) UUID id) {
        return customerTierService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo hạng khách hàng")
    public CustomerTierResponse create(@Valid @RequestBody CustomerTierRequest request) {
        return customerTierService.create(request);
    }

    @PutMapping(ApiPath.ID)
    @Operation(summary = "Cập nhật hạng khách hàng (kể cả đổi status = INACTIVE để vô hiệu hóa)")
    public CustomerTierResponse update(
            @Parameter(description = "ID hạng khách hàng") @PathVariable(ApiParam.ID) UUID id,
            @Valid @RequestBody CustomerTierRequest request) {
        return customerTierService.update(id, request);
    }

    @PatchMapping(ApiPath.TIER_STATUS)
    @Operation(summary = "Đổi trạng thái hạng khách hàng")
    public void changeStatus(
            @Parameter(description = "ID hạng khách hàng") @PathVariable(ApiParam.ID) UUID id,
            @Valid @RequestBody ChangeTierStatusRequest request) {
        customerTierService.changeTierStatus(id, request);
    }
}
