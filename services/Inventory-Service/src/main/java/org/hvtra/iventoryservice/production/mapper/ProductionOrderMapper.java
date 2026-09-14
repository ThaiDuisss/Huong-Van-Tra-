package org.hvtra.iventoryservice.production.mapper;

import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderLineResponse;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductionOrderMapper {

    public ProductionOrderResponse toResponse(ProductionOrder order) {
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getProductVariantId(),
                order.getBomId(),
                order.getBomVersion(),
                order.getPlannedQuantity(),
                order.getUnitId(),
                order.getLocationId(),
                order.getNote(),
                order.getActualGoodQuantity(),
                order.getActualRejectedQuantity(),
                order.getFinishedBatchId(),
                order.getLines() == null ? java.util.Collections.emptyList()
                        : order.getLines().stream().map(this::toLineResponse).toList(),
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
