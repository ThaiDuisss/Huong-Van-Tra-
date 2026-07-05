package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import org.hvtra.iventoryservice.dto.response.LookupResponse;

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
}
