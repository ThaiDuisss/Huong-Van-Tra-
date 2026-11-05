package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.*;
import org.hvtra.orderservice.domain.enums.*;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.*;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hvtra.orderservice.service.query.OrderQueryCriteriaBuilder;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCustomBundleSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Builds real Hibernate criteria in memory only: no connection provider, schema generation or JDBC. */
@ExtendWith(MockitoExtension.class)
class OrderFacadeReadContractTest {
    private static SessionFactory factory;
    private static StandardServiceRegistry registry;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository histories;
    @Mock CatalogClient catalog;
    @Mock PaymentClient payment;
    @Mock OrderCustomerServiceImpl customers;
    @Mock OrderOutboxPublisher outbox;
    @Mock OrderInventoryReservationService reservation;
    @Mock OrderPaymentSagaService saga;
    @Mock OrderDiscountRepository discounts;
    @Mock OrderDraftService drafts;
    @Mock OrderPaymentEventService events;
    @Mock OrderStatusManagementService statuses;
    @Mock OrderCheckoutPaymentProcessor checkout;
    @Mock OrderPaymentService payments;
    @InjectMocks OrderServiceImpl service;

    @BeforeAll static void metamodel() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        factory = new MetadataSources(registry).addAnnotatedClass(Order.class).addAnnotatedClass(OrderItem.class)
                .addAnnotatedClass(OrderDiscount.class).addAnnotatedClass(OrderItemComponent.class)
                .addAnnotatedClass(OrderReturn.class).addAnnotatedClass(OrderReturnItem.class)
                .buildMetadata().buildSessionFactory();
    }
    @AfterAll static void close() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @ParameterizedTest
    @CsvSource({"businessStatus,2", "invoicePayment,2", "createdAt;desc,2", "unknown,2"})
    void listCombinesSearchStatusPaymentTypeAndHalfOpenDatesBeforePaging(String sort, int sorts) {
        sort = sort.replace(';', ',');
        Order row = order(OrderStatus.COMPLETED, PaymentStatus.PAID, "123.45");
        when(orders.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(2, 15), 46));
        Page<OrderSummaryResponse> result = service.getOrders("  TrA  ", OrderStatus.COMPLETED,
                PaymentStatus.UNPAID, " online ", DAY, DAY, null, sort, 2, 15);
        ArgumentCaptor<Specification<Order>> filter = specificationCaptor();
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(filter.capture(), page.capture());
        assertEquals(PageRequest.of(2, 15), page.getValue());
        assertEquals(46, result.getTotalElements());
        assertEquals(row.getId(), result.getContent().getFirst().getId());
        assertEquals(new BigDecimal("123.45"), result.getContent().getFirst().getFinalAmount());
        CriteriaQuery<Order> query = materialize(filter.getValue(), Order.class);
        String hql = hql(query);
        assertTrue(hql.contains("isDeleted"), hql);
        assertTrue(hql.contains("lower("), hql);
        assertTrue(hql.contains("customerNameSnapshot"), hql);
        assertTrue(hql.contains("customerPhoneSnapshot"), hql);
        assertTrue(hql.contains("paymentStatus in"), hql);
        assertTrue(hql.contains("orderType ="), hql);
        assertTrue(hql.contains("createdAt >="), hql);
        assertTrue(hql.contains("createdAt <"), hql);
        List<?> values = parameters(query);
        assertTrue(values.contains(Instant.parse("2026-11-02T17:00:00Z")), values.toString());
        assertTrue(values.contains(Instant.parse("2026-11-03T17:00:00Z")), values.toString());
        assertTrue(values.contains("%tra%"), values.toString());
        assertTrue(values.contains("ONLINE"), values.toString());
        assertEquals(sorts, query.getOrderList().size());
        if (sort.equals("createdAt,desc")) {
            assertTrue(hql.contains(".id desc"), hql);
            assertFalse(hql.contains("case"), hql);
        } else {
            assertTrue(hql.contains(sort.equals("invoicePayment") ? "case " : "case "), hql);
            assertTrue(hql.contains(sort.equals("invoicePayment") ? "paymentStatus when" : "status when"), hql);
        }
        CriteriaQuery<Long> count = materialize(filter.getValue(), Long.class);
        assertTrue(count.getOrderList().isEmpty());
        verifyNoInteractions(payment, catalog, checkout, outbox, reservation);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" "})
    void blankCriteriaUsesDefaultBusinessSortAndClampsPageSizeWithoutFetchingPayments(String keyword) {
        when(orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(service.getOrders(keyword, null, null, keyword, null, null, null, keyword, -2, 999).isEmpty());
        ArgumentCaptor<Specification<Order>> filter = specificationCaptor();
        verify(orders).findAll(filter.capture(), eq(PageRequest.of(0, 200)));
        String hql = hql(materialize(filter.getValue(), Order.class));
        assertFalse(hql.contains("like"), hql);
        assertFalse(hql.contains("createdAt >="), hql);
        assertTrue(hql.contains("order by case"), hql);
        verifyNoInteractions(payment, catalog, histories);
    }

    @ParameterizedTest @ValueSource(strings = {"ALL", "ELIGIBLE", "INELIGIBLE"})
    void returnEligibilityUsesActiveNonDeletedReturnLinesAndDistinctPagingPriority(String eligibility) {
        when(orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getOrders(null, null, PaymentStatus.PAID, null, null, null,
                OrderReturnEligibility.valueOf(eligibility), "businessStatus", 0, 0);
        ArgumentCaptor<Specification<Order>> filter = specificationCaptor();
        verify(orders).findAll(filter.capture(), eq(PageRequest.of(0, 1)));
        CriteriaQuery<Order> q = materialize(filter.getValue(), Order.class);
        String hql = hql(q);
        assertTrue(hql.contains("exists"), hql);
        assertTrue(hql.contains("coalesce(sum("), hql);
        assertTrue(hql.contains("orderReturn"), hql);
        assertTrue(hql.contains("quantity >"), hql);
        assertEquals(eligibility.equals("ALL") ? 3 : 2, q.getOrderList().size());
        if (eligibility.equals("INELIGIBLE")) assertTrue(hql.contains("not ("), hql);
    }

    @Test void summaryCountsKeepFinancialGroupsSeparateAndUseDateRangeForEveryQuery() {
        when(orders.count(any(Specification.class))).thenAnswer(call -> {
            String hql = hql(materialize(call.getArgument(0), Long.class));
            assertTrue(hql.contains("createdAt >="), hql);
            assertTrue(hql.contains("createdAt <"), hql);
            assertFalse(hql.contains("order by"), hql);
            return 7L;
        });
        var result = service.getOrderStats(DAY, DAY);
        assertEquals(7, result.getTotal());
        assertEquals(7, result.getReturnedOrRefunded());
        assertEquals(7, result.getUnpaidInvoices());
        assertEquals(0, result.getShipping());
        verify(orders, times(7)).count(any(Specification.class));
        verifyNoInteractions(payment, catalog);
    }

    @Test void todaySalesRevenueExcludesCancelledAndPendingAmountUsesPositiveDebtOrFinalAmount() {
        Order paid = order(OrderStatus.COMPLETED, PaymentStatus.PAID, "100.01");
        Order confirmed = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID, "50");
        Order cancelled = order(OrderStatus.CANCELLED, PaymentStatus.PAID, "999");
        Order pending = order(OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT, "80");
        pending.setDebtAmount(new BigDecimal("20"));
        Order unpaid = order(OrderStatus.DRAFT, PaymentStatus.UNPAID, "30");
        unpaid.setDebtAmount(BigDecimal.ZERO);
        when(orders.findAll(any(Specification.class))).thenReturn(List.of(paid, confirmed, cancelled, pending, unpaid));
        var summary = service.getTodaySalesSummary();
        assertEquals(5, summary.getTotalOrders());
        assertEquals(2, summary.getCompletedOrders());
        assertEquals(2, summary.getPendingPaymentOrders());
        assertEquals(new BigDecimal("150.01"), summary.getTodayRevenue());
        assertEquals(new BigDecimal("75.01"), summary.getAverageOrderValue());
        assertEquals(new BigDecimal("50"), summary.getPendingAmount());
        verifyNoInteractions(payment, catalog, histories);
    }

    @Test void emptyTodaySummaryHasZeroAverageAndNoPaymentCalls() {
        when(orders.findAll(any(Specification.class))).thenReturn(List.of());
        var result = service.getTodaySalesSummary();
        assertEquals(BigDecimal.ZERO, result.getAverageOrderValue());
        assertEquals(BigDecimal.ZERO, result.getTodayRevenue());
        assertEquals(0, result.getTotalOrders());
        verifyNoInteractions(payment, catalog);
    }

    @Test void paidUnfulfilledCancellationDelegatesWithoutOverwritingLedgerOrPublishingLocally() {
        Order row = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID, "100");
        row.setOrderType("ONLINE");
        row.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        row.setPaidAmount(new BigDecimal("100"));
        when(orders.findByIdAndIsDeletedFalse(row.getId())).thenReturn(Optional.of(row));
        CancelOrderRequest cancel = new CancelOrderRequest();
        cancel.setReason("Customer cancelled before handover");
        service.cancelOrder(row.getId(), cancel);
        ArgumentCaptor<UpdateOrderStatusRequest> request = ArgumentCaptor.forClass(UpdateOrderStatusRequest.class);
        verify(statuses).updateOrderStatus(eq(row.getId()), request.capture());
        assertEquals(OrderStatus.CANCELLED, request.getValue().getStatus());
        assertEquals(cancel.getReason(), request.getValue().getNote());
        assertEquals(new BigDecimal("100"), row.getPaidAmount());
        verify(orders, never()).save(any());
        verifyNoInteractions(payment, histories, outbox);
    }

    @ParameterizedTest
    @CsvSource({"CASH,CASH,Tiền mặt", "bank,BANK_TRANSFER,Chuyển khoản", "QR_CODE,BANK_TRANSFER,Chuyển khoản",
            "CREDIT,DEBT,Công nợ", "CARD,CARD,Thẻ", "E_WALLET,E_WALLET,Ví điện tử", "COMBINED,COMBINED,Kết hợp", "CUSTOM,CUSTOM,CUSTOM"})
    void todaySalesMapsDecimalQuantitiesAndFiltersMethodBeforePaging(String method, String normalized, String label) {
        Order row = order(OrderStatus.COMPLETED, PaymentStatus.PAID, "123.45");
        row.setPaymentMethod(method);
        row.addItem(OrderItem.builder().quantity(new BigDecimal("1.25")).build());
        row.addItem(OrderItem.builder().quantity(null).build());
        when(orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(row)));
        try (MockedStatic<LocalDate> clock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            clock.when(() -> LocalDate.now(ZoneId.systemDefault())).thenReturn(DAY);
            var result = service.getTodaySales(OrderStatus.COMPLETED, PaymentStatus.PAID, " bank ", -1, 999);
            var mapped = result.getContent().getFirst();
            assertEquals(new BigDecimal("1.25"), mapped.getTotalItems());
            assertEquals("123.45", mapped.getFinalAmount().toPlainString());
            assertEquals(normalized, mapped.getPaymentMethod());
            assertEquals(label, mapped.getPaymentMethodLabel());
            assertEquals(LocalDateTime.of(2026, 11, 3, 7, 0), mapped.getSoldAt());
        }
        ArgumentCaptor<Specification<Order>> filter = specificationCaptor();
        verify(orders).findAll(filter.capture(), eq(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))));
        var q = materialize(filter.getValue(), Order.class);
        String hql = hql(q);
        assertTrue(hql.contains("upper("), hql);
        var values = parameters(q);
        assertTrue(values.contains("BANK"), values.toString());
        assertTrue(values.contains(Instant.parse("2026-11-02T17:00:00Z")), values.toString());
        assertTrue(values.contains(Instant.parse("2026-11-03T17:00:00Z")), values.toString());
        verifyNoInteractions(catalog, checkout, outbox, reservation);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,UNPAID,POS,NONE,Nháp,Chưa thanh toán,Bán tại quầy,Chưa hoàn hàng",
            "PENDING_PAYMENT,WAITING_PAYMENT,ONLINE,PARTIALLY_RETURNED,Chờ thanh toán,Chưa thanh toán,Đơn online,Hoàn một phần",
            "PAYMENT_CONFIRMED,PARTIALLY_PAID,RETURN,FULLY_RETURNED,Chờ duyệt,Đã đặt cọc,Hoàn hàng,Hoàn toàn bộ",
            "COMPLETED,PAID,REFUND,NONE,Đã hoàn thành,Đã thanh toán,Hoàn tiền,Chưa hoàn hàng",
            "PAYMENT_FAILED,FAILED,EXCHANGE,NONE,Thanh toán thất bại,Thanh toán thất bại,Đổi hàng,Chưa hoàn hàng",
            "EXPIRED,NEED_REVIEW,CUSTOM,NONE,Đã hết hạn,Chờ kiểm tra thanh toán,CUSTOM,Chưa hoàn hàng",
            "CANCELLED,REFUNDED,POS,NONE,Đã hủy,Đã hoàn tiền,Bán tại quầy,Chưa hoàn hàng",
            "COMPLETED,PARTIALLY_REFUNDED,POS,NONE,Đã hoàn thành,Hoàn tiền một phần,Bán tại quầy,Chưa hoàn hàng"})
    void canonicalFacadeDetailsPreserveAllBusinessStatusLabelsAndAuditSnapshots(OrderStatus status, PaymentStatus paid,
            String type, OrderReturnStatus returned, String statusLabel, String paidLabel, String typeLabel, String returnLabel) {
        Order row = order(status, paid, "123.45");
        row.setOrderType(type);
        row.setReturnStatus(returned);
        row.setCreatedBy(row.getId());
        row.setUpdatedAt(NOW.plusSeconds(60));
        when(orders.findByIdAndIsDeletedFalse(row.getId())).thenReturn(Optional.of(row));
        var response = service.getOrderById(row.getId());
        assertEquals(statusLabel, response.getStatusLabel());
        assertEquals(paidLabel, response.getPaymentStatusLabel());
        assertEquals(typeLabel, response.getOrderTypeLabel());
        assertEquals(returnLabel, response.getReturnStatusLabel());
        assertEquals(row.getId().toString(), response.getCreatedBy());
        assertEquals(LocalDateTime.of(2026, 11, 3, 7, 1), response.getUpdatedAt());
        assertEquals(row.getFinalAmount(), response.getFinalAmount());
        verifyNoInteractions(catalog, outbox, checkout, reservation);
    }

    @ParameterizedTest @ValueSource(strings = {"ledger", "paid-default", "debt-default", "unknown"})
    void missingOrderPaymentMethodUsesLedgerOrExplicitFinancialFallback(String fallback) {
        Order row = order(OrderStatus.CANCELLED, fallback.equals("paid-default") ? PaymentStatus.PAID : PaymentStatus.REFUNDED, "10");
        row.setPaymentMethod(" ");
        if (fallback.equals("debt-default")) row.setDebtAmount(BigDecimal.ONE);
        when(orders.findByIdAndIsDeletedFalse(row.getId())).thenReturn(Optional.of(row));
        if (fallback.equals("ledger")) when(payment.getPaymentByOrderId(row.getId())).thenReturn(
                new PaymentClient.PaymentOrderInfoResponse(null, "PAY-1", row.getId(), BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.TEN, new BigDecimal("0.25"), "PAID", "CARD", "Thẻ"));
        var response = service.getOrderById(row.getId());
        assertEquals(switch (fallback) { case "ledger" -> "CARD"; case "paid-default" -> "CASH";
            case "debt-default" -> "DEBT"; default -> null; }, response.getPaymentMethod());
        assertEquals(fallback.equals("ledger") ? new BigDecimal("0.25") : BigDecimal.ZERO, response.getChangeAmount());
    }

    @Test void unpaidCancellationWritesSingleHistoryAndPreservesFinancialAmounts() {
        Order row = order(OrderStatus.PENDING_PAYMENT, PaymentStatus.WAITING_PAYMENT, "100");
        row.setDebtAmount(new BigDecimal("100"));
        when(orders.findByIdAndIsDeletedFalse(row.getId())).thenReturn(Optional.of(row));
        when(orders.save(row)).thenReturn(row);
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Customer cancelled unpaid order");
        var response = service.cancelOrder(row.getId(), request);
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals(new BigDecimal("100"), response.getFinalAmount());
        assertEquals(new BigDecimal("100"), response.getDebtAmount());
        assertEquals(BigDecimal.ZERO, response.getPaidAmount());
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(histories).save(history.capture());
        assertEquals(OrderStatus.PENDING_PAYMENT, history.getValue().getFromStatus());
        assertEquals(OrderStatus.CANCELLED, history.getValue().getToStatus());
        assertEquals(request.getReason(), history.getValue().getReason());
        verifyNoInteractions(statuses, outbox, reservation, checkout);
    }

    @Test void unboundedStatusCountersDoNotInjectTodayOrOrderingIntoCountQueries() {
        when(orders.count(any(Specification.class))).thenAnswer(call -> {
            String hql = hql(materialize(call.getArgument(0), Long.class));
            assertFalse(hql.contains("createdAt >="), hql);
            assertFalse(hql.contains("createdAt <"), hql);
            assertFalse(hql.contains("order by"), hql);
            return 2L;
        });
        var summary = service.getOrderStats(null, null);
        assertEquals(2, summary.getTotal());
        assertEquals(2, summary.getCompleted());
        verify(orders, times(7)).count(any(Specification.class));
        verifyNoInteractions(payment, catalog);
    }

    @ParameterizedTest @ValueSource(strings = {"PAID", "COMPLETED", "CANCELLED"})
    void directCancellationCannotChangePaidCompletedOrAlreadyCancelledOrder(String forbidden) {
        Order row = order(forbidden.equals("COMPLETED") ? OrderStatus.COMPLETED : forbidden.equals("CANCELLED")
                ? OrderStatus.CANCELLED : OrderStatus.PENDING_PAYMENT,
                forbidden.equals("PAID") ? PaymentStatus.PAID : PaymentStatus.WAITING_PAYMENT, "100");
        when(orders.findByIdAndIsDeletedFalse(row.getId())).thenReturn(Optional.of(row));
        assertThrows(InvalidOrderStatusException.class,
                () -> service.cancelOrder(row.getId(), new CancelOrderRequest()));
        verify(orders, never()).save(any());
        verifyNoInteractions(statuses, payment, histories, outbox);
    }

    @ParameterizedTest @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void bundleAwaitingHandoverQueriesDistinctConfirmedBundleOrdersWithOptionalPackingAndSearch(boolean search, boolean packed) {
        OrderCustomBundleServiceImpl bundles = new OrderCustomBundleServiceImpl(orders, mock(OrderMapper.class),
                new OrderValidator(), new OrderAmountCalculator(), mock(OrderCodeGenerator.class), new OrderDateTimeHelper(),
                mock(OrderStatusHistoryWriter.class), mock(CustomerSnapshotResolver.class), mock(OrderResponseAssembler.class),
                mock(OrderCustomBundleSupport.class), mock(OrderBackorderService.class));
        when(orders.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertTrue(bundles.getAwaitingHandover(search ? " Tea " : " ", packed ? BundlePackingStatus.PACKED : null, -1, 999).isEmpty());
        ArgumentCaptor<Specification<Order>> filter = specificationCaptor();
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orders).findAll(filter.capture(), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(100, page.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), page.getValue().getSort());
        var q = materialize(filter.getValue(), Order.class);
        assertTrue(q.isDistinct());
        String hql = hql(q);
        assertTrue(hql.contains("join"), hql);
        assertTrue(hql.contains("itemType ="), hql);
        assertTrue(hql.contains("status ="), hql);
        assertEquals(search, hql.contains("packingCode"));
        assertEquals(packed, hql.contains("packingStatus"));
        var values = parameters(q);
        assertTrue(values.contains(OrderStatus.PAYMENT_CONFIRMED), values.toString());
        assertTrue(values.contains(OrderItemType.CUSTOM_BUNDLE), values.toString());
        if (search) assertTrue(values.contains("%tea%"), values.toString());
        if (packed) assertTrue(values.contains(BundlePackingStatus.PACKED), values.toString());
        verifyNoInteractions(payment, reservation);
    }

    @ParameterizedTest @CsvSource({"UNPAID,true", "REFUNDED,true", "PAID,false", "PARTIALLY_PAID,false"})
    void sharedCriteriaPreservesFinancialAliasesAndDateBoundaries(PaymentStatus paymentStatus, boolean alias) {
        var builder = new OrderQueryCriteriaBuilder(new OrderDateTimeHelper());
        var filter = builder.build(" Tea ", OrderStatus.COMPLETED, paymentStatus, " online ", DAY, DAY);
        var q = materialize(filter, Order.class);
        String hql = hql(q);
        assertEquals(alias, hql.contains("paymentStatus in"), hql);
        var values = parameters(q);
        assertTrue(values.contains("%tea%"), values.toString());
        assertTrue(values.contains("ONLINE"), values.toString());
        assertTrue(values.contains(Instant.parse("2026-11-02T17:00:00Z")), values.toString());
        assertTrue(values.contains(Instant.parse("2026-11-03T17:00:00Z")), values.toString());
    }

    @ParameterizedTest @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void countCriteriaUseSameOptionalDateBoundsForStatusAndPaymentGroups(boolean from, boolean to) {
        var builder = new OrderQueryCriteriaBuilder(new OrderDateTimeHelper());
        for (Specification<Order> filter : List.of(
                builder.byStatuses(from ? DAY : null, to ? DAY : null, List.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT)),
                builder.byPaymentStatuses(from ? DAY : null, to ? DAY : null, List.of(PaymentStatus.PAID)))) {
            var q = materialize(filter, Long.class);
            String hql = hql(q);
            assertEquals(from, hql.contains("createdAt >="), hql);
            assertEquals(to, hql.contains("createdAt <"), hql);
            assertTrue(hql.contains("isDeleted"), hql);
            assertTrue(q.getOrderList().isEmpty());
            var values = parameters(q);
            if (from) assertTrue(values.contains(Instant.parse("2026-11-02T17:00:00Z")), values.toString());
            if (to) assertTrue(values.contains(Instant.parse("2026-11-03T17:00:00Z")), values.toString());
        }
    }

    @Test void sharedInvoicePrioritySortIsOmittedFromCountQueryButKeepsStablePageOrder() {
        var builder = new OrderQueryCriteriaBuilder(new OrderDateTimeHelper());
        var filter = builder.withBusinessOrder(builder.build(null, null, null, null, null, null), "invoicePayment");
        var q = materialize(filter, Order.class);
        assertEquals(3, q.getOrderList().size());
        String hql = hql(q);
        assertTrue(hql.contains("paymentStatus"), hql);
        assertTrue(hql.contains(".id desc"), hql);
        assertTrue(materialize(filter, Long.class).getOrderList().isEmpty());
    }

    private List<?> parameters(CriteriaQuery<?> criteria) {
        try (var em = factory.createEntityManager()) {
            var query = em.createQuery(criteria);
            return query.getParameters().stream().map(query::getParameterValue).toList();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> CriteriaQuery<T> materialize(Specification<Order> spec, Class<T> result) {
        CriteriaBuilder cb = factory.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(result);
        Root<Order> root = query.from(Order.class);
        query.where(spec.toPredicate(root, query, cb));
        if (result == Long.class) query.select((Selection<? extends T>) cb.count(root));
        else query.select((Selection<? extends T>) root);
        return query;
    }
    static String hql(CriteriaQuery<?> query) { return ((SqmSelectStatement<?>) query).toHqlString(); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Specification<Order>> specificationCaptor() { return ArgumentCaptor.forClass((Class) Specification.class); }
    private Order order(OrderStatus status, PaymentStatus payment, String amount) {
        Order row = Order.builder().status(status).paymentStatus(payment).finalAmount(new BigDecimal(amount))
                .paidAmount(BigDecimal.ZERO).debtAmount(BigDecimal.ZERO).orderCode("ORDER-UNIT").orderType("POS").build();
        row.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        row.setCreatedAt(NOW);
        return row;
    }
}
