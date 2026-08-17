package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusHistoryReaderTest {

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderStatusHistoryReader reader;

    @Test
    void keepsRepositoryOrderingAndMapsEveryHistory() {
        UUID orderId = UUID.randomUUID();
        OrderStatusHistory firstHistory = new OrderStatusHistory();
        OrderStatusHistory secondHistory = new OrderStatusHistory();
        OrderStatusHistoryResponse firstResponse = OrderStatusHistoryResponse.builder().build();
        OrderStatusHistoryResponse secondResponse = OrderStatusHistoryResponse.builder().build();

        when(orderStatusHistoryRepository.findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(orderId))
                .thenReturn(List.of(firstHistory, secondHistory));
        when(orderMapper.toStatusHistoryResponse(firstHistory)).thenReturn(firstResponse);
        when(orderMapper.toStatusHistoryResponse(secondHistory)).thenReturn(secondResponse);

        List<OrderStatusHistoryResponse> result = reader.findResponses(orderId);

        assertEquals(List.of(firstResponse, secondResponse), result);
        verify(orderStatusHistoryRepository).findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(orderId);
        verify(orderMapper).toStatusHistoryResponse(firstHistory);
        verify(orderMapper).toStatusHistoryResponse(secondHistory);
    }
}
