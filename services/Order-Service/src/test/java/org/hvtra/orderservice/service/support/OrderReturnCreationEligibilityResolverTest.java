package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderReturnCreationEligibilityResolverTest {
    private final OrderReturnCreationEligibilityResolver resolver = new OrderReturnCreationEligibilityResolver();
    private final UUID itemId = UUID.randomUUID();
    private final Order order = Order.builder().status(OrderStatus.COMPLETED).paymentStatus(PaymentStatus.PAID)
            .returnStatus(OrderReturnStatus.NONE).build();
    private final OrderItem orderItem = OrderItem.builder().quantity(new BigDecimal("3")).build();

    @BeforeEach
    void setUp() {
        order.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        orderItem.setId(itemId);
    }

    private CreateOrderReturnRequest request(String quantity, ReturnReason reason, ReturnCondition condition, String note) {
        return CreateOrderReturnRequest.builder().items(List.of(CreateOrderReturnItemRequest.builder()
                .orderItemId(itemId).quantity(new BigDecimal(quantity)).reason(reason).condition(condition).note(note).build())).build();
    }

    private OrderReturnEligibilityResponse resolve(CreateOrderReturnRequest request, String alreadyReturned) {
        return resolver.resolve(order, request, Map.of(itemId, orderItem), Map.of(itemId, new BigDecimal(alreadyReturned)));
    }

    private void assertFailed(OrderReturnEligibilityResponse result, String code) {
        assertThat(result.eligible()).isFalse();
        assertThat(Stream.concat(result.checks().stream(), result.items().stream().flatMap(item -> item.checks().stream()))
                .filter(check -> check.code().equals(code))).allMatch(check -> !check.passed()).hasSize(1);
        assertThrows(InvalidOrderReturnException.class, () -> resolver.requireEligible(result));
    }

    @ParameterizedTest
    @EnumSource(ReturnReason.class)
    void completedPaidOrderWithRemainingQuantityHasPolicyForEveryExistingReason(ReturnReason reason) {
        var result = resolve(request("1", reason, ReturnCondition.SEALED, "Khách trả hàng"), "1");
        assertThat(result.eligible()).isTrue();
        assertThat(result.items().getFirst().remainingQuantity()).isEqualByComparingTo("2");
        assertThat(result.items().getFirst().policy()).anyMatch(line -> line.contains("QC"));
        assertThat(result.items().getFirst().policy()).noneMatch(line -> line.contains("RESTOCK") || line.contains("WRITE_OFF") || line.contains("7 ngày"));
        resolver.requireEligible(result);
    }

    @Test
    void partiallyRefundedOrderIsStillEligible() {
        order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(resolve(request("2", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null), "1").eligible()).isTrue();
    }

    @Test
    void nonCompletedOrderFailsSpecificCheck() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        assertFailed(resolve(request("1", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null), "0"), "ORDER_COMPLETED");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PAID", "PARTIALLY_REFUNDED"})
    void invalidPaymentFailsSpecificCheck(PaymentStatus status) {
        order.setPaymentStatus(status);
        assertFailed(resolve(request("1", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null), "0"), "PAYMENT_VALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.0001"})
    void invalidQuantityFailsSpecificCheck(String quantity) {
        assertFailed(resolve(request(quantity, ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null), "0"), "QUANTITY_POSITIVE");
    }

    @Test
    void quantityAboveRemainingFailsAfterPreviousReturnsAreSubtracted() {
        assertFailed(resolve(request("2", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null), "2"), "QUANTITY_WITHIN_REMAINING");
    }

    @Test
    void fullyConsumedQuantityFails() {
        assertFailed(resolve(request("1", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null), "3"), "QUANTITY_REMAINING");
    }

    @Test
    void foreignItemFailsMembershipCheck() {
        var draft = request("1", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null);
        draft.getItems().getFirst().setOrderItemId(UUID.randomUUID());
        assertFailed(resolve(draft, "0"), "ITEM_BELONGS_TO_ORDER");
    }

    @Test
    void missingOrderProducesStructuredFailure() {
        assertFailed(resolver.resolve(null, request("1", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null), Map.of(), Map.of()), "ORDER_EXISTS");
    }

    @Test
    void missingReasonIsExplicitlyReported() {
        assertFailed(resolve(request("1", null, ReturnCondition.OPENED, null), "0"), "REASON_REQUIRED");
    }

    @Test
    void changeOfMindRequiresDeclaredSealedCondition() {
        assertFailed(resolve(request("1", ReturnReason.CHANGE_OF_MIND, ReturnCondition.OPENED, null), "0"), "CHANGE_OF_MIND_SEALED");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void otherReasonRequiresNote(String note) {
        assertFailed(resolve(request("1", ReturnReason.OTHER, ReturnCondition.SEALED, note), "0"), "OTHER_NOTE_REQUIRED");
    }

    @Test
    void duplicateLinesCannotDoubleTheRequestedQuantity() {
        var draft = request("2", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null);
        draft.setItems(List.of(draft.getItems().getFirst(), draft.getItems().getFirst()));
        assertFailed(resolve(draft, "0"), "ITEMS_UNIQUE");
    }
}
