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
import org.hvtra.orderservice.dto.response.SalesDashboardResponse;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.BranchSalesMetrics;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.OrderTimelineEntry;
import org.hvtra.orderservice.repository.OrderStatisticReportQueryRepository.PaymentMethodMetrics;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    @Override
    public SalesDashboardResponse getSalesDashboard(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        LocalDate effectiveTo = toDate == null ? orderDateTimeHelper.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo : fromDate;
        if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) >= DASHBOARD_MAX_DAYS) {
            throw new BusinessException(
                    "DATE_RANGE_TOO_WIDE",
                    "Dashboard chỉ hỗ trợ tối đa " + DASHBOARD_MAX_DAYS + " ngày"
            );
        }
        Instant fromCreatedAt = startOfDay(effectiveFrom);
        Instant toCreatedBefore = startOfDay(effectiveTo.plusDays(1));

        List<OrderTimelineEntry> timeline =
                reportQueryRepository.findPaidOrderTimeline(fromCreatedAt, toCreatedBefore);
        List<PaymentMethodMetrics> payments =
                reportQueryRepository.findPaymentBreakdown(fromCreatedAt, toCreatedBefore);
        List<BranchSalesMetrics> branches =
                reportQueryRepository.findStorePerformance(fromCreatedAt, toCreatedBefore);

        long totalOrders = timeline.size();
        BigDecimal totalRevenue = timeline.stream()
                .map(entry -> defaultZero(entry.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesDashboardResponse(
                effectiveFrom,
                effectiveTo,
                totalOrders,
                totalRevenue,
                hourlyTrend(timeline),
                payments.stream()
                        .map(row -> new SalesDashboardResponse.PaymentBreakdownRow(
                                trim(row.paymentMethod()),
                                row.orderCount(),
                                defaultZero(row.revenue())
                        ))
                        .sorted(Comparator.comparing(SalesDashboardResponse.PaymentBreakdownRow::revenue).reversed())
                        .toList(),
                branches.stream()
                        .map(row -> new SalesDashboardResponse.StorePerformanceRow(
                                row.branchId(),
                                trim(row.branchName()),
                                row.orderCount(),
                                defaultZero(row.revenue()),
                                average(row.revenue(), row.orderCount())
                        ))
                        .sorted(Comparator.comparing(SalesDashboardResponse.StorePerformanceRow::revenue).reversed())
                        .toList(),
                getTopSellingProducts(effectiveFrom, effectiveTo, SORT_REVENUE, DASHBOARD_TOP_LIMIT).getRows(),
                getTopSalesStaff(effectiveFrom, effectiveTo, SORT_REVENUE, DASHBOARD_TOP_LIMIT).getRows()
        );
    }

    /**
     * Gộp đơn theo giờ trong ngày (múi giờ hệ thống). Trả về dải giờ liên tục từ giờ bán sớm nhất
     * tới muộn nhất, các giờ không có đơn điền 0 để biểu đồ không bị đứt; không có đơn thì rỗng.
     */
    private List<SalesDashboardResponse.HourlyPoint> hourlyTrend(List<OrderTimelineEntry> timeline) {
        Map<Integer, long[]> counts = new LinkedHashMap<>();
        Map<Integer, BigDecimal> revenues = new LinkedHashMap<>();
        for (OrderTimelineEntry entry : timeline) {
            if (entry.soldAt() == null) {
                continue;
            }
            LocalDateTime local = orderDateTimeHelper.toLocalDateTime(entry.soldAt());
            if (local == null) {
                continue;
            }
            int hour = local.getHour();
            counts.computeIfAbsent(hour, key -> new long[1])[0]++;
            revenues.merge(hour, defaultZero(entry.amount()), BigDecimal::add);
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        int firstHour = counts.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        int lastHour = counts.keySet().stream().mapToInt(Integer::intValue).max().orElse(23);
        List<SalesDashboardResponse.HourlyPoint> points = new ArrayList<>();
        for (int hour = firstHour; hour <= lastHour; hour++) {
            long[] count = counts.get(hour);
            points.add(new SalesDashboardResponse.HourlyPoint(
                    hour,
                    String.format("%02d:00", hour),
                    count == null ? 0 : count[0],
                    revenues.getOrDefault(hour, BigDecimal.ZERO)
            ));
        }
        return points;
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
