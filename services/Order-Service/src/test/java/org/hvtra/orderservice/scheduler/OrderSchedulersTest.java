package org.hvtra.orderservice.scheduler;

import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSchedulersTest {
    @Mock private OrderPaymentEventService paymentEvents;
    @Mock private OrderReturnInventoryReconciliationService reconciliation;

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void retrySchedulerDelegatesExactlyOnceWithoutWaitingForWallClock(int completed) {
        when(paymentEvents.retryFailedInventory()).thenReturn(completed);
        new OrderInventoryRetryScheduler(paymentEvents).retryFailedInventory();
        verify(paymentEvents).retryFailedInventory();
        verifyNoMoreInteractions(paymentEvents);
    }

    @Test
    void retrySchedulerPropagatesFailuresInsteadOfReportingSuccess() {
        RuntimeException failure = new IllegalStateException("retry failed");
        when(paymentEvents.retryFailedInventory()).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> new OrderInventoryRetryScheduler(paymentEvents).retryFailedInventory()));
    }

    @Test
    void reconciliationSchedulerSurvivesFailureAndCanRunAgain() {
        doThrow(new IllegalStateException("downstream unavailable")).doNothing().when(reconciliation).reconcileStalePendingCommands();
        var scheduler = new OrderReturnInventoryReconciliationScheduler(reconciliation);
        assertDoesNotThrow(scheduler::runReconciliation);
        assertDoesNotThrow(scheduler::runReconciliation);
        verify(reconciliation, times(2)).reconcileStalePendingCommands();
    }
}
