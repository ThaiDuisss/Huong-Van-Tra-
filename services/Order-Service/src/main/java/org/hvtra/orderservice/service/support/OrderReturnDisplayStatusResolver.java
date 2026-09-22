package org.hvtra.orderservice.service.support;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderReturnDisplayStatusResolver {

    public String resolveDisplayStatus(
            ReturnStatus persistedStatus,
            OrderReturnPhysicalProgress progress,
            Boolean refundRequired,
            boolean refundCompleted
    ) {
        if (persistedStatus == null) {
            return "RECEIVED";
        }

        switch (persistedStatus) {
            case CREATED -> {
                return "CREATED";
            }
            case CANCELLED -> {
                return "CANCELLED";
            }
            case COMPLETED -> {
                return "COMPLETED";
            }
            case RECEIVED -> {
                boolean isPhysicalDone = progress != null && progress.physicalDone();
                if (isPhysicalDone) {
                    if (Boolean.FALSE.equals(refundRequired) || refundCompleted) {
                        return "COMPLETED";
                    }
                    return "PENDING_REFUND";
                }

                String invStatus = progress != null ? progress.inventoryProcessingStatus() : "PENDING";
                if ("FAILED".equalsIgnoreCase(invStatus)) {
                    return "PROCESSING_FAILED";
                }
                if ("PROCESSING".equalsIgnoreCase(invStatus)) {
                    return "PROCESSING";
                }
                return "RECEIVED";
            }
            default -> {
                return "RECEIVED";
            }
        }
    }
}
