package org.hvtra.orderservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Số liệu tổng hợp cho dashboard bán hàng (một lần gọi, nhiều widget).
 *
 * <p>Cùng quy ước với các báo cáo thống kê: chỉ tính đơn đã thanh toán, lọc theo ngày tạo đơn.
 * Mốc giờ của {@code hourlyTrend} lấy theo giờ bán ({@code soldAt}, với đơn offline) hoặc giờ tạo đơn,
 * gộp theo giờ trong ngày trên toàn bộ khoảng thời gian.
 */
public record SalesDashboardResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalOrders,
        BigDecimal totalRevenue,
        List<HourlyPoint> hourlyTrend,
        List<PaymentBreakdownRow> paymentBreakdown,
        List<StorePerformanceRow> storePerformance,
        List<TopSellingProductResponse> topProducts,
        List<TopSalesStaffResponse> topStaff
) {

    /** Một cột trên biểu đồ theo giờ; {@code label} dạng {@code HH:00}. */
    public record HourlyPoint(int hour, String label, long orderCount, BigDecimal revenue) {
    }

    /** {@code method} là mã phương thức thanh toán lưu trên đơn ({@code CASH}, {@code BANK_TRANSFER}, ...). */
    public record PaymentBreakdownRow(String method, long orderCount, BigDecimal revenue) {
    }

    public record StorePerformanceRow(
            UUID branchId,
            String branchName,
            long orderCount,
            BigDecimal revenue,
            BigDecimal averageOrderValue
    ) {
    }
}
