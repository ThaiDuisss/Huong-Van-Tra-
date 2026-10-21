package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.entity.PriceChangeHistory;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceChangeHistoryResponse(
        UUID id,
        UUID priceListId,
        String priceListName,
        UUID productVariantId,
        String sku,
        String variantName,
        PriceChangeType changeType,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        /** newPrice - oldPrice; null khi thiếu một trong hai (CREATED / REMOVED / STATUS_CHANGED). */
        BigDecimal priceDifference,
        Status oldStatus,
        Status newStatus,
        UUID changedBy,
        String changedByName,
        Instant changedAt
) {
    public static PriceChangeHistoryResponse from(PriceChangeHistory history) {
        BigDecimal difference = history.getOldPrice() != null && history.getNewPrice() != null
                ? history.getNewPrice().subtract(history.getOldPrice())
                : null;
        return new PriceChangeHistoryResponse(
                history.getId(),
                history.getPriceListId(),
                history.getPriceListName(),
                history.getProductVariantId(),
                history.getSku(),
                history.getVariantName(),
                history.getChangeType(),
                history.getOldPrice(),
                history.getNewPrice(),
                difference,
                history.getOldStatus(),
                history.getNewStatus(),
                history.getChangedBy(),
                history.getChangedByName(),
                history.getChangedAt()
        );
    }
}
