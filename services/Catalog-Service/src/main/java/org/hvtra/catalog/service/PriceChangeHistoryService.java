package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceChangeHistoryResponse;
import org.hvtra.catalog.entity.PriceList;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface PriceChangeHistoryService {

    /** Giá của một Mặt hàng trong bảng giá tại thời điểm trước khi sửa. */
    record PriceSnapshot(UUID productVariantId, String sku, String variantName, BigDecimal sellingPrice) {
    }

    /** Chụp lại các mức giá hiện có của bảng giá; gọi TRƯỚC khi thay items. */
    Collection<PriceSnapshot> snapshot(PriceList priceList);

    /**
     * So sánh snapshot cũ với items hiện tại của bảng giá (đã lưu, đã có id) và ghi lịch sử
     * cho các Mặt hàng được thêm / đổi giá / gỡ. Giá không đổi thì không ghi.
     */
    void recordItemChanges(PriceList priceList, Collection<PriceSnapshot> before);

    /** Ghi lịch sử đổi trạng thái bảng giá; không ghi nếu trạng thái không đổi. */
    void recordStatusChange(PriceList priceList, Status oldStatus);

    PageResponse<PriceChangeHistoryResponse> findByPriceList(
            UUID priceListId, UUID variantId, PriceChangeType changeType, Instant from, Instant to,
            int page, int size);

    PageResponse<PriceChangeHistoryResponse> findByVariant(
            UUID variantId, PriceChangeType changeType, Instant from, Instant to, int page, int size);
}
