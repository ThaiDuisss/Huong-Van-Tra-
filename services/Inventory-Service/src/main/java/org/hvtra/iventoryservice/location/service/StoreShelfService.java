package org.hvtra.iventoryservice.location.service;

import org.hvtra.iventoryservice.location.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;

/**
 * Service quản lý kệ (Shelf) trong cửa hàng.
 * Mọi kệ đều thuộc kho WAREHOUSE đầu tiên (tạm thời chưa có xác thực).
 */
public interface StoreShelfService {
    PageResponse<InventoryLocationResponse> findAll(String query, InventoryStatus status, Pageable pageable);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);

    InventoryLocationResponse findById(UUID id);
    InventoryLocationResponse create(StoreShelfRequest request);
    InventoryLocationResponse update(UUID id, StoreShelfRequest request);
    InventoryLocationResponse updateStatus(UUID id, InventoryStatus status);
    void delete(UUID id);
}
