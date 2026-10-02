package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;

public final class RefundProgressStatusResolver {

    private RefundProgressStatusResolver() {
    }

    public static RefundProgressStatus resolve(OrderRefundSummaryResponse row) {
        OrderRefundStatus refundStatus = row.getRefundStatus();
        OrderRefundBlockReason blockReason = row.getRefundBlockReason();
        ReturnStatus returnStatus = row.getReturnStatus();
        String inventoryStatus = row.getInventoryProcessingStatus();

        if (refundStatus == OrderRefundStatus.REFUNDED
                || blockReason == OrderRefundBlockReason.ALREADY_REFUNDED) {
            return RefundProgressStatus.REFUNDED;
        }
        if (refundStatus == OrderRefundStatus.FAILED
                || blockReason == OrderRefundBlockReason.REFUND_FAILED
                || "FAILED".equalsIgnoreCase(inventoryStatus)
                || "PROCESSING_FAILED".equalsIgnoreCase(row.getDisplayStatus())) {
            return RefundProgressStatus.FAILED;
        }
        if (refundStatus == OrderRefundStatus.NOT_REQUIRED
                || Boolean.FALSE.equals(row.getRefundRequired())
                || returnStatus == ReturnStatus.CANCELLED) {
            return RefundProgressStatus.OTHER;
        }
        if (refundStatus == OrderRefundStatus.PENDING || Boolean.TRUE.equals(row.getRefundEligible())) {
            return RefundProgressStatus.WAITING_REFUND;
        }
        if (returnStatus == ReturnStatus.CREATED
                || blockReason == OrderRefundBlockReason.RETURN_NOT_RECEIVED) {
            return RefundProgressStatus.WAITING_RECEIPT;
        }
        boolean waitingForPhysicalWork = Boolean.FALSE.equals(row.getPhysicalDone())
                || blockReason == OrderRefundBlockReason.PHYSICAL_DISPOSITION_NOT_COMPLETED
                || "PENDING".equalsIgnoreCase(inventoryStatus)
                || "PROCESSING".equalsIgnoreCase(inventoryStatus);
        boolean physicalBlockOnly = blockReason == null
                || blockReason == OrderRefundBlockReason.PHYSICAL_DISPOSITION_NOT_COMPLETED;
        if (returnStatus == ReturnStatus.RECEIVED && waitingForPhysicalWork && physicalBlockOnly) {
            return RefundProgressStatus.WAITING_INSPECTION;
        }
        return RefundProgressStatus.OTHER;
    }
}
