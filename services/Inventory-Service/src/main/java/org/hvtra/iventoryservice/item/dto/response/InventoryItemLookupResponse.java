package org.hvtra.iventoryservice.item.dto.response;

import java.util.UUID;

/** Mặt hàng tồn kho rút gọn cho dropdown chọn nguyên liệu (id = inventory_items.id). */
public record InventoryItemLookupResponse(
        UUID id,
        String name,
        String code,
        String unitName
) {
}
