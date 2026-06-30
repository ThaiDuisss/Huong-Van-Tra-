package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.enums.InventoryStatus;

/**
 * Request DTO dành riêng cho màn hình Quản lý kệ (Store Shelf).
 * Frontend chỉ gửi tên và trạng thái.
 * Backend tự động bổ sung: type = SHELF, parentLocationId = kho đầu tiên.
 */
public record StoreShelfRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull InventoryStatus status
) {
}
