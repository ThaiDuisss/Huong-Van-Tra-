package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.service.OrderExpirationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationServiceImpl implements OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryClient inventoryClient;

    @Override
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public int expireDraftOrders() {
        var expiredOrders = orderRepository.findByStatusAndExpiresAtBeforeAndIsDeletedFalse(
                OrderStatus.DRAFT,
                Instant.now());
        int expiredCount = 0;

        for (Order order : expiredOrders) {
            try {
                inventoryClient.releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder()
                        .orderId(order.getId())
                        .build());
                OrderStatus oldStatus = order.getStatus();
                order.setStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
                orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(oldStatus)
                        .toStatus(OrderStatus.EXPIRED)
                        .reason("Hóa đơn nháp POS hết hạn")
                        .changedAt(Instant.now())
                        .build());
                expiredCount++;
            } catch (RuntimeException ex) {
                log.error("Không thể expire draft orderId={}; sẽ thử lại ở lượt sau", order.getId(), ex);
            }
        }
        return expiredCount;
    }
}
