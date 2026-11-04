package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReturnHistoryWriterTest {
    @Mock private OrderReturnHistoryRepository histories;
    @InjectMocks private OrderReturnHistoryWriter writer;
    private static final OrderReturnHistoryAction ACTION = OrderReturnHistoryAction.QC_COMPLETED;

    @Test
    void absentIdentityOrActionNeverWritesHistory() {
        write(null, ACTION);
        write(new OrderReturn(), ACTION);
        write(returnRecord(), null);
        verifyNoInteractions(histories);
    }

    @Test
    void existingHistoryIsIdempotentNoOp() {
        OrderReturn record = returnRecord();
        when(histories.existsByOrderReturnIdAndActionAndIsDeletedFalse(record.getId(), ACTION)).thenReturn(true);
        write(record, ACTION);
        verify(histories, never()).save(any());
    }

    @Test
    void capturesAuditIdentityReferenceAndControlledTimestamp() {
        OrderReturn record = returnRecord();
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(now);
            write(record, ACTION);
        }
        ArgumentCaptor<OrderReturnHistory> captured = ArgumentCaptor.forClass(OrderReturnHistory.class);
        verify(histories).save(captured.capture());
        var history = captured.getValue();
        assertSame(record, history.getOrderReturn());
        assertEquals(ACTION, history.getAction());
        assertEquals(ReturnStatus.RECEIVED, history.getFromStatus());
        assertEquals(ReturnStatus.RECEIVED, history.getToStatus());
        assertEquals("INVENTORY_INSPECTION", history.getReferenceType());
        assertEquals("inspection-fixture", history.getReferenceId());
        assertEquals(now, history.getOccurredAt());
    }

    @Test
    void concurrentDuplicateInsertIsIgnoredOnlyAfterConfirmingHistoryExists() {
        OrderReturn record = returnRecord();
        when(histories.existsByOrderReturnIdAndActionAndIsDeletedFalse(record.getId(), ACTION)).thenReturn(false, true);
        when(histories.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertDoesNotThrow(() -> write(record, ACTION));
        verify(histories, times(2)).existsByOrderReturnIdAndActionAndIsDeletedFalse(record.getId(), ACTION);
        verify(histories).save(any());
    }

    @Test
    void genuineConstraintFailurePropagatesInsteadOfPretendingItIsDuplicate() {
        OrderReturn record = returnRecord();
        var failure = new DataIntegrityViolationException("broken constraint");
        when(histories.save(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> write(record, ACTION)));
        verify(histories, times(2)).existsByOrderReturnIdAndActionAndIsDeletedFalse(record.getId(), ACTION);
    }

    private void write(OrderReturn record, OrderReturnHistoryAction action) {
        writer.writeIfAbsent(record, action, ReturnStatus.RECEIVED, ReturnStatus.RECEIVED, null,
                "Physical inspection complete", "INVENTORY_INSPECTION", "inspection-fixture");
    }

    private OrderReturn returnRecord() {
        OrderReturn record = new OrderReturn();
        record.setId(UUID.randomUUID());
        return record;
    }
}
