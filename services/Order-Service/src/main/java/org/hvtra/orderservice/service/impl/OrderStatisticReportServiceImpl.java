package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Báo cáo thống kê: nhân viên bán hàng tốt nhất và sản phẩm bán chạy.
 *
 * <p>Quy ước số liệu: chỉ tính đơn đã thanh toán (xem {@link OrderStatisticReportQueryRepository}),
 * lọc theo ngày tạo đơn. Số đơn / doanh thu / số lượng là số gộp, không trừ hàng trả; hoàn trả được
 * báo ở cột riêng ({@code refundedOrderCount}, {@code returnedQuantity}) vì số tiền hoàn thực tế nằm
 * ở Payment-Service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderStatisticReportServiceImpl implements OrderStatisticReportService {

    private static final String INVALID_DATE_RANGE_MESSAGE = "Từ ngày không được lớn hơn đến ngày";
    private static final Comparator<UUID> NULLS_LAST_ID =
            Comparator.nullsLast(Comparator.comparing(UUID::toString));

    private final OrderStatisticReportQueryRepository reportQueryRepository;
    private final OrderDateTimeHelper orderDateTimeHelper;

    @Override
    public TopSalesStaffReportResponse getTopSalesStaff(
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy,
            Integer limit
    ) {
        validateDateRange(fromDate, toDate);
        String effectiveSort = SORT_ORDERS.equalsIgnoreCase(trim(sortBy)) ? SORT_ORDERS : SORT_REVENUE;
        int effectiveLimit = normalizeLimit(limit);
        Instant fromCreatedAt = startOfDay(fromDate);
        Instant toCreatedBefore = startOfDay(toDate == null ? null : toDate.plusDays(1));

        List<StaffOrderMetrics> metrics =
                reportQueryRepository.findStaffOrderMetrics(fromCreatedAt, toCreatedBefore);
        Map<UUID, BigDecimal> itemsSoldByCashier = metrics.isEmpty()
                ? Map.of()
                : reportQueryRepository.findStaffItemsSold(fromCreatedAt, toCreatedBefore);

        List<StaffOrderMetrics> sorted = metrics.stream()
                .sorted(staffComparator(effectiveSort))
                .toList();

        List<TopSalesStaffResponse> rows = new ArrayList<>();
        for (int index = 0; index < Math.min(effectiveLimit, sorted.size()); index++) {
            StaffOrderMetrics staff = sorted.get(index);
            rows.add(TopSalesStaffResponse.builder()
                    .rank(index + 1)
                    .cashierId(staff.cashierId())
                    .cashierName(trim(staff.cashierName()))
                    .orderCount(staff.orderCount())
                    .revenue(defaultZero(staff.revenue()))
                    .averageOrderValue(average(staff.revenue(), staff.orderCount()))
                    .itemsSold(itemsSold(itemsSoldByCashier, staff.cashierId()))
                    .refundedOrderCount(staff.refundedOrderCount())
                    .build());
        }

        long totalOrders = metrics.stream().mapToLong(StaffOrderMetrics::orderCount).sum();
        BigDecimal totalRevenue = metrics.stream()
                .map(staff -> defaultZero(staff.revenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalItemsSold = itemsSoldByCashier.values().stream()
                .map(this::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TopSalesStaffReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .sortBy(effectiveSort)
                .limit(effectiveLimit)
                .totalStaff(metrics.size())
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .totalItemsSold(totalItemsSold)
                .averageOrderValue(average(totalRevenue, totalOrders))
                .totalRefundedOrders(metrics.stream().mapToLong(StaffOrderMetrics::refundedOrderCount).sum())
                .rows(rows)
                .build();
    }

    @Override
    public TopSellingProductReportResponse getTopSellingProducts(
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy,
            Integer limit
    ) {
        validateDateRange(fromDate, toDate);
        boolean sortByRevenue = SORT_REVENUE.equalsIgnoreCase(trim(sortBy));
        int effectiveLimit = normalizeLimit(limit);
        Instant fromCreatedAt = startOfDay(fromDate);
        Instant toCreatedBefore = startOfDay(toDate == null ? null : toDate.plusDays(1));

        List<ProductSalesMetrics> products = reportQueryRepository.findTopSellingProducts(
                fromCreatedAt,
                toCreatedBefore,
                sortByRevenue,
                effectiveLimit
        );
        ProductSalesTotals totals = products.isEmpty()
                ? new ProductSalesTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, 0)
                : reportQueryRepository.findProductSalesTotals(fromCreatedAt, toCreatedBefore);
        Map<UUID, BigDecimal> returnedByVariant = products.isEmpty()
                ? Map.of()
                : reportQueryRepository.findReturnedQuantities(
                        fromCreatedAt,
                        toCreatedBefore,
                        products.stream().map(ProductSalesMetrics::productVariantId).toList()
                );

        List<TopSellingProductResponse> rows = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            ProductSalesMetrics product = products.get(index);
            rows.add(TopSellingProductResponse.builder()
                    .rank(index + 1)
                    .productVariantId(product.productVariantId())
                    .sku(trim(product.sku()))
                    .productName(trim(product.productName()))
                    .variantName(trim(product.variantName()))
                    .quantitySold(defaultZero(product.quantitySold()))
                    .revenue(defaultZero(product.revenue()))
                    .orderCount(product.orderCount())
                    .returnedQuantity(defaultZero(returnedByVariant.get(product.productVariantId())))
                    .build());
        }

        return TopSellingProductReportResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .sortBy(sortByRevenue ? SORT_REVENUE : SORT_QUANTITY)
                .limit(effectiveLimit)
                .totalProducts(totals.productCount())
                .totalQuantitySold(defaultZero(totals.quantitySold()))
                .totalRevenue(defaultZero(totals.revenue()))
                .totalOrders(totals.orderCount())
                .rows(rows)
                .build();
    }

    private BigDecimal itemsSold(Map<UUID, BigDecimal> itemsSoldByCashier, UUID cashierId) {
        // Không dùng Map.get(null) trực tiếp: Map bất biến ném NPE với key null (nhóm đơn không có thu ngân).
        return itemsSoldByCashier.entrySet().stream()
                .filter(entry -> java.util.Objects.equals(entry.getKey(), cashierId))
                .map(entry -> defaultZero(entry.getValue()))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private Comparator<StaffOrderMetrics> staffComparator(String sortBy) {
        Comparator<StaffOrderMetrics> byRevenue =
                Comparator.comparing((StaffOrderMetrics staff) -> defaultZero(staff.revenue())).reversed();
        Comparator<StaffOrderMetrics> byOrders =
                Comparator.comparingLong(StaffOrderMetrics::orderCount).reversed();
        Comparator<StaffOrderMetrics> primary = SORT_ORDERS.equals(sortBy)
                ? byOrders.thenComparing(byRevenue)
                : byRevenue.thenComparing(byOrders);
        return primary.thenComparing(StaffOrderMetrics::cashierId, NULLS_LAST_ID);
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", INVALID_DATE_RANGE_MESSAGE);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : orderDateTimeHelper.startOfDay(date);
    }

    private BigDecimal average(BigDecimal total, long count) {
        if (count <= 0) {
            return BigDecimal.ZERO;
        }
        return defaultZero(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
