package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundProgressStatusResolverTest {
    @ParameterizedTest
    @CsvSource({
            "REFUNDED,REFUND_FAILED,RECEIVED,true,false,false,FAILED,PROCESSING_FAILED,REFUNDED",
            "FAILED,ALREADY_REFUNDED,RECEIVED,true,false,false,,,REFUNDED",
            "FAILED,,RECEIVED,false,false,false,,,FAILED",
            "NOT_READY,REFUND_FAILED,RECEIVED,true,false,false,,,FAILED",
            "NOT_READY,,RECEIVED,true,false,false,failed,,FAILED",
            "NOT_READY,,RECEIVED,true,false,false,,processing_failed,FAILED",
            "NOT_REQUIRED,,RECEIVED,true,true,true,,,OTHER",
            "NOT_READY,,RECEIVED,false,true,false,,,OTHER",
            "NOT_READY,,CANCELLED,true,true,false,,,OTHER",
            "PENDING,,RECEIVED,true,false,true,,,WAITING_REFUND",
            "NOT_READY,,RECEIVED,true,true,true,,,WAITING_REFUND",
            "NOT_READY,,CREATED,true,false,false,,,WAITING_RECEIPT",
            "NOT_READY,RETURN_NOT_RECEIVED,RECEIVED,true,false,false,,,WAITING_RECEIPT",
            "NOT_READY,,RECEIVED,true,false,false,,,WAITING_INSPECTION",
            "NOT_READY,PHYSICAL_DISPOSITION_NOT_COMPLETED,RECEIVED,true,false,true,,,WAITING_INSPECTION",
            "NOT_READY,,RECEIVED,true,false,true,pending,,WAITING_INSPECTION",
            "NOT_READY,,RECEIVED,true,false,true,processing,,WAITING_INSPECTION",
            "NOT_READY,PAYMENT_SERVICE_UNAVAILABLE,RECEIVED,true,false,false,PENDING,,OTHER",
            "NOT_READY,,RECEIVED,true,false,true,PROCESSED,,OTHER",
            ",,,,,,,,OTHER"
    })
    void dashboardGroupingRespectsRefundFailureAndPhysicalWorkPrecedence(
            OrderRefundStatus refund, OrderRefundBlockReason block, ReturnStatus status,
            Boolean required, Boolean eligible, Boolean physicalDone, String inventory, String display,
            RefundProgressStatus expected) {
        var row = OrderRefundSummaryResponse.builder().refundStatus(refund).refundBlockReason(block)
                .returnStatus(status).refundRequired(required).refundEligible(eligible).physicalDone(physicalDone)
                .inventoryProcessingStatus(inventory).displayStatus(display).build();
        assertEquals(expected, RefundProgressStatusResolver.resolve(row));
    }
}
