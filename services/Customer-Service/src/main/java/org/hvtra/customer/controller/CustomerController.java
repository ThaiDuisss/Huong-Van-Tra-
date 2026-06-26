package org.hvtra.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.hvtra.customer.client.OrderClient;
import org.hvtra.customer.constant.ApiParam;
import org.hvtra.customer.constant.ApiPath;
import org.hvtra.customer.dto.request.CustomerFilterRequest;
import org.hvtra.customer.dto.request.CustomerRequest;
import org.hvtra.customer.dto.response.CustomerResponse;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.service.CustomerService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(ApiPath.CUSTOMER_BASE)
@Tag(name = "Customers", description = "CRUD khách hàng")
public class CustomerController {
    private final CustomerService customerService;
    private final OrderClient orderClient;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách khách hàng",
            description = "Có thể kết hợp nhiều bộ lọc. Không truyền page và size thì trả toàn bộ kết quả."
    )
    public PageResponse<CustomerResponse> getAll(
            @ParameterObject @Valid @ModelAttribute CustomerFilterRequest filter,
            @Parameter(description = "Trang bắt đầu từ 0; không truyền page và size để lấy tất cả")
            @RequestParam(name = ApiParam.PAGE, required = false) @Min(0) Integer page,
            @Parameter(description = "Số phần tử mỗi trang; không truyền page và size để lấy tất cả")
            @RequestParam(name = ApiParam.SIZE, required = false) @Min(1) @Max(200) Integer size,
            @Parameter(description = "Kiểu sắp xếp của màn hình danh sách khách hàng")
            @RequestParam(name = "sort", required = false, defaultValue = "lastPurchaseDesc") String sort) {
        return customerService.findAll(filter, toPageable(page, size, sort));
    }

    private Pageable toPageable(Integer page, Integer size, String sortValue) {
        Sort sort = customerSort(sortValue);
        if (page == null && size == null) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(
                page == null ? 0 : page,
                size == null ? 20 : size,
                sort
        );
    }

    private Sort customerSort(String value) {
        Sort.Order primary = switch (value) {
            case "lastPurchaseAsc" -> Sort.Order.asc("lastPurchaseDate");
            case "createdDesc" -> Sort.Order.desc("createdAt");
            case "createdAsc" -> Sort.Order.asc("createdAt");
            case "nameAsc" -> Sort.Order.asc("fullName");
            case "nameDesc" -> Sort.Order.desc("fullName");
            default -> Sort.Order.desc("lastPurchaseDate");
        };
        return Sort.by(primary, Sort.Order.asc("fullName"));
    }

    @GetMapping(ApiPath.ID)
    @Operation(summary = "Lấy chi tiết khách hàng")
    public CustomerResponse getById(
            @PathVariable(ApiParam.ID) @Parameter(description = "ID khách hàng") UUID id
    ) {
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
    public CustomerResponse update(
            @PathVariable(ApiParam.ID) @Parameter(description = "ID khách hàng") UUID id,
            @Valid @RequestBody CustomerRequest request
    ) {
        return customerService.update(id, request);
    }

    @PutMapping(ApiPath.PURCHASE_UPDATE)
    @Operation(summary = "Cập nhật thông tin mua hàng của khách hàng từ đơn hàng")
    public CustomerResponse updatePurchase(
            @PathVariable("orderId") @Parameter(description = "ID đơn hàng") UUID orderId
    ) {
        OrderClient.OrderResponse order = orderClient.getOrderById(orderId);
        return customerService.updatePurchase(order.customerId(), order.finalAmount());
    }

    @DeleteMapping(ApiPath.ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm khách hàng")
    public void delete(@PathVariable(ApiParam.ID) @Parameter(description = "ID khách hàng") UUID id) {
        customerService.delete(id);
    }


}
