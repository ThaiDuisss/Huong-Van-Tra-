package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderStatusManagementService;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderStatusManagementServiceImpl implements OrderStatusManagementService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = getExistingOrder(orderId);
        if ((request.getStatus() == OrderStatus.COMPLETED || request.getStatus() == OrderStatus.CANCELLED)
                && order.isFulfillmentRequired() && order.getPaymentStatus() == PaymentStatus.PAID) {
            order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
        }
        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == request.getStatus()) {
            return orderResponseAssembler.toResponse(order);
        }

        if (order.isFulfillmentRequired() && order.getPaymentStatus() == PaymentStatus.PAID) {
            if (request.getStatus() == OrderStatus.COMPLETED) {
                if (order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.RESERVED) {
                    throw new IllegalStateException("Online order has no material reservation to consume");
                }
                inventoryClient.consumeMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
                order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
                order.setInventoryProcessedAt(Instant.now());
            } else if (request.getStatus() == OrderStatus.CANCELLED) {
                if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.CONSUMED) {
                    throw new IllegalStateException("Consumed online order cannot be cancelled");
                }
                if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED
                        && order.getSellableCompensatedAt() == null) {
                    throw new IllegalStateException("Inventory compensation is pending; retry processing before cancellation");
                }
                if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.RESERVED) {
                    var released = inventoryClient.releaseMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
                    if (released != null && released.stream().anyMatch(item -> "CONFIRMED".equals(item.status()))) {
                        throw new IllegalStateException("Material reservation has already been consumed");
                    }
                    restoreSellable(order);
                    order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RELEASED);
                    order.setSellableCompensatedAt(Instant.now());
                } else if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED) {
                    order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RELEASED);
                }
            }
        }

        order.setStatus(request.getStatus());
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, request.getStatus(), request.getNote(), null);

        return orderResponseAssembler.toResponse(savedOrder);
    }

    private void restoreSellable(Order order) {
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        order.getItems().forEach(item -> quantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add));
        quantities.forEach((variantId, quantity) -> catalogClient.restoreSellableQuantity(variantId,
                new CatalogClient.SellableQuantityAdjustmentRequest(quantity, null, "ORDER_CANCEL",
                        "ORDER_CANCEL:" + order.getId())));
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }
}
