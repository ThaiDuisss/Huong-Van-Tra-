package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.SupplierRequest;
import org.hvtra.catalog.dto.response.InternalSupplierLookupResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SupplierService {
    PageResponse<SupplierResponse> findAll(String search, Status status, UUID ingredientId, Pageable pageable);
    SupplierResponse findById(UUID id);
    List<SupplierResponse> findByIds(List<UUID> ids);
    /** Toàn bộ nhà cung cấp (id/code/name, không kèm ingredients) — phục vụ service khác tra cứu theo mã, tránh N+1. */
    List<InternalSupplierLookupResponse> findAllForLookup();

    PageResponse<LookupResponse> lookup(String search, List<UUID> ids, Pageable pageable);
    SupplierResponse create(SupplierRequest request);
    /** Tạo với mã tự chọn — dùng lúc mã đã biết trước (vd import Excel cho phép nhập tay mã nhà cung cấp mới); mã rỗng/null thì tự sinh như create() thường. */
    SupplierResponse createWithCode(SupplierRequest request, String explicitCode);
    SupplierResponse update(UUID id, SupplierRequest request);
    SupplierResponse updateStatus(UUID id, Status status);
    void addIngredients(UUID supplierId, List<UUID> ingredientIds);

    List<UUID> findIngredientIds(UUID supplierId);
    void delete(UUID id);
}
