package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStatusHistoryWriter {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public void write(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason,
            UUID changedBy
    ) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .build();

        orderStatusHistoryRepository.save(history);
    }
}
