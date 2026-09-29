package org.hvtra.orderservice.service.impl;

import org.junit.jupiter.api.Test;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OrderServiceImplDelegationTest {

    @Test
    void orderServiceImplStillUsesCanonicalConstructor() {
        assertDoesNotThrow(() -> new OrderServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void posAddDraftDelegatesToFlowWithoutPrepaymentReservation() {
        OrderDraftService draftService = mock(OrderDraftService.class);
        OrderServiceImpl service = new OrderServiceImpl(null, null, null, null, null, null,
                null, null, null, draftService, null, null, null, null);
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();

        service.addDraftItem(request);

        verify(draftService).addDraftItem(request);
    }
}
