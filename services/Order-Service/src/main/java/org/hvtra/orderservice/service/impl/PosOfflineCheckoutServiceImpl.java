package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Không mở transaction ở lớp này: {@link OrderService#createOrder} đã tự quản lý transaction.
 * Nếu hai request cùng khoá chạy song song, unique key {@code uk_orders_client_order_id}
 * làm một bên rollback; bên đó đọc lại và trả về đơn bên kia vừa tạo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosOfflineCheckoutServiceImpl implements PosOfflineCheckoutService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @Override
    public OrderResponse checkout(CreateOrderRequest request) {
        UUID clientOrderId = request.getClientOrderId();
        if (clientOrderId == null) {
            throw new InvalidOrderStatusException(
                    "Đơn offline phải có clientOrderId để chống tạo trùng khi đồng bộ"
            );
        }
        if (request.getOrderId() != null) {
            throw new InvalidOrderStatusException(
                    "Đơn offline không gắn với hóa đơn nháp trên máy chủ; không truyền orderId"
            );
        }

        Optional<OrderResponse> existing = findExisting(clientOrderId);
        if (existing.isPresent()) {
            log.info("Offline checkout replay, clientOrderId={} đã có đơn", clientOrderId);
            return existing.get();
        }

        // POS gửi offlineSale=false khi mạng đã về nhưng giỏ vẫn là giỏ cục bộ (không có đơn nháp):
        // vẫn đi đường một phát + idempotent, nhưng lấy giá catalog hiện tại.
        if (request.getOfflineSale() == null) {
            request.setOfflineSale(Boolean.TRUE);
        }
        if (request.getSoldAt() == null) {
            request.setSoldAt(Instant.now());
        }
        if (request.getCashierId() == null) {
            request.setCashierId(currentUserIdOrNull());
        }

        try {
            return orderService.createOrder(request);
        } catch (DataIntegrityViolationException exception) {
            Optional<OrderResponse> raced = findExisting(clientOrderId);
            if (raced.isPresent()) {
                log.info("Offline checkout race, clientOrderId={} trả về đơn đã tạo", clientOrderId);
                return raced.get();
            }
            throw exception;
        }
    }

    private Optional<OrderResponse> findExisting(UUID clientOrderId) {
        return orderRepository.findByClientOrderIdAndIsDeletedFalse(clientOrderId)
                .map(Order::getId)
                .map(orderService::getOrderById);
    }

    private UUID currentUserIdOrNull() {
        try {
            return UUID.fromString(SecurityUtils.currentUserId());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
