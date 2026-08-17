package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderStatusHistoryReader {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderMapper orderMapper;

    public List<OrderStatusHistoryResponse> findResponses(UUID orderId) {
        return orderStatusHistoryRepository.findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(orderId)
                .stream()
                .map(orderMapper::toStatusHistoryResponse)
                .toList();
    }
}
