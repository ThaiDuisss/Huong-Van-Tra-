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
     * Dùng cho chi tiết 1 đơn: order phải được load kèm lines (vd. findByIdWithLines)
     * để tránh N+1 khi đọc order.getLines().
     */
    public ProductionOrderResponse toResponse(ProductionOrder order) {
        return toResponse(order, null);
    }

    public ProductionOrderResponse toResponse(ProductionOrder order, String createByName) {
        List<ProductionOrderLineResponse> lines = order.getLines() == null
                ? Collections.emptyList()
                : order.getLines().stream().map(this::toLineResponse).toList();
        return build(order, lines, createByName);
    }

    /**
     * Dùng cho danh sách phân trang: KHÔNG đọc order.getLines() (lazy collection)
     * để tránh N+1 khi map nhiều order trong 1 trang.
     */
    public ProductionOrderResponse toSummaryResponse(ProductionOrder order) {
        return toSummaryResponse(order, null);
    }

    public ProductionOrderResponse toSummaryResponse(ProductionOrder order, String createByName) {
        return build(order, Collections.emptyList(), createByName);
    }

    private ProductionOrderResponse build(ProductionOrder order, List<ProductionOrderLineResponse> lines, String createByName) {
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getProductVariant() != null ? order.getProductVariant().getId() : null,
                order.getProductVariant() != null ? order.getProductVariant().getName() : null,
                order.getPlannedQuantity(),
                order.getUnitId(),
                order.getLocationId(),
                order.getNote(),
                lines,
                order.getCreatedBy(),
                createByName,
                order.getSubmittedBy(),
                order.getSubmittedAt(),
                order.getApprovedBy(),
                order.getApprovedAt(),
                order.getRejectedBy(),
                order.getRejectedAt(),
                order.getCancelledBy(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getExpectedCompletionDate(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public ProductionOrderLineResponse toLineResponse(ProductionOrderLine line) {
        return new ProductionOrderLineResponse(
                line.getId(),
                line.getIngredient() != null ? line.getIngredient().getId() : null,
                line.getIngredient() != null ? line.getIngredient().getName() : null,
                line.getRequiredQuantity(),
                line.getUnitId(),
                line.getWasteRate(),
                line.getReservedQuantity(),
                line.getConsumedQuantity()
        );
    }
}
