package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.PaymentClient.PaymentRefundSummaryResponse;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderReturnRefundEligibilityResolver {

    public RefundEligibilityResult resolve(
            OrderReturn orderReturn,
            OrderReturnPhysicalProgress physicalProgress,
            PaymentRefundSummaryResponse paymentSummary,
            boolean paymentAvailable,
            BigDecimal refundAmount
    ) {
        return resolve(orderReturn, physicalProgress, paymentSummary, paymentAvailable, refundAmount, null);
    }

    public RefundEligibilityResult resolveEligibility(
            OrderReturn orderReturn,
            PaymentRefundSummaryResponse paymentSummary,
            boolean physicalDone,
            boolean paymentAvailable,
            BigDecimal refundAmount
    ) {
        OrderReturnPhysicalProgress progress = new OrderReturnPhysicalProgress(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                physicalDone,
                physicalDone ? "COMPLETED" : "PENDING"
        );
        return resolve(orderReturn, progress, paymentSummary, paymentAvailable, refundAmount, null);
    }

    public RefundEligibilityResult resolve(
            OrderReturn orderReturn,
            OrderReturnPhysicalProgress physicalProgress,
            PaymentRefundSummaryResponse paymentSummary,
            boolean paymentAvailable,
            BigDecimal refundAmount,
            BigDecimal orderPaidAmount
    ) {
        if (orderReturn == null || !Boolean.TRUE.equals(orderReturn.getRefundRequired())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_REQUIRED,
                    OrderRefundBlockReason.REFUND_NOT_REQUIRED,
                    "Refund is not required for this return"
            );
        }

        // Check if this specific return already has a completed refund transaction
        if (paymentSummary != null && paymentSummary.refundTransactionId() != null) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.REFUNDED,
                    OrderRefundBlockReason.ALREADY_REFUNDED,
                    "Return already refunded"
            );
        }

        if (orderReturn.getStatus() == ReturnStatus.COMPLETED) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.REFUNDED,
                    OrderRefundBlockReason.ALREADY_REFUNDED,
                    "Return already refunded"
            );
        }

        if (paymentSummary != null && "FAILED".equalsIgnoreCase(paymentSummary.refundStatus())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.FAILED,
                    OrderRefundBlockReason.REFUND_FAILED,
                    "Payment refund failed"
            );
        }

        if (orderReturn.getStatus() != ReturnStatus.RECEIVED) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.RETURN_NOT_RECEIVED,
                    "Return is not in received status"
            );
        }

        if (physicalProgress == null || !physicalProgress.physicalDone()) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.PHYSICAL_DISPOSITION_NOT_COMPLETED,
                    "Return inventory inspection is not completed"
            );
        }

        if (!paymentAvailable) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.PAYMENT_SERVICE_UNAVAILABLE,
                    "Payment service is temporarily unavailable"
            );
        }

        if (paymentSummary == null || paymentSummary.paymentId() == null
                || "NOT_FOUND".equalsIgnoreCase(paymentSummary.refundStatus())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.PAYMENT_NOT_FOUND,
                    "Payment not found"
            );
        }

        if (!"CASH".equalsIgnoreCase(paymentSummary.method())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.UNSUPPORTED_PAYMENT_METHOD,
                    "Refund method is not supported yet"
            );
        }

        if (!"PAID".equalsIgnoreCase(paymentSummary.paymentStatus())
                && !"PARTIALLY_REFUNDED".equalsIgnoreCase(paymentSummary.paymentStatus())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.PAYMENT_NOT_REFUNDABLE,
                    "Payment is not eligible for refund"
            );
        }

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.NOT_READY,
                    OrderRefundBlockReason.NO_REFUND_AMOUNT,
                    "Refund amount must be greater than 0"
            );
        }

        BigDecimal amountPaid = paymentSummary.amountPaid() != null
                ? paymentSummary.amountPaid()
                : defaultZero(orderPaidAmount);
        BigDecimal totalRefunded = defaultZero(paymentSummary.totalAmountRefunded());

        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingRefundable = amountPaid.subtract(totalRefunded);
            if (remainingRefundable.compareTo(BigDecimal.ZERO) <= 0
                    || "REFUNDED".equalsIgnoreCase(paymentSummary.paymentStatus())) {
                return new RefundEligibilityResult(
                        false,
                        OrderRefundStatus.REFUNDED,
                        OrderRefundBlockReason.ALREADY_REFUNDED,
                        "Payment has already been fully refunded"
                );
            }

            if (refundAmount.compareTo(remainingRefundable) > 0) {
                return new RefundEligibilityResult(
                        false,
                        OrderRefundStatus.NOT_READY,
                        OrderRefundBlockReason.REFUND_AMOUNT_EXCEEDS_REMAINING,
                        "Refund amount exceeds remaining refundable amount"
                );
            }
        } else if ("REFUNDED".equalsIgnoreCase(paymentSummary.paymentStatus())) {
            return new RefundEligibilityResult(
                    false,
                    OrderRefundStatus.REFUNDED,
                    OrderRefundBlockReason.ALREADY_REFUNDED,
                    "Payment has already been fully refunded"
            );
        }

        return new RefundEligibilityResult(
                true,
                OrderRefundStatus.PENDING,
                null,
                null
        );
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record RefundEligibilityResult(
            boolean refundEligible,
            OrderRefundStatus refundStatus,
            OrderRefundBlockReason blockReason,
            String message
    ) {}
}
