package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderStatusHistoryWriterTest {

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @InjectMocks
    private OrderStatusHistoryWriter writer;

    @Test
    void writesAllExistingHistoryFields() {
        Order order = new Order();
        UUID changedBy = UUID.randomUUID();
        Instant beforeWrite = Instant.now();

        writer.write(
                order,
                OrderStatus.DRAFT,
                OrderStatus.CANCELLED,
                "Lý do",
                changedBy
        );

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(captor.capture());
        OrderStatusHistory history = captor.getValue();

        assertSame(order, history.getOrder());
        assertEquals(OrderStatus.DRAFT, history.getFromStatus());
        assertEquals(OrderStatus.CANCELLED, history.getToStatus());
        assertEquals("Lý do", history.getReason());
        assertEquals(changedBy, history.getChangedBy());
        assertFalse(history.getChangedAt().isBefore(beforeWrite));
    }
}
