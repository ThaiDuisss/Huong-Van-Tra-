package org.hvtra.orderservice.service.impl;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSalesStaffResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductResponse;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.ProductSalesMetrics;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.ProductSalesTotals;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.StaffOrderMetrics;
import org.hvtra.orderservice.service.OrderStatisticReportService;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticReportServiceImplTest {

    private static final LocalDate FROM_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 30);
    private static final Instant FROM_INSTANT = Instant.parse("2026-08-31T17:00:00Z");
    private static final Instant TO_INSTANT = Instant.parse("2026-09-30T17:00:00Z");

    @Mock
    private OrderStatisticReportQueryRepository reportQueryRepository;

    @Mock
    private OrderDateTimeHelper orderDateTimeHelper;

    private OrderStatisticReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticReportServiceImpl(reportQueryRepository, orderDateTimeHelper);
    }

    @Test
    void topSalesStaffRanksByRevenueByDefaultAndTotalsCoverAllStaff() {
        UUID lan = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID minh = UUID.fromString("00000000-0000-0000-0000-000000000002");
        stubDateRange();
        when(reportQueryRepository.findStaffOrderMetrics(FROM_INSTANT, TO_INSTANT)).thenReturn(List.of(
                new StaffOrderMetrics(lan, " Lan ", 5, new BigDecimal("500000"), 1),
                new StaffOrderMetrics(minh, "Minh", 2, new BigDecimal("900000"), 0),
                new StaffOrderMetrics(null, null, 3, new BigDecimal("100000"), 0)
        ));
        Map<UUID, BigDecimal> itemsSold = new HashMap<>();
        itemsSold.put(lan, new BigDecimal("12"));
        itemsSold.put(minh, new BigDecimal("7.5"));
        itemsSold.put(null, new BigDecimal("4"));
        when(reportQueryRepository.findStaffItemsSold(FROM_INSTANT, TO_INSTANT)).thenReturn(itemsSold);

        TopSalesStaffReportResponse report = service.getTopSalesStaff(FROM_DATE, TO_DATE, null, 2);

        assertEquals(OrderStatisticReportService.SORT_REVENUE, report.getSortBy());
        assertEquals(2, report.getLimit());
        assertEquals(2, report.getRows().size());
        TopSalesStaffResponse first = report.getRows().get(0);
        assertEquals(1, first.getRank());
        assertEquals(minh, first.getCashierId());
        assertEquals(0, new BigDecimal("450000.00").compareTo(first.getAverageOrderValue()));
        assertEquals(0, new BigDecimal("7.5").compareTo(first.getItemsSold()));
        TopSalesStaffResponse second = report.getRows().get(1);
        assertEquals(2, second.getRank());
        assertEquals("Lan", second.getCashierName());
        assertEquals(1, second.getRefundedOrderCount());

        assertEquals(3, report.getTotalStaff());
        assertEquals(10, report.getTotalOrders());
        assertEquals(0, new BigDecimal("1500000").compareTo(report.getTotalRevenue()));
        assertEquals(0, new BigDecimal("23.5").compareTo(report.getTotalItemsSold()));
        assertEquals(0, new BigDecimal("150000.00").compareTo(report.getAverageOrderValue()));
        assertEquals(1, report.getTotalRefundedOrders());
    }

    @Test
    void topSalesStaffCanRankByOrderCountAndKeepsOrdersWithoutCashier() {
        UUID lan = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID minh = UUID.fromString("00000000-0000-0000-0000-000000000002");
        stubDateRange();
        when(reportQueryRepository.findStaffOrderMetrics(FROM_INSTANT, TO_INSTANT)).thenReturn(List.of(
                new StaffOrderMetrics(minh, "Minh", 2, new BigDecimal("900000"), 0),
                new StaffOrderMetrics(null, null, 3, new BigDecimal("100000"), 0),
                new StaffOrderMetrics(lan, "Lan", 5, new BigDecimal("500000"), 0)
        ));
        Map<UUID, BigDecimal> itemsSold = new HashMap<>();
        itemsSold.put(null, new BigDecimal("4"));
        when(reportQueryRepository.findStaffItemsSold(FROM_INSTANT, TO_INSTANT)).thenReturn(itemsSold);

        TopSalesStaffReportResponse report = service.getTopSalesStaff(FROM_DATE, TO_DATE, "ORDERS", null);

        assertEquals(OrderStatisticReportService.SORT_ORDERS, report.getSortBy());
        assertEquals(OrderStatisticReportService.DEFAULT_LIMIT, report.getLimit());
        assertEquals(
                List.of(5L, 3L, 2L),
                report.getRows().stream().map(TopSalesStaffResponse::getOrderCount).toList()
        );
        TopSalesStaffResponse unknownCashier = report.getRows().get(1);
        assertNull(unknownCashier.getCashierId());
        assertNull(unknownCashier.getCashierName());
        assertEquals(0, new BigDecimal("4").compareTo(unknownCashier.getItemsSold()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getRows().get(0).getItemsSold()));
    }

    @Test
    void topSalesStaffWithoutSalesReturnsZeroTotalsAndSkipsItemQuery() {
        when(reportQueryRepository.findStaffOrderMetrics(null, null)).thenReturn(List.of());

        TopSalesStaffReportResponse report = service.getTopSalesStaff(null, null, "revenue", 10);

        assertTrue(report.getRows().isEmpty());
        assertEquals(0, report.getTotalOrders());
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getTotalRevenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getAverageOrderValue()));
        verify(reportQueryRepository, never()).findStaffItemsSold(any(), any());
        verifyNoInteractions(orderDateTimeHelper);
    }

    @Test
    void topSellingProductsDefaultsToQuantitySortAndAttachesReturnedQuantity() {
        UUID sen = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID nhai = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        stubDateRange();
        when(reportQueryRepository.findTopSellingProducts(FROM_INSTANT, TO_INSTANT, false, 5)).thenReturn(List.of(
                new ProductSalesMetrics(sen, "SEN-100", "Trà sen", "Hộp 100g", new BigDecimal("30"), new BigDecimal("3000000"), 12),
                new ProductSalesMetrics(nhai, "NHAI-50", "Trà nhài", null, new BigDecimal("10"), new BigDecimal("4000000"), 4)
        ));
        when(reportQueryRepository.findProductSalesTotals(FROM_INSTANT, TO_INSTANT))
                .thenReturn(new ProductSalesTotals(8, new BigDecimal("75"), new BigDecimal("9000000"), 20));
        when(reportQueryRepository.findReturnedQuantities(FROM_INSTANT, TO_INSTANT, List.of(sen, nhai)))
                .thenReturn(Map.of(sen, new BigDecimal("2")));

        TopSellingProductReportResponse report = service.getTopSellingProducts(FROM_DATE, TO_DATE, "unknown", 5);

        assertEquals(OrderStatisticReportService.SORT_QUANTITY, report.getSortBy());
        assertEquals(8, report.getTotalProducts());
        assertEquals(20, report.getTotalOrders());
        assertEquals(0, new BigDecimal("75").compareTo(report.getTotalQuantitySold()));
        assertEquals(0, new BigDecimal("9000000").compareTo(report.getTotalRevenue()));
        TopSellingProductResponse first = report.getRows().get(0);
        assertEquals(1, first.getRank());
        assertEquals("SEN-100", first.getSku());
        assertEquals(12, first.getOrderCount());
        assertEquals(0, new BigDecimal("2").compareTo(first.getReturnedQuantity()));
        TopSellingProductResponse second = report.getRows().get(1);
        assertEquals(2, second.getRank());
        assertNull(second.getVariantName());
        assertEquals(0, BigDecimal.ZERO.compareTo(second.getReturnedQuantity()));
    }

    @Test
    void topSellingProductsPassesRevenueSortAndClampsLimit() {
        when(reportQueryRepository.findTopSellingProducts(null, null, true, OrderStatisticReportService.MAX_LIMIT))
                .thenReturn(List.of());

        TopSellingProductReportResponse report = service.getTopSellingProducts(null, null, "Revenue", 100_000);

        assertEquals(OrderStatisticReportService.SORT_REVENUE, report.getSortBy());
        assertEquals(OrderStatisticReportService.MAX_LIMIT, report.getLimit());
        assertTrue(report.getRows().isEmpty());
        assertEquals(0, report.getTotalProducts());
        verify(reportQueryRepository, never()).findProductSalesTotals(any(), any());
        verify(reportQueryRepository, never()).findReturnedQuantities(any(), any(), any());
    }

    @Test
    void nonPositiveLimitFallsBackToOneRow() {
        when(reportQueryRepository.findTopSellingProducts(null, null, false, 1)).thenReturn(List.of());

        assertEquals(1, service.getTopSellingProducts(null, null, null, 0).getLimit());
    }

    @Test
    void rejectsInvertedDateRangeBeforeQuerying() {
        BusinessException staffError = assertThrows(
                BusinessException.class,
                () -> service.getTopSalesStaff(TO_DATE, FROM_DATE, null, null)
        );
        BusinessException productError = assertThrows(
                BusinessException.class,
                () -> service.getTopSellingProducts(TO_DATE, FROM_DATE, null, null)
        );

        assertEquals("INVALID_DATE_RANGE", staffError.getCode());
        assertEquals("INVALID_DATE_RANGE", productError.getCode());
        verify(reportQueryRepository, never()).findStaffOrderMetrics(any(), any());
        verify(reportQueryRepository, never()).findTopSellingProducts(any(), any(), anyBoolean(), anyInt());
    }

    /** Khoảng lọc là [đầu ngày fromDate, đầu ngày sau toDate) nên toDate được tính trọn ngày. */
    private void stubDateRange() {
        when(orderDateTimeHelper.startOfDay(FROM_DATE)).thenReturn(FROM_INSTANT);
        when(orderDateTimeHelper.startOfDay(TO_DATE.plusDays(1))).thenReturn(TO_INSTANT);
    }
}
