package org.hvtra.customer.integration.consumer;

import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.service.LoyaltyTierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderEventConsumerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 15, 10, 0);

    private LoyaltyTierService loyaltyTierService;
    private OrderCompletedConsumer completedConsumer;
    private OrderRefundedConsumer refundedConsumer;

    @BeforeEach
    void setUp() {
        loyaltyTierService = mock(LoyaltyTierService.class);
        completedConsumer = new OrderCompletedConsumer(loyaltyTierService);
        refundedConsumer = new OrderRefundedConsumer(loyaltyTierService);
    }

    @Test
    void completedEventIsForwardedToLoyaltyTierService() {
        OrderCompletedEvent event = new OrderCompletedEvent(
                UUID.randomUUID(), "DH-1", UUID.randomUUID(), BigDecimal.TEN, AT);

        completedConsumer.handle(event);

        verify(loyaltyTierService).recordPurchase(event);
    }

    @Test
    void completedEventForGuestOrderIsIgnored() {
        completedConsumer.handle(new OrderCompletedEvent(UUID.randomUUID(), "DH-1", null, BigDecimal.TEN, AT));
        completedConsumer.handle(new OrderCompletedEvent(null, "DH-1", UUID.randomUUID(), BigDecimal.TEN, AT));
        completedConsumer.handle(null);

        verifyNoInteractions(loyaltyTierService);
    }

    @Test
    void refundEventsAreForwardedToLoyaltyTierService() {
        OrderPartiallyRefundedEvent partial = new OrderPartiallyRefundedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, AT, null);
        OrderFullyRefundedEvent full = new OrderFullyRefundedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, AT, null);

        refundedConsumer.handlePartialRefund(partial);
        refundedConsumer.handleFullRefund(full);

        verify(loyaltyTierService).recordPartialRefund(partial);
        verify(loyaltyTierService).recordFullRefund(full);
    }

    @Test
    void refundEventsWithoutCustomerOrRefundIdAreIgnored() {
        refundedConsumer.handlePartialRefund(new OrderPartiallyRefundedEvent(
                UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.ONE, AT, null));
        refundedConsumer.handleFullRefund(new OrderFullyRefundedEvent(
                UUID.randomUUID(), null, UUID.randomUUID(), BigDecimal.TEN, AT, null));
        refundedConsumer.handlePartialRefund(null);
        refundedConsumer.handleFullRefund(null);

        verifyNoInteractions(loyaltyTierService);
    }
}
