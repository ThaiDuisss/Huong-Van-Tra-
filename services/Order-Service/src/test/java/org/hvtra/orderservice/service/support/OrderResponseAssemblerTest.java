package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderResponseAssemblerTest {

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderAmountCalculator orderAmountCalculator;

    @Mock
    private OrderStatusHistoryReader orderStatusHistoryReader;

    private OrderResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new OrderResponseAssembler(
                paymentClient,
                orderMapper,
                orderAmountCalculator,
                orderStatusHistoryReader
        );
    }

    @Test
    void toResponseKeepsPaymentLookupChangeAmountMapperAndHistoryOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-001")
                .build();
        PaymentClient.PaymentOrderInfoResponse paymentInfo = paymentInfo(
                orderId,
                new BigDecimal("5000.00"),
                "qr_code"
        );
        BigDecimal changeAmount = new BigDecimal("5000.00");
        OrderResponse mapped = OrderResponse.builder().id(orderId).build();
        OrderStatusHistoryResponse history = OrderStatusHistoryResponse.builder()
                .id(UUID.randomUUID())
                .build();
        when(paymentClient.getPaymentByOrderId(orderId)).thenReturn(paymentInfo);
        when(orderAmountCalculator.defaultZero(paymentInfo.changeAmount())).thenReturn(changeAmount);
        when(orderMapper.toResponse(order, payment, changeAmount, "BANK_TRANSFER")).thenReturn(mapped);
        when(orderStatusHistoryReader.findResponses(orderId)).thenReturn(List.of(history));

        OrderResponse response = assembler.toResponse(order, payment);

        assertSame(mapped, response);
        assertEquals(List.of(history), response.getStatusHistories());
        InOrder inOrder = inOrder(paymentClient, orderAmountCalculator, orderMapper, orderStatusHistoryReader);
        inOrder.verify(paymentClient).getPaymentByOrderId(orderId);
        inOrder.verify(orderAmountCalculator).defaultZero(paymentInfo.changeAmount());
        inOrder.verify(orderMapper).toResponse(order, payment, changeAmount, "BANK_TRANSFER");
        inOrder.verify(orderStatusHistoryReader).findResponses(orderId);
    }

    @Test
    void toResponseKeepsExistingPaymentFallbackWhenPaymentLookupFails() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        order.setPaymentMethod(" cash ");
        OrderResponse mapped = OrderResponse.builder().id(orderId).build();
        when(paymentClient.getPaymentByOrderId(orderId)).thenThrow(new RuntimeException("Payment down"));
        when(orderMapper.toResponse(order, null, BigDecimal.ZERO, "CASH")).thenReturn(mapped);
        when(orderStatusHistoryReader.findResponses(orderId)).thenReturn(List.of());

        OrderResponse response = assembler.toResponse(order);

        assertSame(mapped, response);
        assertEquals(List.of(), response.getStatusHistories());
        InOrder inOrder = inOrder(paymentClient, orderMapper, orderStatusHistoryReader);
        inOrder.verify(paymentClient).getPaymentByOrderId(orderId);
        inOrder.verify(orderMapper).toResponse(order, null, BigDecimal.ZERO, "CASH");
        inOrder.verify(orderStatusHistoryReader).findResponses(orderId);
        verifyNoInteractions(orderAmountCalculator);
    }

    @Test
    void toTodaySalesItemResponseUsesSharedPaymentMethodResolution() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        PaymentClient.PaymentOrderInfoResponse paymentInfo = paymentInfo(
                orderId,
                BigDecimal.ZERO,
                "transfer"
        );
        TodaySalesItemResponse mapped = TodaySalesItemResponse.builder()
                .orderId(orderId)
                .paymentMethod("BANK_TRANSFER")
                .build();
        when(paymentClient.getPaymentByOrderId(orderId)).thenReturn(paymentInfo);
        when(orderMapper.toTodaySalesItemResponse(order, "BANK_TRANSFER")).thenReturn(mapped);

        TodaySalesItemResponse response = assembler.toTodaySalesItemResponse(order);

        assertSame(mapped, response);
        InOrder inOrder = inOrder(paymentClient, orderMapper);
        inOrder.verify(paymentClient).getPaymentByOrderId(orderId);
        inOrder.verify(orderMapper).toTodaySalesItemResponse(order, "BANK_TRANSFER");
        verifyNoInteractions(orderAmountCalculator, orderStatusHistoryReader);
    }

    @Test
    void paymentMethodNormalizationAndFilterKeepExistingAliases() {
        assertNull(assembler.normalizePaymentMethod(" "));
        assertEquals("BANK_TRANSFER", assembler.normalizePaymentMethod("bank"));
        assertEquals("BANK_TRANSFER", assembler.normalizePaymentMethod("QR_CODE"));
        assertEquals("DEBT", assembler.normalizePaymentMethod("credit"));
        assertEquals("CARD", assembler.normalizePaymentMethod("card"));

        TodaySalesItemResponse bankItem = TodaySalesItemResponse.builder()
                .paymentMethod("transfer")
                .build();

        assertTrue(assembler.paymentMethodMatches(bankItem, null));
        assertTrue(assembler.paymentMethodMatches(bankItem, "bank"));
        assertFalse(assembler.paymentMethodMatches(bankItem, "cash"));
    }

    private Order order(UUID orderId) {
        Order order = Order.builder()
                .paymentStatus(PaymentStatus.PAID)
                .debtAmount(BigDecimal.ZERO)
                .build();
        order.setId(orderId);
        return order;
    }

    private PaymentClient.PaymentOrderInfoResponse paymentInfo(
            UUID orderId,
            BigDecimal changeAmount,
            String method
    ) {
        return new PaymentClient.PaymentOrderInfoResponse(
                UUID.randomUUID(),
                "PAY-001",
                orderId,
                new BigDecimal("100000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("105000.00"),
                changeAmount,
                "PAID",
                method,
                "Chuyển khoản"
        );
    }
}
