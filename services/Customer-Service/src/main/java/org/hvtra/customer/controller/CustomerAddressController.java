package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.CustomerAddressRequest;
import org.hvtra.customer.dto.response.CustomerAddressResponse;
import org.hvtra.customer.service.CustomerAddressService;
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
@RequestMapping(ApiPath.CUSTOMER_ADDRESSES)
@Tag(name = "Customer Addresses", description = "CRUD địa chỉ khách hàng")
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;

    @GetMapping
    @Operation(summary = "Lấy danh sách địa chỉ khách hàng")
    public List<CustomerAddressResponse> getAll(
            @Parameter(description = "Lọc theo ID khách hàng")
            @RequestParam(name = ApiParam.CUSTOMER_ID, required = false) UUID customerId) {
        return customerAddressService.getAll(customerId);
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết địa chỉ khách hàng")
    public CustomerAddressResponse getById(
            @Parameter(description = "ID địa chỉ") @PathVariable(ApiParam.ID) UUID id) {
        return customerAddressService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo địa chỉ khách hàng")
    public CustomerAddressResponse create(@Valid @RequestBody CustomerAddressRequest request) {
        return customerAddressService.create(request);
    }

    @PutMapping(ApiPath.ID)
    @Operation(summary = "Cập nhật địa chỉ khách hàng")
    public CustomerAddressResponse update(
            @Parameter(description = "ID địa chỉ") @PathVariable(ApiParam.ID) UUID id,
            @Valid @RequestBody CustomerAddressRequest request) {
        return customerAddressService.update(id, request);
    }

    @DeleteMapping(ApiPath.ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm địa chỉ khách hàng")
    public void delete(
            @Parameter(description = "ID địa chỉ") @PathVariable(ApiParam.ID) UUID id) {
        customerAddressService.delete(id);
    }
}
