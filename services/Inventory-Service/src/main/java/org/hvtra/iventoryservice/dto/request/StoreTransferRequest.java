package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO cho chức năng Chuyển vị trí (Transfer) giữa 2 kệ.
 * - fromShelfId: kệ nguồn
 * - toShelfId:   kệ đích (phải khác fromShelfId)
 * - inventoryItemId: hàng hóa cần chuyển
 * - quantity: số lượng chuyển (> 0)
 * - note: ghi chú tùy chọn
 */
public record StoreTransferRequest(
        @NotNull UUID fromShelfId,
        @NotNull UUID toShelfId,
        @NotNull UUID inventoryItemId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity,
        String note
) {
}
