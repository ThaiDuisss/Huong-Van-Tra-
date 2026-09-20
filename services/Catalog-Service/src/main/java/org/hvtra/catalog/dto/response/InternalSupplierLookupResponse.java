package org.hvtra.catalog.dto.response;

import java.util.UUID;

/** DTO tối giản cho service khác (vd Inventory-Service) tra cứu toàn bộ nhà cung cấp theo mã — không kèm ingredients để tránh N+1. */
public record InternalSupplierLookupResponse(
        UUID id,
        String code,
        String name
) {
}
