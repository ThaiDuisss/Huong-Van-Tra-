package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosOfflineCheckoutServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PosOfflineCheckoutServiceImpl service;

    @Test
    void rejectsRequestWithoutClientOrderId() {
        CreateOrderRequest request = baseRequest();
        request.setClientOrderId(null);

        assertThrows(InvalidOrderStatusException.class, () -> service.checkout(request));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void rejectsRequestBoundToServerDraft() {
        CreateOrderRequest request = baseRequest();
        request.setOrderId(UUID.randomUUID());

        assertThrows(InvalidOrderStatusException.class, () -> service.checkout(request));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void replayReturnsExistingOrderWithoutCreating() {
        CreateOrderRequest request = baseRequest();
        UUID orderId = UUID.randomUUID();
        Order existing = Order.builder().build();
        existing.setId(orderId);
        OrderResponse response = OrderResponse.builder().id(orderId).orderCode("ORD1").build();

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.of(existing));
        when(orderService.getOrderById(orderId)).thenReturn(response);

        assertSame(response, service.checkout(request));
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void firstCallCreatesOrderMarkedOfflineWithSoldAtDefault() {
        CreateOrderRequest request = baseRequest();
        request.setSoldAt(null);
        OrderResponse response = OrderResponse.builder().orderCode("ORD2").build();

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.empty());
        when(orderService.createOrder(request)).thenReturn(response);

        assertSame(response, service.checkout(request));
        assertTrue(request.getOfflineSale());
        assertNotNull(request.getSoldAt());
    }

    @Test
    void keepsExplicitOnlineFlagForLocalCartCheckout() {
        CreateOrderRequest request = baseRequest();
        request.setOfflineSale(Boolean.FALSE);

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.empty());
        when(orderService.createOrder(request)).thenReturn(OrderResponse.builder().build());

        service.checkout(request);
        assertEquals(Boolean.FALSE, request.getOfflineSale());
    }

    @Test
    void keepsClientSoldAtWhenProvided() {
        CreateOrderRequest request = baseRequest();
        Instant soldAt = Instant.parse("2026-10-09T03:15:00Z");
        request.setSoldAt(soldAt);

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.empty());
        when(orderService.createOrder(request)).thenReturn(OrderResponse.builder().build());

        service.checkout(request);
        assertEquals(soldAt, request.getSoldAt());
    }

    @Test
    void uniqueKeyRaceReturnsOrderCreatedByOtherRequest() {
        CreateOrderRequest request = baseRequest();
        UUID orderId = UUID.randomUUID();
        Order winner = Order.builder().build();
        winner.setId(orderId);
        OrderResponse response = OrderResponse.builder().id(orderId).build();

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(orderService.createOrder(request))
                .thenThrow(new DataIntegrityViolationException("uk_orders_client_order_id"));
        when(orderService.getOrderById(orderId)).thenReturn(response);

        assertSame(response, service.checkout(request));
    }

    @Test
    void unrelatedIntegrityErrorIsRethrown() {
        CreateOrderRequest request = baseRequest();

        when(orderRepository.findByClientOrderIdAndIsDeletedFalse(request.getClientOrderId()))
                .thenReturn(Optional.empty());
        when(orderService.createOrder(request))
                .thenThrow(new DataIntegrityViolationException("other"));

        assertThrows(DataIntegrityViolationException.class, () -> service.checkout(request));
    }

    private CreateOrderRequest baseRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientOrderId(UUID.randomUUID());
        request.setCashierId(UUID.randomUUID());
        request.setSoldAt(Instant.parse("2026-10-09T02:00:00Z"));
        request.setPaymentMethod("CASH");
        return request;
    }
}
