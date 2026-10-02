package org.hvtra.iventoryservice.sale.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FulfillSaleResponse(
        UUID orderId,
        /** Đơn đã được trừ tồn ở lần gọi trước; lần này không ghi gì thêm. */
        boolean alreadyProcessed,
        /** Mã phiếu nhập kệ được xuất ngay cho đơn (khi kệ thiếu hàng), nếu có. */
        String replenishmentCode,
        List<LineResult> lines,
        List<MaterialResult> materials
) {
    public record LineResult(
            UUID productVariantId,
            BigDecimal soldQuantity,
            /** Số đã chuyển từ kho lên kệ để đủ bán. */
            BigDecimal movedFromWarehouse,
            BigDecimal remainingShelfQuantity,
            BigDecimal remainingWarehouseQuantity
    ) {
    }

    public record MaterialResult(
            UUID productVariantId,
            UUID ingredientId,
            BigDecimal requiredQuantity,
            BigDecimal reservedQuantity,
            BigDecimal shortageQuantity,
            BigDecimal availableAfter,
            boolean alertRaised
    ) {
    }
}
