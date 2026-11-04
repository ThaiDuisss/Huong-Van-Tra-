package org.hvtra.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStatus;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStepStatus;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.entity.OrderSaga;
import org.hvtra.orderservice.domain.entity.OrderSagaStep;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.repository.OrderSagaRepository;
import org.hvtra.orderservice.repository.OrderSagaStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentSagaServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock private OrderSagaRepository sagas;
    @Mock private OrderSagaStepRepository steps;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private OrderPaymentSagaService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentSagaService(sagas, steps, mapper);
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("ORD-UNIT");
        order.setPaymentMethod("CASH");
        order.setPaymentCode("PAY-UNIT");
        order.setFinalAmount(new BigDecimal("120000"));
    }

    @Test
    void startingSagaPersistsOrderContextAndOrderedSteps() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(sagas.saveAndFlush(any())).thenAnswer(call -> {
            OrderSaga created = call.getArgument(0);
            created.setId(createdId);
            return created;
        });
        atFixedTime(() -> assertEquals(createdId, service.startPaymentSaga(order, "CASH")));
        ArgumentCaptor<OrderSaga> captured = ArgumentCaptor.forClass(OrderSaga.class);
        verify(sagas).save(captured.capture());
        var saga = captured.getValue();
        assertEquals(SagaStatus.RUNNING, saga.getStatus());
        assertEquals("PAYMENT_REQUESTED", saga.getCurrentStep());
        assertEquals(order.getId(), saga.getOrderId());
        assertEquals(order.getId(), saga.getEntityId());
        assertEquals(order.getId().toString(), saga.getCorrelationId());
        var json = mapper.readTree(saga.getSagaDataJson());
        assertEquals(order.getId().toString(), json.get("orderId").asText());
        assertEquals("CASH", json.get("paymentMethod").asText());
        assertEquals(120000, json.get("finalAmount").asInt());
        var capturedSteps = captureSteps(2);
        assertEquals(List.of("ORDER_CREATED", "PAYMENT_REQUESTED"), capturedSteps.stream().map(OrderSagaStep::getStepName).toList());
        assertEquals(List.of(1, 2), capturedSteps.stream().map(OrderSagaStep::getStepOrder).toList());
        assertEquals(SagaStepStatus.COMPLETED, capturedSteps.getFirst().getStatus());
        assertEquals(SagaStepStatus.PROCESSING, capturedSteps.getLast().getStatus());
        assertEquals(NOW, capturedSteps.getFirst().getCompletedAt());
        assertEquals(NOW, capturedSteps.getLast().getStartedAt());
        assertNull(capturedSteps.getLast().getCompletedAt());
    }

    @Test
    void restartingPaymentUsesLatestExistingSagaInsteadOfCreatingAnother() {
        OrderSaga existing = saga();
        when(sagas.findFirstByOrderIdAndSagaTypeOrderByStartedAtDesc(order.getId(), OrderPaymentSagaService.SAGA_TYPE))
                .thenReturn(Optional.of(existing));
        atFixedTime(() -> assertEquals(existing.getId(), service.startPaymentSaga(order, "CASH")));
        verify(sagas, never()).saveAndFlush(any());
        assertEquals(SagaStatus.RUNNING, existing.getStatus());
    }

    @Test
    void failedPaymentRecordsErrorAndFailedStepWithControlledTime() throws Exception {
        OrderSaga existing = existingSaga();
        PaymentOrderEventPayload event = event(existing.getId());
        event.setEventType("PAYMENT_FAILED");
        event.setFailureReason("declined");
        atFixedTime(() -> service.failPaymentSaga(order, event, "payment rejected"));
        assertEquals(SagaStatus.FAILED, existing.getStatus());
        assertEquals("PAYMENT_FAILED", existing.getCurrentStep());
        assertEquals("payment rejected", existing.getLastError());
        assertEquals(NOW, existing.getFailedAt());
        assertEquals("declined", mapper.readTree(existing.getSagaDataJson()).get("failureReason").asText());
        OrderSagaStep step = captureSteps(1).getFirst();
        assertEquals(SagaStepStatus.FAILED, step.getStatus());
        assertEquals("payment rejected", step.getLastError());
        assertEquals(NOW, step.getFailedAt());
        assertEquals(NOW, step.getStartedAt());
    }

    @Test
    void completionUpdatesExistingStepClearsFailureAndKeepsOriginalStart() {
        OrderSaga existing = existingSaga();
        existing.setLastError("old failure");
        OrderSagaStep requested = new OrderSagaStep();
        Instant started = NOW.minusSeconds(100);
        requested.setStartedAt(started);
        requested.setFailedAt(started);
        when(steps.findBySagaIdAndStepName(existing.getId(), "PAYMENT_REQUESTED")).thenReturn(Optional.of(requested));
        atFixedTime(() -> assertEquals(existing.getId(), service.completePaymentSaga(order, event(existing.getId()), "event-1")));
        assertEquals(SagaStatus.COMPLETED, existing.getStatus());
        assertEquals("PAYMENT_COMPLETED", existing.getCurrentStep());
        assertEquals(NOW, existing.getCompletedAt());
        assertNull(existing.getLastError());
        assertEquals(started, requested.getStartedAt());
        assertEquals(NOW, requested.getCompletedAt());
        assertNull(requested.getFailedAt());
        assertEquals(SagaStepStatus.COMPLETED, requested.getStatus());
        assertEquals("PAYMENT_COMPLETED", captureSteps(2).getLast().getStepName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid"})
    void absentOrMalformedSagaIdResolvesLatestOrderSaga(String rawId) {
        OrderSaga existing = saga();
        when(sagas.findFirstByOrderIdAndSagaTypeOrderByStartedAtDesc(order.getId(), OrderPaymentSagaService.SAGA_TYPE))
                .thenReturn(Optional.of(existing));
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setSagaId(rawId);
        atFixedTime(() -> assertEquals(existing.getId(), service.completePaymentSaga(order, event, "event-2")));
        verify(sagas, never()).saveAndFlush(any());
    }

    @Test
    void reviewAndWaitingRecordDifferentStepAndErrorSemantics() throws Exception {
        OrderSaga existing = existingSaga();
        PaymentOrderEventPayload event = event(existing.getId());
        atFixedTime(() -> service.markPaymentNeedReview(order, event, "amount mismatch"));
        assertEquals(SagaStatus.RUNNING, existing.getStatus());
        assertEquals("PAYMENT_NEED_REVIEW", existing.getCurrentStep());
        assertEquals("amount mismatch", existing.getLastError());
        atFixedTime(() -> service.markPaymentWaiting(order, event, "awaiting transfer"));
        assertEquals("PAYMENT_REQUESTED", existing.getCurrentStep());
        assertNull(existing.getLastError());
        assertEquals("awaiting transfer", mapper.readTree(existing.getSagaDataJson()).get("failureReason").asText());
        List<OrderSagaStep> saved = captureSteps(2);
        assertEquals(SagaStepStatus.PROCESSING, saved.getFirst().getStatus());
        assertEquals(SagaStepStatus.COMPLETED, saved.getLast().getStatus());
    }

    @Test
    void resourceConfirmationOnlyCreatesPromotionStepWhenCouponUsageExists() {
        OrderSaga existing = existingSaga();
        existing.setCompletedAt(NOW);
        existing.setLastError("old");
        OrderDiscount discount = new OrderDiscount();
        discount.setUsageId(UUID.randomUUID());
        order.setDiscounts(List.of(discount));
        atFixedTime(() -> service.markResourceConfirmationRequested(order, existing.getId()));
        assertEquals(SagaStatus.RUNNING, existing.getStatus());
        assertEquals("INVENTORY_CONFIRM_REQUESTED", existing.getCurrentStep());
        assertNull(existing.getCompletedAt());
        assertNull(existing.getLastError());
        assertEquals(List.of("INVENTORY_CONFIRM_REQUESTED", "PROMOTION_CONFIRM_REQUESTED"),
                captureSteps(2).stream().map(OrderSagaStep::getStepName).toList());
    }

    @Test
    void noCouponUsageCreatesOnlyInventoryStep() {
        OrderSaga existing = existingSaga();
        order.setDiscounts(List.of(new OrderDiscount()));
        atFixedTime(() -> service.markResourceConfirmationRequested(order, existing.getId()));
        assertEquals("INVENTORY_CONFIRM_REQUESTED", captureSteps(1).getFirst().getStepName());
    }

    @Test
    void markRequestedUsesPaymentCodeWhenPresentOtherwiseOrderSnapshot() throws Exception {
        OrderSaga existing = existingSaga();
        PaymentResponse payment = PaymentResponse.builder().paymentCode("PAY-NEW").build();
        atFixedTime(() -> service.markPaymentRequested(order, existing.getId(), payment));
        assertEquals("PAY-NEW", mapper.readTree(existing.getSagaDataJson()).get("paymentCode").asText());
        payment.setPaymentCode(" ");
        atFixedTime(() -> service.markPaymentRequested(order, existing.getId(), payment));
        assertEquals("PAY-UNIT", mapper.readTree(existing.getSagaDataJson()).get("paymentCode").asText());
        atFixedTime(() -> service.markPaymentRequested(order, existing.getId(), null));
        assertEquals("PAY-UNIT", mapper.readTree(existing.getSagaDataJson()).get("paymentCode").asText());
    }

    @Test
    void serializationFailurePropagatesAndDoesNotSavePartialSaga() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("fixture failure") {});
        var brokenService = new OrderPaymentSagaService(sagas, steps, broken);
        assertThrows(IllegalStateException.class, () -> brokenService.startPaymentSaga(order, "CASH"));
        verify(sagas, never()).saveAndFlush(any());
        verify(sagas, never()).save(any());
        verifyNoInteractions(steps);
    }

    private OrderSaga existingSaga() {
        OrderSaga existing = saga();
        when(sagas.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        return existing;
    }

    private OrderSaga saga() {
        OrderSaga saga = new OrderSaga();
        saga.setId(UUID.randomUUID());
        saga.setOrderId(order.getId());
        return saga;
    }

    private PaymentOrderEventPayload event(UUID sagaId) {
        var event = new PaymentOrderEventPayload();
        event.setSagaId(sagaId.toString());
        return event;
    }

    private List<OrderSagaStep> captureSteps(int count) {
        ArgumentCaptor<OrderSagaStep> captured = ArgumentCaptor.forClass(OrderSagaStep.class);
        verify(steps, times(count)).save(captured.capture());
        return captured.getAllValues();
    }

    private void atFixedTime(Runnable action) {
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            action.run();
        }
    }
}
