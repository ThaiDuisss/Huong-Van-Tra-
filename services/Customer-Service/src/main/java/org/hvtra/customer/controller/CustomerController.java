package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.service.CustomerService;
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
@RequestMapping(ApiPath.CUSTOMER_BASE)
@Tag(name = "Customers", description = "CRUD khách hàng")
public class CustomerController {
    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "Lấy danh sách khách hàng")
    public List<CustomerResponse> getAll(
            @Parameter(description = "Tìm theo tên, số điện thoại hoặc mã khách hàng")
            @RequestParam(name = ApiParam.KEYWORD, required = false) String keyword) {
        return customerService.search(keyword);
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết khách hàng")
    public CustomerResponse getById(@PathVariable @Parameter(description = "ID khách hàng") UUID id) {
        return customerService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo khách hàng")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping(ApiPath.ID)
    @Operation(summary = "Cập nhật khách hàng")
    public CustomerResponse update(@PathVariable @Parameter(description = "ID khách hàng") UUID id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping(ApiPath.ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm khách hàng")
    public void delete(@PathVariable @Parameter(description = "ID khách hàng") UUID id) {
        customerService.delete(id);
    }
}
