package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver.RefundEligibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReturnRefundEligibilityResolverTest {

    private OrderReturnRefundEligibilityResolver resolver;
    private OrderReturn orderReturn;
    private UUID returnId;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        resolver = new OrderReturnRefundEligibilityResolver();
        returnId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        Order order = Order.builder()
                .orderCode("ORD-001")
                .build();
        order.setId(orderId);

        orderReturn = OrderReturn.builder()
                .returnCode("RET-001")
                .order(order)
                .status(ReturnStatus.RECEIVED)
                .refundRequired(true)
                .build();
        orderReturn.setId(returnId);
    }

    private PaymentClient.PaymentRefundSummaryResponse createPaymentSummary(
            UUID paymentId,
            String method,
            String paymentStatus,
            String refundStatus,
            UUID refundTransactionId,
            BigDecimal refundAmount,
            BigDecimal totalAmountRefunded,
            BigDecimal amountPaid
    ) {
        return new PaymentClient.PaymentRefundSummaryResponse(
                returnId,
                orderId,
                paymentId,
                refundTransactionId,
                refundAmount,
                totalAmountRefunded,
                amountPaid,
                refundStatus,
                paymentStatus,
                method,
                null
        );
    }

    @Test
    @DisplayName("Should block with REFUND_NOT_REQUIRED when refundRequired is false")
    void shouldBlockWhenRefundNotRequired() {
        orderReturn.setRefundRequired(false);

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_REQUIRED);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.REFUND_NOT_REQUIRED);
    }

    @Test
    @DisplayName("Should block with ALREADY_REFUNDED when return status is COMPLETED")
    void shouldBlockWhenReturnStatusIsCompleted() {
        orderReturn.setStatus(ReturnStatus.COMPLETED);

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.REFUNDED);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.ALREADY_REFUNDED);
    }

    @Test
    @DisplayName("Should block with ALREADY_REFUNDED when payment refundStatus is REFUNDED")
    void shouldBlockWhenPaymentSummaryRefundStatusIsRefunded() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PAID",
                "REFUNDED",
                UUID.randomUUID(),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(100_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.REFUNDED);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.ALREADY_REFUNDED);
    }

    @Test
    @DisplayName("Should block with REFUND_FAILED when payment refundStatus is FAILED")
    void shouldBlockWhenPaymentSummaryRefundStatusIsFailed() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PAID",
                "FAILED",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.FAILED);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.REFUND_FAILED);
    }

    @Test
    @DisplayName("Should block with RETURN_NOT_RECEIVED when return status is not RECEIVED")
    void shouldBlockWhenReturnNotReceived() {
        orderReturn.setStatus(ReturnStatus.CREATED);

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.RETURN_NOT_RECEIVED);
    }

    @Test
    @DisplayName("Should block with PHYSICAL_DISPOSITION_NOT_COMPLETED when physicalDone is false")
    void shouldBlockWhenPhysicalDoneIsFalse() {
        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                false, // physicalDone = false
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.PHYSICAL_DISPOSITION_NOT_COMPLETED);
    }

    @Test
    @DisplayName("Should block with PAYMENT_SERVICE_UNAVAILABLE when paymentAvailable is false")
    void shouldBlockWhenPaymentServiceUnavailable() {
        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                true,
                false, // paymentAvailable = false
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.PAYMENT_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Should block with PAYMENT_NOT_FOUND when paymentSummary is null or NOT_FOUND")
    void shouldBlockWhenPaymentNotFound() {
        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                null,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.PAYMENT_NOT_FOUND);

        PaymentClient.PaymentRefundSummaryResponse notFoundSummary = createPaymentSummary(
                null,
                null,
                null,
                "NOT_FOUND",
                null,
                null,
                null,
                null
        );

        RefundEligibilityResult result2 = resolver.resolveEligibility(
                orderReturn,
                notFoundSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result2.refundEligible()).isFalse();
        assertThat(result2.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result2.blockReason()).isEqualTo(OrderRefundBlockReason.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Should block with UNSUPPORTED_PAYMENT_METHOD when payment method is not CASH")
    void shouldBlockWhenPaymentMethodNotCash() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "BANK_TRANSFER",
                "PAID",
                "PENDING",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.UNSUPPORTED_PAYMENT_METHOD);
    }

    @Test
    @DisplayName("Should block with PAYMENT_NOT_REFUNDABLE when payment status is UNPAID or FAILED")
    void shouldBlockWhenPaymentStatusNotRefundable() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "UNPAID",
                "PENDING",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("Should block with NO_REFUND_AMOUNT when refundAmount <= 0 or null")
    void shouldBlockWhenNoRefundAmount() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PAID",
                "PENDING",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.ZERO
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.NO_REFUND_AMOUNT);
    }

    @Test
    @DisplayName("Should block with REFUND_AMOUNT_EXCEEDS_REMAINING when refundAmount > remainingRefundable")
    void shouldBlockWhenRefundAmountExceedsRemaining() {
        // amountPaid = 500,000, alreadyRefunded = 200,000 => remaining = 300,000.
        // currentRefundAmount = 400,000 > 300,000
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PARTIALLY_REFUNDED",
                "PENDING",
                null,
                null,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(400_000)
        );

        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.NOT_READY);
        assertThat(result.blockReason()).isEqualTo(OrderRefundBlockReason.REFUND_AMOUNT_EXCEEDS_REMAINING);
    }

    @Test
    @DisplayName("Partial refund: should be eligible when remainingRefundable >= refundAmount")
    void shouldBeEligibleForPartialRefund() {
        // amountPaid = 500,000, alreadyRefunded = 200,000 => remaining = 300,000.
        // currentRefundAmount = 100,000 <= 300,000
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PARTIALLY_REFUNDED",
                "PENDING",
                null,
                null,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(100_000)
        );

        assertThat(result.refundEligible()).isTrue();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.PENDING);
        assertThat(result.blockReason()).isNull();
    }

    @Test
    @DisplayName("Full refund: should be eligible when all conditions satisfied")
    void shouldBeEligibleForFullRefund() {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = createPaymentSummary(
                paymentId,
                "CASH",
                "PAID",
                "PENDING",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500_000)
        );

        RefundEligibilityResult result = resolver.resolveEligibility(
                orderReturn,
                paymentSummary,
                true,
                true,
                BigDecimal.valueOf(500_000)
        );

        assertThat(result.refundEligible()).isTrue();
        assertThat(result.refundStatus()).isEqualTo(OrderRefundStatus.PENDING);
        assertThat(result.blockReason()).isNull();
    }
}
