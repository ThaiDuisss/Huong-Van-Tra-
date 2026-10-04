package org.hvtra.iventoryservice.availability.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.availability.AvailabilityProperties;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Giữ sẵn thời gian có hàng trung bình trên {@link InventoryItem} để việc ước tính ngày có hàng chỉ phải đọc
 * một cột thay vì quét lịch sử mỗi lần. Lịch sử chỉ được đọc lại đúng lúc nó thay đổi: khi một lệnh sản xuất
 * hoàn tất hoặc một phiếu nhập ghi sổ. Lỗi ở đây không được làm hỏng nghiệp vụ đang chạy.
 */
@Slf4j
@Service
public class SupplyLeadTimeService {

    private final AvailabilityQueryRepository queries;
    private final InventoryItemRepository itemRepository;
    private final AvailabilityProperties properties;

    public SupplyLeadTimeService(AvailabilityQueryRepository queries, InventoryItemRepository itemRepository,
                                 AvailabilityProperties properties) {
        this.queries = queries;
        this.itemRepository = itemRepository;
        this.properties = properties;
    }

    /** Gọi khi một lệnh sản xuất vừa hoàn tất: tính lại thời gian sản xuất trung bình của thành phẩm đó. */
    @Transactional
    public void onProductionCompleted(ProductionOrder order) {
        try {
            UUID variantId = order.getProductVariantId();
            itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT)
                    .ifPresent(item -> store(item, queries.findRecentCompletedByVariant(variantId, sample()),
                            ProductionOrder::getCreatedAt, ProductionOrder::getCompletedAt));
        } catch (RuntimeException exception) {
            log.warn("Cannot refresh production lead time. productionOrder={}, reason={}",
                    order.getOrderCode(), exception.getMessage());
        }
    }

    /** Gọi khi một phiếu nhập vừa ghi sổ: tính lại thời gian nhập trung bình của từng nguyên liệu trên phiếu. */
    @Transactional
    public void onReceiptPosted(InventoryDocument document) {
        if (document.getDocumentType() != InventoryDocumentType.PURCHASE_RECEIPT || document.getLines() == null) {
            return;
        }
        try {
            Map<UUID, InventoryItem> items = new LinkedHashMap<>();
            for (InventoryDocumentLine line : document.getLines()) {
                if (line.getInventoryItem() != null) {
                    items.putIfAbsent(line.getInventoryItem().getId(), line.getInventoryItem());
                }
            }
            for (InventoryItem item : items.values()) {
                store(item, queries.findRecentPostedReceiptsByItem(item.getId(), sample()),
                        InventoryDocument::getCreatedAt, InventoryDocument::getPostedAt);
            }
        } catch (RuntimeException exception) {
            log.warn("Cannot refresh receipt lead time. document={}, reason={}",
                    document.getDocumentCode(), exception.getMessage());
        }
    }

    private <T> void store(InventoryItem item, List<T> history, Function<T, Instant> start, Function<T, Instant> end) {
        long totalSeconds = 0;
        int count = 0;
        for (T entry : history) {
            Instant from = start.apply(entry);
            Instant to = end.apply(entry);
            if (from == null || to == null || !to.isAfter(from)) {
                continue;
            }
            totalSeconds += Duration.between(from, to).getSeconds();
            count++;
        }
        item.setAvgSupplyLeadSeconds(count == 0 ? null : totalSeconds / count);
        item.setSupplyLeadSamples(count);
        itemRepository.save(item);
    }

    private PageRequest sample() {
        return PageRequest.of(0, Math.max(1, properties.getHistorySampleSize()));
    }
}
