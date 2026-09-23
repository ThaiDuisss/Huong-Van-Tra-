package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.InternalSupplierLookupResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/suppliers")
public class InternalSupplierController {
    private final SupplierService service;

    public InternalSupplierController(SupplierService service) {
        this.service = service;
    }

    /** Tạo nhà cung cấp mới — dùng khi service khác (vd Inventory-Service, lúc import Excel phiếu nhập
     * kho) gặp mã nhà cung cấp chưa tồn tại và cần tự tạo mới thay vì báo lỗi. Truyền {@code code} nếu
     * người dùng tự nhập mã mong muốn; để trống thì hệ thống tự sinh mã như tạo qua UI bình thường. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(
            @RequestParam(name = "code", required = false) String code,
            @Valid @RequestBody SupplierRequest request) {
        return service.createWithCode(request, code);
    }

    @PostMapping("/batch")
    public List<SupplierResponse> getSuppliersBatch(@RequestBody List<UUID> ids) {
        return service.findByIds(ids);
    }

    /** Toàn bộ nhà cung cấp (id/code/name) — dùng để resolve theo mã lúc parse Excel ở service khác (vd Inventory-Service). */
    @GetMapping
    public List<InternalSupplierLookupResponse> getAllSuppliers() {
        return service.findAllForLookup();
    }

    /** Bổ sung danh sách nguyên liệu được phép cung cấp cho nhà cung cấp. */
    @PostMapping("/{supplierId}/ingredients")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addIngredientsToSupplier(
            @PathVariable UUID supplierId,
            @RequestBody List<UUID> ingredientIds) {
        service.addIngredients(supplierId, ingredientIds);
    }
}
