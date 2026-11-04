package org.hvtra.orderservice.service.mapper;

import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.dto.response.OrderReturnItemResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgress;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderReturnMapper {

    public OrderReturnResponse toResponse(OrderReturn orderReturn) {
        return toResponse(orderReturn, null, null);
    }

    public OrderReturnResponse toResponse(OrderReturn orderReturn, OrderReturnPhysicalProgress progress) {
        return toResponse(orderReturn, progress, null);
    }

    public OrderReturnResponse toResponse(
            OrderReturn orderReturn,
            OrderReturnPhysicalProgress progress,
            String displayStatus
    ) {
        boolean physicalDone = progress != null && progress.physicalDone();
        boolean isPendingDisposition = progress != null && !physicalDone && progress.hasPendingCommand();
        boolean hasFailedDisposition = progress != null && !physicalDone && progress.hasFailedCommand();

        return OrderReturnResponse.builder()
                .id(orderReturn.getId())
                .returnCode(orderReturn.getReturnCode())
                .orderId(orderReturn.getOrder().getId())
                .orderCode(orderReturn.getOrder().getOrderCode())
                .customerName(orderReturn.getOrder().getCustomerNameSnapshot())
                .customerPhone(orderReturn.getOrder().getCustomerPhoneSnapshot())
                .branchId(orderReturn.getOrder().getBranchId())
                .status(orderReturn.getStatus())
                .note(orderReturn.getNote())
                .refundRequired(orderReturn.getRefundRequired())
                .createdAt(orderReturn.getCreatedAt())
                .updatedAt(orderReturn.getUpdatedAt())
                .items(orderReturn.getItems() == null ? List.of() : orderReturn.getItems().stream()
                        .map(this::toItemResponse)
                        .toList())
                .physicalDone(progress != null ? progress.physicalDone() : null)
                .requiredQuantity(progress != null ? progress.requiredQuantity() : null)
                .processedQuantity(progress != null ? progress.processedQuantity() : null)
                .remainingQuantity(progress != null ? progress.remainingQuantity() : null)
                .inventoryProcessingStatus(progress != null ? progress.inventoryProcessingStatus() : null)
                .displayStatus(displayStatus)
                .isPendingDisposition(isPendingDisposition)
                .hasFailedDisposition(hasFailedDisposition)
                .build();
    }

    private OrderReturnItemResponse toItemResponse(OrderReturnItem item) {
        OrderItem orderItem = item.getOrderItem();

        return OrderReturnItemResponse.builder()
                .id(item.getId())
                .orderItemId(orderItem.getId())
                .productVariantId(orderItem.getProductVariantId())
                .quantity(item.getQuantity())
                .reason(item.getReason())
                .condition(item.getCondition())
                .note(item.getNote())
                .skuSnapshot(orderItem.getSkuSnapshot())
                .productNameSnapshot(orderItem.getProductNameSnapshot())
                .variantNameSnapshot(orderItem.getVariantNameSnapshot())
                .build();
    }
}
