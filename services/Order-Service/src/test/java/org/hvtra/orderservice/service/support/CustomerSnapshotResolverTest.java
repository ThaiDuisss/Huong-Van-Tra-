package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSnapshotResolverTest {

    @Mock
    private OrderCustomerService orderCustomerService;

    @InjectMocks
    private CustomerSnapshotResolver resolver;

    @Test
    void nullCustomerIdKeepsNullFallbackWithoutCallingCustomerService() {
        assertNull(resolver.resolve(null));
        verifyNoInteractions(orderCustomerService);
    }

    @Test
    void returnsCustomerSnapshotFromExistingService() {
        UUID customerId = UUID.randomUUID();
        CustomerResponse expected = new CustomerResponse();
        when(orderCustomerService.getCustomerById(customerId)).thenReturn(expected);

        CustomerResponse result = resolver.resolve(customerId);

        assertSame(expected, result);
        verify(orderCustomerService).getCustomerById(customerId);
    }

    @Test
    void runtimeFailureKeepsExistingNullFallback() {
        UUID customerId = UUID.randomUUID();
        when(orderCustomerService.getCustomerById(customerId))
                .thenThrow(new RuntimeException("customer unavailable"));

        assertNull(resolver.resolve(customerId));
        verify(orderCustomerService).getCustomerById(customerId);
    }
}
