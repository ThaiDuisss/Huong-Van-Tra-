package org.hvtra.orderservice.service.support;

import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReturnPhysicalCompletionHandlerTest {
    @Mock private OrderReturnRepository returns;
    @Mock private OrderRepository orders;
    @Mock private OrderReturnPhysicalProgressResolver progress;
    @Mock private OrderReturnHistoryWriter history;
    @Mock private OrderOutboxPublisher outbox;
    @InjectMocks private OrderReturnPhysicalCompletionHandler handler;

    @Test
    void absentReturnDoesNotCreatePhantomHistoryOrCompletion() {
        assertFalse(handler.isPhysicalDispositionCompleted(null));
        handler.recordPhysicalCompletion(null, null, null);
        handler.checkAndAutoCompleteNoRefundReturn(null);
        UUID missingId = UUID.randomUUID();
        handler.recordPhysicalCompletion(missingId, null, null);
        handler.checkAndAutoCompleteNoRefundReturn(missingId);
        verifyNoInteractions(history, orders, outbox, progress);
    }

    @Test
    void recordsQcHistoryWithExactInspectionAndActorWithoutChangingReturnState() {
        OrderReturn record = record(ReturnStatus.RECEIVED, false);
        UUID actor = UUID.randomUUID();
        UUID inspection = UUID.randomUUID();
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        handler.recordPhysicalCompletion(record.getId(), actor, inspection);
        verify(history).writeIfAbsent(record, OrderReturnHistoryAction.QC_COMPLETED, ReturnStatus.RECEIVED,
                ReturnStatus.RECEIVED, actor, "Inventory physical disposition completed", "INVENTORY_INSPECTION", inspection.toString());
        assertEquals(ReturnStatus.RECEIVED, record.getStatus());
        verifyNoInteractions(outbox, orders);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {"CREATED", "CANCELLED", "COMPLETED"})
    void onlyReceivedReturnCanAutoComplete(ReturnStatus status) {
        OrderReturn record = record(status, false);
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        handler.checkAndAutoCompleteNoRefundReturn(record.getId());
        assertEquals(status, record.getStatus());
        verifyNoInteractions(progress, history, orders, outbox);
        verify(returns, never()).save(record);
    }

    @Test
    void refundRequiredReturnCannotBeAutoCompletedByQc() {
        OrderReturn record = record(ReturnStatus.RECEIVED, true);
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        handler.checkAndAutoCompleteNoRefundReturn(record.getId());
        assertEquals(ReturnStatus.RECEIVED, record.getStatus());
        verifyNoInteractions(progress, history, orders, outbox);
    }

    @Test
    void pendingPhysicalQuantityBlocksNoRefundCompletion() {
        OrderReturn record = record(ReturnStatus.RECEIVED, false);
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(progress.resolve(record.getId())).thenReturn(OrderReturnPhysicalProgress.empty());
        handler.checkAndAutoCompleteNoRefundReturn(record.getId());
        assertEquals(ReturnStatus.RECEIVED, record.getStatus());
        verify(returns, never()).save(record);
        verifyNoInteractions(orders, outbox);
    }

    @ParameterizedTest
    @CsvSource({"0,NONE", "1,PARTIALLY_RETURNED", "3,FULLY_RETURNED"})
    void completedNoRefundReturnUpdatesOrderQuantityClassificationAndPublishesOnce(String returned, OrderReturnStatus expected) {
        OrderReturn record = record(ReturnStatus.RECEIVED, false);
        Order order = record.getOrder();
        OrderItem sold = new OrderItem();
        sold.setId(UUID.randomUUID());
        sold.setQuantity(new BigDecimal("3"));
        sold.setIsDeleted(false);
        order.setItems(List.of(sold));
        OrderReturnItem item = new OrderReturnItem();
        item.setOrderItem(sold);
        item.setIsDeleted(false);
        item.setQuantity(new BigDecimal(returned));
        record.setItems(List.of(item));
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(progress.resolve(record.getId())).thenReturn(new OrderReturnPhysicalProgress(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, true, "COMPLETED"));
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(order.getId())).thenReturn(List.of(record));
        UUID actor = UUID.randomUUID();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentUserId).thenReturn(actor.toString());
            handler.checkAndAutoCompleteNoRefundReturn(record.getId());
            handler.checkAndAutoCompleteNoRefundReturn(record.getId());
        }
        assertEquals(ReturnStatus.COMPLETED, record.getStatus());
        assertEquals(expected, order.getReturnStatus());
        verify(returns).save(record);
        verify(orders).save(order);
        verify(outbox).publishReturnCompletedNotification(record, ReturnStatus.COMPLETED, actor);
    }

    @Test
    void missingOrderAndUnavailableActorDoNotStopNoRefundCompletionNotification() {
        OrderReturn record = record(ReturnStatus.RECEIVED, false);
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(progress.resolve(record.getId())).thenReturn(new OrderReturnPhysicalProgress(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, true, "COMPLETED"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentUserId).thenThrow(new AccessDeniedException("no context"));
            handler.checkAndAutoCompleteNoRefundReturn(record.getId());
        }
        assertEquals(ReturnStatus.COMPLETED, record.getStatus());
        verify(outbox).publishReturnCompletedNotification(record, ReturnStatus.COMPLETED, null);
        verify(orders, never()).save(any());
    }

    private OrderReturn record(ReturnStatus status, boolean refund) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        OrderReturn record = OrderReturn.builder().order(order).status(status).refundRequired(refund).build();
        record.setId(UUID.randomUUID());
        return record;
    }
}
