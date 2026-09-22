package org.hvtra.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStatus;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStepStatus;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderSaga;
import org.hvtra.orderservice.domain.entity.OrderSagaStep;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.dto.saga.OrderPaymentSagaContext;
import org.hvtra.orderservice.repository.OrderSagaRepository;
import org.hvtra.orderservice.repository.OrderSagaStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentSagaService {

    public static final String SAGA_TYPE = "ORDER_PAYMENT_SAGA";
    private static final String ENTITY_TYPE = "ORDER";

    private final OrderSagaRepository orderSagaRepository;
    private final OrderSagaStepRepository orderSagaStepRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID startPaymentSaga(Order order, String paymentMethod) {
        UUID sagaId = UUID.randomUUID();
        OrderSaga saga = findOrCreateSaga(order, sagaId);

        saga.setStatus(SagaStatus.RUNNING);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name());
        saga.setSagaDataJson(toJson(new OrderPaymentSagaContext(
                saga.getId(),
                order.getId(),
                order.getId(),
                order.getOrderCode(),
                paymentMethod,
                order.getPaymentCode(),
                order.getFinalAmount()
        )));
        orderSagaRepository.save(saga);

        upsertStep(saga.getId(), 1, org.hvtra.orderservice.domain.enums.OrderSagaStep.ORDER_CREATED.name(), SagaStepStatus.COMPLETED, null);
        upsertStep(saga.getId(), 2, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name(), SagaStepStatus.PROCESSING, null);
        return saga.getId();
    }

    @Transactional
    public void markPaymentRequested(Order order, UUID sagaId, PaymentResponse payment) {
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        saga.setStatus(SagaStatus.RUNNING);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name());
        saga.setSagaDataJson(toJson(new OrderPaymentSagaContext(
                saga.getId(),
                order.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getPaymentMethod(),
                payment == null ? order.getPaymentCode() : firstNonBlank(payment.getPaymentCode(), order.getPaymentCode()),
                order.getFinalAmount()
        )));
        orderSagaRepository.save(saga);
        upsertStep(saga.getId(), 2, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name(), SagaStepStatus.COMPLETED, null);
    }

    @Transactional
    public UUID completeManualPaymentSaga(Order order, String causationId) {
        UUID sagaId = UUID.randomUUID();
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        completeSaga(saga, order, causationId);
        return saga.getId();
    }

    @Transactional
    public UUID completePaymentSaga(Order order, PaymentOrderEventPayload event, String causationId) {
        UUID sagaId = resolveSagaId(event == null ? null : event.getSagaId()).orElseGet(UUID::randomUUID);
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        completeSaga(saga, order, causationId);
        return saga.getId();
    }

    @Transactional
    public void markResourceConfirmationRequested(Order order, UUID sagaId) {
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        saga.setStatus(SagaStatus.RUNNING);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.INVENTORY_CONFIRM_REQUESTED.name());
        saga.setCompletedAt(null);
        saga.setLastError(null);
        saga.setSagaDataJson(mergeSagaData(order, null, "Resource confirmation requested"));
        orderSagaRepository.save(saga);

        upsertStep(
                saga.getId(),
                4,
                org.hvtra.orderservice.domain.enums.OrderSagaStep.INVENTORY_CONFIRM_REQUESTED.name(),
                SagaStepStatus.PROCESSING,
                null
        );

        if (hasCouponUsage(order)) {
            upsertStep(
                    saga.getId(),
                    5,
                    org.hvtra.orderservice.domain.enums.OrderSagaStep.PROMOTION_CONFIRM_REQUESTED.name(),
                    SagaStepStatus.PROCESSING,
                    null
            );
        }
    }

    @Transactional
    public void failPaymentSaga(Order order, PaymentOrderEventPayload event, String error) {
        UUID sagaId = resolveSagaId(event == null ? null : event.getSagaId()).orElseGet(UUID::randomUUID);
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        saga.setStatus(SagaStatus.FAILED);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_FAILED.name());
        saga.setFailedAt(Instant.now());
        saga.setLastError(error);
        saga.setSagaDataJson(mergeSagaData(order, event, error));
        orderSagaRepository.save(saga);
        upsertStep(saga.getId(), 3, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_FAILED.name(), SagaStepStatus.FAILED, error);
    }


    @Transactional
    public void markPaymentWaiting(Order order, PaymentOrderEventPayload event, String reason) {
        UUID sagaId = resolveSagaId(event == null ? null : event.getSagaId()).orElseGet(UUID::randomUUID);
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        saga.setStatus(SagaStatus.RUNNING);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name());
        saga.setLastError(null);
        saga.setSagaDataJson(mergeSagaData(order, event, reason));
        orderSagaRepository.save(saga);
        upsertStep(saga.getId(), 2, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name(), SagaStepStatus.COMPLETED, null);
    }

    @Transactional
    public void markPaymentNeedReview(Order order, PaymentOrderEventPayload event, String reason) {
        UUID sagaId = resolveSagaId(event == null ? null : event.getSagaId()).orElseGet(UUID::randomUUID);
        OrderSaga saga = findOrCreateSaga(order, sagaId);
        saga.setStatus(SagaStatus.RUNNING);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_NEED_REVIEW.name());
        saga.setLastError(reason);
        saga.setSagaDataJson(mergeSagaData(order, event, reason));
        orderSagaRepository.save(saga);
        upsertStep(saga.getId(), 3, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_NEED_REVIEW.name(), SagaStepStatus.PROCESSING, reason);
    }

    private void completeSaga(OrderSaga saga, Order order, String causationId) {
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_COMPLETED.name());
        saga.setCompletedAt(Instant.now());
        saga.setLastError(null);
        saga.setSagaDataJson(toJson(new OrderPaymentSagaContext(
                saga.getId(),
                order.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getPaymentMethod(),
                order.getPaymentCode(),
                order.getFinalAmount()
        )));
        orderSagaRepository.save(saga);
        upsertStep(saga.getId(), 2, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_REQUESTED.name(), SagaStepStatus.COMPLETED, null);
        upsertStep(saga.getId(), 3, org.hvtra.orderservice.domain.enums.OrderSagaStep.PAYMENT_COMPLETED.name(), SagaStepStatus.COMPLETED, causationId);
    }

    private OrderSaga findOrCreateSaga(Order order, UUID requestedSagaId) {
        UUID sagaId = requestedSagaId == null ? UUID.randomUUID() : requestedSagaId;

        Optional<OrderSaga> bySagaId = orderSagaRepository.findByIdForUpdate(sagaId);
        if (bySagaId.isPresent()) {
            return bySagaId.get();
        }

        Optional<OrderSaga> byOrderId = orderSagaRepository.findFirstByOrderIdAndSagaTypeOrderByStartedAtDesc(order.getId(), SAGA_TYPE);
        if (byOrderId.isPresent()) {
            return byOrderId.get();
        }

        OrderSaga saga = new OrderSaga();
        saga.setOrderId(order.getId());
        saga.setOrderCode(order.getOrderCode());
        saga.setSagaType(SAGA_TYPE);
        saga.setEntityType(ENTITY_TYPE);
        saga.setEntityId(order.getId());
        saga.setStatus(SagaStatus.STARTED);
        saga.setCurrentStep(org.hvtra.orderservice.domain.enums.OrderSagaStep.ORDER_CREATED.name());
        saga.setCorrelationId(order.getId().toString());
        saga.setRetryCount(0);
        saga.setMaxRetry(3);
        saga.setSagaDataJson(toJson(new OrderPaymentSagaContext(
                sagaId,
                order.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getPaymentMethod(),
                order.getPaymentCode(),
                order.getFinalAmount()
        )));
        return orderSagaRepository.saveAndFlush(saga);
    }

    private void upsertStep(UUID sagaId, int order, String stepName, SagaStepStatus status, String note) {
        OrderSagaStep step = orderSagaStepRepository.findBySagaIdAndStepName(sagaId, stepName)
                .orElseGet(OrderSagaStep::new);
        step.setSagaId(sagaId);
        step.setStepOrder(order);
        step.setStepName(stepName);
        step.setStatus(status);
        step.setLastError(status == SagaStepStatus.FAILED ? note : null);

        Instant now = Instant.now();
        if (status == SagaStepStatus.PROCESSING && step.getStartedAt() == null) {
            step.setStartedAt(now);
        }
        if (status == SagaStepStatus.COMPLETED) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(now);
            }
            step.setCompletedAt(now);
            step.setFailedAt(null);
        }
        if (status == SagaStepStatus.FAILED) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(now);
            }
            step.setFailedAt(now);
        }

        orderSagaStepRepository.save(step);
    }

    private Optional<UUID> resolveSagaId(String rawSagaId) {
        if (rawSagaId == null || rawSagaId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(rawSagaId));
        } catch (IllegalArgumentException ex) {
            log.warn("Payment event có sagaId không hợp lệ: {}", rawSagaId);
            return Optional.empty();
        }
    }

    private String mergeSagaData(Order order, PaymentOrderEventPayload event, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderCode", order.getOrderCode());
        data.put("paymentMethod", order.getPaymentMethod());
        data.put("paymentCode", order.getPaymentCode());
        data.put("finalAmount", order.getFinalAmount());
        data.put("paymentEventType", event == null ? null : event.getEventType());
        data.put("paymentEventId", event == null ? null : event.getEventId());
        data.put("paymentEventKey", event == null ? null : event.getEventKey());
        data.put("failureReason", event == null ? note : firstNonBlank(event.getFailureReason(), note));
        return toJson(data);
    }

    private boolean hasCouponUsage(Order order) {
        return order.getDiscounts() != null
                && order.getDiscounts().stream()
                .map(discount -> discount.getUsageId())
                .anyMatch(Objects::nonNull);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize order saga data", ex);
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
