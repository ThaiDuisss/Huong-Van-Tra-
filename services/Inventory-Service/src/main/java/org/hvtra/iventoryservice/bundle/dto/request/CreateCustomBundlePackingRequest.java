package org.hvtra.iventoryservice.bundle.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Order-Service gọi sau khi dòng bundle đã thu tiền (đủ hoặc cọc). Gọi lại với cùng {@code orderItemId} trả về
 * yêu cầu đã tạo.
 *
 * @param productVariantId variant hệ thống "Trà đóng theo yêu cầu" bên Catalog
 */
public record CreateCustomBundlePackingRequest(
        @NotNull UUID orderId,
        String orderCode,
        @NotNull UUID orderItemId,
        @NotNull UUID productVariantId,
        String bundleName,
        UUID branchId,
        @NotEmpty List<@Valid CustomBundleLineRequest> lines
) {
}
