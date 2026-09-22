package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderReturnDisplayStatusResolverTest {

    private OrderReturnDisplayStatusResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OrderReturnDisplayStatusResolver();
    }

    private OrderReturnPhysicalProgress createProgress(
            boolean physicalDone,
            String inventoryProcessingStatus
    ) {
        return new OrderReturnPhysicalProgress(
                BigDecimal.TEN,
                physicalDone ? BigDecimal.TEN : BigDecimal.ZERO,
                physicalDone ? BigDecimal.ZERO : BigDecimal.TEN,
                physicalDone,
                inventoryProcessingStatus,
                "PROCESSING".equalsIgnoreCase(inventoryProcessingStatus),
                "FAILED".equalsIgnoreCase(inventoryProcessingStatus)
        );
    }

    @Test
    @DisplayName("Case 1: ReturnStatus.CREATED -> CREATED")
    void testCreatedStatus() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.CREATED,
                createProgress(false, "PENDING"),
                true,
                false
        );
        assertEquals("CREATED", result);
    }

    @Test
    @DisplayName("Case 2: RECEIVED + physicalDone=false + PENDING -> RECEIVED")
    void testReceivedPendingStatus() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(false, "PENDING"),
                true,
                false
        );
        assertEquals("RECEIVED", result);
    }

    @Test
    @DisplayName("Case 3: RECEIVED + physicalDone=false + PROCESSING -> PROCESSING")
    void testReceivedProcessingStatus() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(false, "PROCESSING"),
                true,
                false
        );
        assertEquals("PROCESSING", result);
    }

    @Test
    @DisplayName("Case 4: RECEIVED + physicalDone=false + FAILED -> PROCESSING_FAILED")
    void testReceivedFailedStatus() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(false, "FAILED"),
                true,
                false
        );
        assertEquals("PROCESSING_FAILED", result);
    }

    @Test
    @DisplayName("Case 5: RECEIVED + physicalDone=true + refundRequired=true + refundCompleted=false -> PENDING_REFUND")
    void testReceivedPhysicalDoneRefundRequiredPending() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(true, "COMPLETED"),
                true,
                false
        );
        assertEquals("PENDING_REFUND", result);
    }

    @Test
    @DisplayName("Case 6: RECEIVED + physicalDone=true + refundRequired=true + refundCompleted=true -> COMPLETED")
    void testReceivedPhysicalDoneRefundRequiredCompleted() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(true, "COMPLETED"),
                true,
                true
        );
        assertEquals("COMPLETED", result);
    }

    @Test
    @DisplayName("Case 7: RECEIVED + physicalDone=true + refundRequired=false -> COMPLETED")
    void testReceivedPhysicalDoneNoRefundRequired() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                createProgress(true, "COMPLETED"),
                false,
                false
        );
        assertEquals("COMPLETED", result);
    }

    @Test
    @DisplayName("Case 8: COMPLETED -> COMPLETED")
    void testPersistedCompleted() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.COMPLETED,
                createProgress(true, "COMPLETED"),
                true,
                true
        );
        assertEquals("COMPLETED", result);
    }

    @Test
    @DisplayName("Case 9: CANCELLED -> CANCELLED")
    void testCancelled() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.CANCELLED,
                createProgress(false, "PENDING"),
                false,
                false
        );
        assertEquals("CANCELLED", result);
    }

    @Test
    @DisplayName("Case 10: Null persisted status returns RECEIVED fallback")
    void testNullStatus() {
        String result = resolver.resolveDisplayStatus(
                null,
                createProgress(false, "PENDING"),
                true,
                false
        );
        assertEquals("RECEIVED", result);
    }

    @Test
    @DisplayName("Case 11: Null progress with RECEIVED returns RECEIVED")
    void testNullProgress() {
        String result = resolver.resolveDisplayStatus(
                ReturnStatus.RECEIVED,
                null,
                true,
                false
        );
        assertEquals("RECEIVED", result);
    }
}
