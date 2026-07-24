package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCancellationService;
import org.hvtra.orderservice.service.OrderCheckoutService;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentService;
import org.hvtra.orderservice.service.OrderQueryService;
import org.hvtra.orderservice.service.OrderStatusManagementService;
import org.hvtra.orderservice.service.TodaySalesService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplDelegationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCustomerServiceImpl orderCustomerServiceImpl;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    @Mock
    private OrderPaymentSagaService orderPaymentSagaService;

    @Mock
    private OrderPaymentService orderPaymentService;

    @Mock
    private OrderCheckoutService orderCheckoutService;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private TodaySalesService todaySalesService;

    @Mock
    private OrderDraftService orderDraftService;

    @Mock
    private OrderCancellationService orderCancellationService;

    @Mock
    private OrderStatusManagementService orderStatusManagementService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderAmountCalculator orderAmountCalculator;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(
                orderRepository,
                orderCustomerServiceImpl,
                orderOutboxPublisher,
                orderPaymentSagaService,
                orderPaymentService,
                orderCheckoutService,
                orderQueryService,
                todaySalesService,
                orderDraftService,
                orderCancellationService,
                orderStatusManagementService,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }

    @Test
    void delegatesExtractedUseCasesToDedicatedServices() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CreateOrderRequest createOrderRequest = new CreateOrderRequest();
        MarkOrderPaidRequest markOrderPaidRequest = new MarkOrderPaidRequest();
        AddDraftOrderItemRequest addDraftRequest = new AddDraftOrderItemRequest();
        UpdateDraftOrderItemQuantityRequest updateDraftRequest = new UpdateDraftOrderItemQuantityRequest();
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
        UpdateOrderStatusRequest updateStatusRequest = new UpdateOrderStatusRequest();
        OrderResponse createOrderResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse markOrderPaidResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse addDraftResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse updateDraftResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse removeDraftResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse cancelDraftResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse cancelOrderResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        OrderResponse updateStatusResponse = OrderResponse.builder().id(UUID.randomUUID()).build();
        when(orderCheckoutService.createOrder(createOrderRequest)).thenReturn(createOrderResponse);
        when(orderPaymentService.markOrderPaid(orderId, markOrderPaidRequest)).thenReturn(markOrderPaidResponse);
        when(orderDraftService.addDraftItem(addDraftRequest)).thenReturn(addDraftResponse);
        when(orderDraftService.updateDraftItemQuantity(orderId, itemId, updateDraftRequest))
                .thenReturn(updateDraftResponse);
        when(orderDraftService.removeDraftItem(orderId, itemId)).thenReturn(removeDraftResponse);
        when(orderDraftService.cancelDraftOrder(orderId)).thenReturn(cancelDraftResponse);
        when(orderDraftService.checkoutDraftOrder(orderId, null)).thenReturn(cancelDraftResponse);
        when(orderCancellationService.cancelOrder(orderId, cancelOrderRequest)).thenReturn(cancelOrderResponse);
        when(orderStatusManagementService.updateOrderStatus(orderId, updateStatusRequest))
                .thenReturn(updateStatusResponse);

        assertSame(createOrderResponse, service.createOrder(createOrderRequest));
        assertSame(markOrderPaidResponse, service.markOrderPaid(orderId, markOrderPaidRequest));
        assertSame(addDraftResponse, service.addDraftItem(addDraftRequest));
        assertSame(updateDraftResponse, service.updateDraftItemQuantity(orderId, itemId, updateDraftRequest));
        assertSame(removeDraftResponse, service.removeDraftItem(orderId, itemId));
        assertSame(cancelDraftResponse, service.cancelDraftOrder(orderId));
        assertSame(cancelDraftResponse, service.checkoutDraftOrder(orderId, null));
        assertSame(cancelOrderResponse, service.cancelOrder(orderId, cancelOrderRequest));
        assertSame(updateStatusResponse, service.updateOrderStatus(orderId, updateStatusRequest));

        verify(orderCheckoutService).createOrder(createOrderRequest);
        verify(orderPaymentService).markOrderPaid(orderId, markOrderPaidRequest);
        verify(orderDraftService).addDraftItem(addDraftRequest);
        verify(orderDraftService).updateDraftItemQuantity(orderId, itemId, updateDraftRequest);
        verify(orderDraftService).removeDraftItem(orderId, itemId);
        verify(orderDraftService).cancelDraftOrder(orderId);
        verify(orderDraftService).checkoutDraftOrder(orderId, null);
        verify(orderCancellationService).cancelOrder(orderId, cancelOrderRequest);
        verify(orderStatusManagementService).updateOrderStatus(orderId, updateStatusRequest);
        verifyNoInteractions(
                orderRepository,
                orderValidator,
                orderAmountCalculator,
                orderStatusHistoryWriter,
                orderResponseAssembler
        );
    }
}
