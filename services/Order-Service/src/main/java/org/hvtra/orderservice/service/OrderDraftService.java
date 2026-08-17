package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderDraftService {

    OrderResponse addDraftItem(AddDraftOrderItemRequest request);

    OrderResponse updateDraftItemQuantity(
            UUID orderId,
            UUID itemId,
            UpdateDraftOrderItemQuantityRequest request
    );

    OrderResponse removeDraftItem(UUID orderId, UUID itemId);

    OrderResponse cancelDraftOrder(UUID orderId);

    OrderResponse checkoutDraftOrder(UUID orderId, CheckoutDraftOrderRequest request);
}
