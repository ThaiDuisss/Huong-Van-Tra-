package org.hvtra.orderservice.service.mapper;

import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.dto.response.OrderReturnItemResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.springframework.stereotype.Component;

@Component
public class OrderReturnMapper {

    public OrderReturnResponse toResponse(OrderReturn orderReturn) {
        return OrderReturnResponse.builder()
                .id(orderReturn.getId())
                .returnCode(orderReturn.getReturnCode())
                .orderId(orderReturn.getOrder().getId())
                .status(orderReturn.getStatus())
                .note(orderReturn.getNote())
                .createdAt(orderReturn.getCreatedAt())
                .updatedAt(orderReturn.getUpdatedAt())
                .items(orderReturn.getItems().stream()
                        .map(this::toItemResponse)
                        .toList())
                .build();
    }

    private OrderReturnItemResponse toItemResponse(OrderReturnItem item) {
        OrderItem orderItem = item.getOrderItem();

        return OrderReturnItemResponse.builder()
                .id(item.getId())
                .orderItemId(orderItem.getId())
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
