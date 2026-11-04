package org.hvtra.orderservice.service.mapper;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderItemComponent;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.*;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderSnapshotMappingTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final OrderItemMapper items = new OrderItemMapper();
    private final OrderMapper mapper = new OrderMapper(items, new OrderAmountCalculator(), new OrderDateTimeHelper());

    @Test void responseKeepsFrozenFinancialCustomerAndNestedIngredientSnapshotsWithoutRepricing() {
        Order order = order();
        order.setSoldAt(NOW.minusSeconds(3600));
        order.setOfflineSale(true);
        order.setClientOrderId(ID);
        order.setCreatedAt(NOW);
        order.setUpdatedAt(NOW.plusSeconds(60));
        order.setBackorder(true);
        order.setExpectedAvailableAt(NOW.plusSeconds(3600));
        order.setDepositAmount(new BigDecimal("20.25"));
        OrderItem line = line();
        line.setItemType(OrderItemType.CUSTOM_BUNDLE);
        line.setPackingStatus(BundlePackingStatus.PACKED);
        line.setPackingCode("PACK-FROZEN");
        OrderItemComponent component = new OrderItemComponent();
        component.setIngredientId(ID);
        component.setIngredientCodeSnapshot("TEA");
        component.setIngredientNameSnapshot("Tea at sale");
        component.setUnitSnapshot("g");
        component.setQuantity(new BigDecimal("50.125"));
        component.setUnitPrice(new BigDecimal("0.75"));
        component.setLineTotal(new BigDecimal("37.59"));
        line.replaceComponents(List.of(component));
        order.addItem(line);
        PaymentResponse payment = PaymentResponse.builder().paymentCode("PAY-FROZEN").build();
        var result = mapper.toResponse(order, payment, new BigDecimal("0.005"), "BANK_TRANSFER");
        assertSame(payment, result.getPayment());
        assertEquals(order.getFinalAmount(), result.getFinalAmount());
        assertEquals(order.getOriginalAmount(), result.getSubTotal());
        assertEquals(order.getDiscountAmount(), result.getDiscountAmount());
        assertEquals(order.getShippingFee(), result.getShippingFee());
        assertEquals(order.getPaidAmount(), result.getPaidAmount());
        assertEquals(order.getDebtAmount(), result.getDebtAmount());
        assertEquals(new BigDecimal("0.005"), result.getChangeAmount());
        assertEquals("Customer at sale", result.getCustomerName());
        assertEquals("Branch at sale", result.getBranchName());
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 0), result.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 0), result.getSoldAt());
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 1), result.getUpdatedAt());
        assertTrue(result.isOfflineSale());
        assertEquals(ID, result.getClientOrderId());
        assertTrue(result.isBackorder());
        assertEquals(order.getExpectedAvailableAt(), result.getExpectedAvailableAt());
        var nested = result.getItems().getFirst();
        assertEquals(line.getActualUnitPrice(), nested.getUnitPrice());
        assertEquals("Tea frozen", nested.getProductName());
        assertEquals(line.getSkuSnapshot(), nested.getSku());
        assertEquals(BundlePackingStatus.PACKED, nested.getPackingStatus());
        assertEquals("PACK-FROZEN", nested.getPackingCode());
        var ingredient = nested.getComponents().getFirst();
        assertEquals(ID, ingredient.getIngredientId());
        assertEquals("Tea at sale", ingredient.getIngredientName());
        assertEquals("g", ingredient.getUnit());
        assertEquals(new BigDecimal("50.125"), ingredient.getQuantity());
        assertEquals(new BigDecimal("37.59"), ingredient.getLineTotal());
        assertEquals(new BigDecimal("0.75"), ingredient.getUnitPrice());
    }

    @ParameterizedTest @CsvSource({"DRAFT,Nháp", "PENDING_PAYMENT,Chờ thanh toán", "PAYMENT_CONFIRMED,Chờ duyệt",
            "COMPLETED,Đã hoàn thành", "CANCELLED,Đã hủy", "EXPIRED,Đã hết hạn", "PAYMENT_FAILED,Thanh toán thất bại"})
    void summaryAndHistoryExposeConsistentBusinessStatusLabels(OrderStatus status, String label) {
        Order order = order();
        order.setStatus(status);
        var summary = mapper.toSummaryResponse(order);
        OrderStatusHistory history = OrderStatusHistory.builder().fromStatus(null).toStatus(status)
                .changedBy(ID).changedAt(NOW).reason("Audit reason").build();
        var audit = mapper.toStatusHistoryResponse(history);
        assertEquals(status, summary.getStatus());
        assertEquals(label, summary.getStatusLabel());
        assertEquals(status, audit.getNewStatus());
        assertEquals(label, audit.getNewStatusLabel());
        assertNull(audit.getOldStatusLabel());
        assertEquals("Audit reason", audit.getNote());
        assertEquals(ID.toString(), audit.getCreatedBy());
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 0), audit.getCreatedAt());
    }

    @ParameterizedTest @CsvSource({"UNPAID,Chưa thanh toán", "WAITING_PAYMENT,Chưa thanh toán", "PAID,Đã thanh toán",
            "FAILED,Thanh toán thất bại", "REFUNDED,Đã hoàn tiền", "PARTIALLY_REFUNDED,Hoàn tiền một phần",
            "PARTIALLY_PAID,Đã đặt cọc", "NEED_REVIEW,Chờ kiểm tra thanh toán"})
    void summaryUsesFinancialStatusRatherThanInferringFromOrderStatus(PaymentStatus status, String label) {
        Order order = order();
        order.setPaymentStatus(status);
        var result = mapper.toSummaryResponse(order);
        assertEquals(label, result.getPaymentStatusLabel());
        assertEquals(status, result.getPaymentStatus());
        assertEquals(order.getFinalAmount(), result.getFinalAmount());
    }

    @ParameterizedTest @CsvSource({"CASH,Tiền mặt", "BANK_TRANSFER,Chuyển khoản", "QR_CODE,Chuyển khoản",
            "DEBT,Công nợ", "CARD,Thẻ", "E_WALLET,Ví điện tử", "COMBINED,Kết hợp", "CUSTOM,CUSTOM"})
    void todaySalesKeepsDecimalQuantityAndKnownOrCustomMethodLabels(String method, String label) {
        Order order = order();
        order.addItem(line());
        var result = mapper.toTodaySalesItemResponse(order, method);
        assertEquals(new BigDecimal("1.250"), result.getTotalItems());
        assertEquals(new BigDecimal("123.4500"), result.getFinalAmount());
        assertEquals(method, result.getPaymentMethod());
        assertEquals(label, result.getPaymentMethodLabel());
    }

    @ParameterizedTest @CsvSource({"POS,Bán tại quầy", "online,Đơn online", "RETURN,Hoàn hàng", "REFUND,Hoàn tiền",
            "EXCHANGE,Đổi hàng", "CUSTOM,CUSTOM"})
    void orderTypeLabelsDoNotChangePersistedOrderIdentity(String type, String label) {
        Order order = order();
        order.setOrderType(type);
        var result = mapper.toSummaryResponse(order);
        assertEquals(type, result.getOrderType());
        assertEquals(label, result.getOrderTypeLabel());
        assertEquals(ID, result.getId());
    }

    @ParameterizedTest @CsvSource({"NONE,Chưa hoàn hàng", "PARTIALLY_RETURNED,Hoàn một phần", "FULLY_RETURNED,Hoàn toàn bộ"})
    void orderReturnLabelsRemainDistinctFromRefundState(OrderReturnStatus status, String label) {
        Order order = order();
        order.setReturnStatus(status);
        var result = mapper.toSummaryResponse(order);
        assertEquals(label, result.getReturnStatusLabel());
        assertEquals(status, result.getReturnStatus());
    }

    @Test void nullableAuditAndStatusFieldsStayNullAndSoldAtDefaultsToCreation() {
        Order order = new Order();
        order.setReturnStatus(null);
        order.setStatus(null);
        order.setPaymentStatus(null);
        order.setCreatedAt(NOW);
        var result = mapper.toResponse(order, null, null, null);
        assertNull(result.getStatusLabel());
        assertNull(result.getPaymentStatusLabel());
        assertNull(result.getReturnStatusLabel());
        assertNull(result.getOrderTypeLabel());
        assertNull(result.getPaymentMethodLabel());
        assertNull(result.getCreatedBy());
        assertNull(result.getUpdatedAt());
        assertEquals(result.getCreatedAt(), result.getSoldAt());
        assertTrue(result.getItems().isEmpty());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "  fragile  "})
    void draftItemNormalizesNoteAndKeepsCatalogSnapshotAndExactDecimalMoney(String note) {
        CatalogClient.ProductVariantPosViewResponse variant = new CatalogClient.ProductVariantPosViewResponse(
                ID, ID, "SKU", "Tea frozen", "100g", "Tea", new BigDecimal("999"), BigDecimal.TEN, null);
        var result = items.toDraftOrderItem(variant, new BigDecimal("1.250"), new BigDecimal("12.345"),
                new BigDecimal("0.125"), ID, note);
        assertEquals("Tea frozen", result.getProductNameSnapshot());
        assertEquals("100g", result.getVariantNameSnapshot());
        assertEquals(new BigDecimal("12.345"), result.getActualUnitPrice());
        assertEquals(result.getActualUnitPrice(), result.getOriginalUnitPrice());
        assertEquals(new BigDecimal("1.250"), result.getQuantity());
        assertEquals(new BigDecimal("0.125"), result.getDiscountAmount());
        assertEquals(ID, result.getBomVersionId());
        assertEquals(note == null || note.isBlank() ? null : "fragile", result.getNote());
        assertNull(items.toDraftOrderItem(variant, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, null).getNote());
        var response = items.toResponse(result);
        assertEquals(result.getProductVariantId(), response.getProductVariantId());
        assertTrue(response.getComponents().isEmpty());
    }

    @Test void headerCustomerFallbackKeepsCallerSnapshotOnlyWhenAuthoritativeValuesAreBlank() {
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();
        request.setCustomerId(ID);
        request.setBranchId(ID);
        request.setCustomerNameSnapshot("Cached name");
        request.setCustomerPhoneSnapshot("Cached phone");
        CustomerResponse authoritative = new CustomerResponse();
        authoritative.setFullName(" ");
        authoritative.setPhone("Verified phone");
        authoritative.setEmail("Verified email");
        var result = mapper.toDraftOrder(request, authoritative, "ORD-UNIT", NOW.plusSeconds(900));
        assertEquals("Cached name", result.getCustomerNameSnapshot());
        assertEquals("Verified phone", result.getCustomerPhoneSnapshot());
        assertEquals("Verified email", result.getCustomerEmailSnapshot());
        assertEquals(ID, result.getBranchId());
        assertEquals(OrderStatus.DRAFT, result.getStatus());
        assertEquals(PaymentStatus.WAITING_PAYMENT, result.getPaymentStatus());
        assertEquals(NOW.plusSeconds(900), result.getExpiresAt());
        assertEquals(BigDecimal.ZERO, result.getPaidAmount());
        var fallback = mapper.toDraftOrder(request, null, "ORD-UNIT", NOW);
        assertEquals("Cached phone", fallback.getCustomerPhoneSnapshot());
        assertNull(fallback.getCustomerEmailSnapshot());
    }

    private Order order() {
        Order order = Order.builder().orderCode("ORD-SNAPSHOT").orderType("POS").status(OrderStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID).returnStatus(OrderReturnStatus.NONE).customerNameSnapshot("Customer at sale")
                .branchNameSnapshot("Branch at sale").originalAmount(new BigDecimal("130.0000"))
                .discountAmount(new BigDecimal("10.0000")).shippingFee(new BigDecimal("3.4500"))
                .finalAmount(new BigDecimal("123.4500")).paidAmount(new BigDecimal("123.4500")).debtAmount(BigDecimal.ZERO).build();
        order.setId(ID);
        return order;
    }
    private OrderItem line() {
        OrderItem item = OrderItem.builder().productVariantId(ID).skuSnapshot("SKU-FROZEN")
                .productNameSnapshot("Tea frozen").variantNameSnapshot("100g frozen").quantity(new BigDecimal("1.250"))
                .originalUnitPrice(new BigDecimal("12.345")).actualUnitPrice(new BigDecimal("10.125"))
                .discountAmount(new BigDecimal("2.775")).lineTotal(new BigDecimal("9.88")).build();
        item.setId(ID);
        return item;
    }
}
