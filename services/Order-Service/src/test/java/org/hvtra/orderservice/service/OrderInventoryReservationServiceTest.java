package org.hvtra.orderservice.service;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.exception.InventoryReservationFailedException;
import org.hvtra.orderservice.exception.OutOfStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderInventoryReservationServiceTest {
    @Mock private InventoryClient inventory;
    @InjectMocks private OrderInventoryReservationService service;
    private final UUID variantId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @Test
    void reservesExactTargetQuantityWithOrderBranchAndExpiry() {
        Order order = order();
        service.applyReservation(order, variantId, new BigDecimal("3.5"), locationId);
        ArgumentCaptor<InventoryClient.ApplyInventoryReservationRequest> request =
                ArgumentCaptor.forClass(InventoryClient.ApplyInventoryReservationRequest.class);
        verify(inventory).applyReservation(request.capture());
        assertEquals(order.getId(), request.getValue().orderId());
        assertEquals(order.getBranchId(), request.getValue().branchId());
        assertEquals(order.getExpiresAt(), request.getValue().expiresAt());
        assertEquals(variantId, request.getValue().productVariantId());
        assertEquals(locationId, request.getValue().locationId());
        assertEquals(new BigDecimal("3.5"), request.getValue().targetQuantity());
        verifyNoMoreInteractions(inventory);
    }

    @ParameterizedTest
    @CsvSource({"409, true", "404, false", "503, false"})
    void translatesRawFeignFailuresWithoutRetryingReservation(int status, boolean outOfStock) {
        doThrow(feignFailure(status)).when(inventory).applyReservation(any());
        Class<? extends RuntimeException> expected = outOfStock ? OutOfStockException.class : InventoryReservationFailedException.class;
        assertThrows(expected, () -> service.applyReservation(order(), variantId, BigDecimal.ONE, locationId));
        verify(inventory).applyReservation(any());
    }

    @ParameterizedTest
    @CsvSource(value = {"409|unavailable||true", "500|OUT_OF_STOCK||true", "500|unavailable|OUT_OF_STOCK|true",
            "404|missing||false", "503|unavailable||false"}, delimiter = '|')
    void translatesDecodedFeignErrorsFromStatusMessageOrBody(int status, String message, String body, boolean stock) {
        doThrow(new FeignClientException(status, "apply", message, body)).when(inventory).applyReservation(any());
        Class<? extends RuntimeException> expected = stock ? OutOfStockException.class : InventoryReservationFailedException.class;
        assertThrows(expected, () -> service.applyReservation(order(), variantId, BigDecimal.ONE, locationId));
        verify(inventory).applyReservation(any());
    }

    @Test
    void releaseUsesExactItemAndOrderIdentity() {
        UUID orderId = UUID.randomUUID();
        service.releaseItemReservation(orderId, variantId, locationId);
        service.releaseOrderReservation(orderId);
        verify(inventory).releaseItem(InventoryClient.ReleaseInventoryReservationItemRequest.builder()
                .orderId(orderId).productVariantId(variantId).locationId(locationId).build());
        verify(inventory).releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder().orderId(orderId).build());
    }

    @Test
    void decodedReleaseErrorsAreNotSwallowed() {
        doThrow(new FeignClientException(503, "release", null, null)).when(inventory).releaseItem(any());
        doThrow(new FeignClientException(503, "release", null, null)).when(inventory).releaseOrder(any());
        assertThrows(InventoryReservationFailedException.class, () -> service.releaseItemReservation(UUID.randomUUID(), variantId, locationId));
        assertThrows(InventoryReservationFailedException.class, () -> service.releaseOrderReservation(UUID.randomUUID()));
    }

    @Test
    void rawReleaseErrorsAreNotSwallowed() {
        doThrow(feignFailure(503)).when(inventory).releaseItem(any());
        doThrow(feignFailure(503)).when(inventory).releaseOrder(any());
        assertThrows(InventoryReservationFailedException.class, () -> service.releaseItemReservation(UUID.randomUUID(), variantId, locationId));
        assertThrows(InventoryReservationFailedException.class, () -> service.releaseOrderReservation(UUID.randomUUID()));
    }

    private Order order() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setBranchId(UUID.randomUUID());
        order.setExpiresAt(Instant.parse("2026-11-03T01:00:00Z"));
        return order;
    }

    private FeignException feignFailure(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://unit.invalid/reservation", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("reservation", Response.builder().request(request).status(status).headers(Map.of()).build());
    }
}
