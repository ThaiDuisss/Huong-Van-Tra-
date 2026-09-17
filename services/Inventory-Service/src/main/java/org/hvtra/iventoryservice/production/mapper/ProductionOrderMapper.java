package org.hvtra.iventoryservice.production.mapper;

import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderLineResponse;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ProductionOrderMapper {

    /**
     * Dùng cho chi tiết 1 đơn: order phải được load kèm lines (vd. findByIdWithDetails)
     * để tránh N+1 khi đọc order.getLines().
     */
    public ProductionOrderResponse toResponse(ProductionOrder order) {
        List<ProductionOrderLineResponse> lines = order.getLines() == null
                ? Collections.emptyList()
                : order.getLines().stream().map(this::toLineResponse).toList();
        return build(order, lines);
    }

    /**
     * Dùng cho danh sách phân trang: KHÔNG đọc order.getLines() (lazy collection)
     * để tránh N+1 khi map nhiều order trong 1 trang.
     */
    public ProductionOrderResponse toSummaryResponse(ProductionOrder order) {
        return build(order, Collections.emptyList());
    }

    private ProductionOrderResponse build(ProductionOrder order, List<ProductionOrderLineResponse> lines) {
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getProductVariantId(),
                order.getPlannedQuantity(),
                order.getUnitId(),
                order.getLocationId(),
                order.getNote(),
                order.getActualGoodQuantity(),
                order.getActualRejectedQuantity(),
                order.getFinishedBatchId(),
                lines,
                order.getSubmittedBy(),
                order.getSubmittedAt(),
                order.getApprovedBy(),
                order.getApprovedAt(),
                order.getReleasedBy(),
                order.getReleasedAt(),
                order.getStartedBy(),
                order.getStartedAt(),
                order.getCompletedBy(),
                order.getCompletedAt(),
                order.getCancelledBy(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public ProductionOrderLineResponse toLineResponse(ProductionOrderLine line) {
        return new ProductionOrderLineResponse(
                line.getId(),
                line.getIngredientId(),
                line.getIngredientName(),
                line.getRequiredQuantity(),
                line.getUnitId(),
                line.getWasteRate(),
                line.getReservedQuantity(),
                line.getConsumedQuantity()
        );
    }
}
