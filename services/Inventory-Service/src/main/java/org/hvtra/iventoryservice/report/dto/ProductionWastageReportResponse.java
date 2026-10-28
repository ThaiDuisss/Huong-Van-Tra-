package org.hvtra.iventoryservice.report.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Báo cáo sản xuất trong kỳ, tính từ lệnh sản xuất: hao hụt nguyên liệu và sản lượng lấy theo lệnh đã duyệt
 * (approvedAt trong kỳ); thiếu nguyên liệu lấy theo lệnh đang mở (DRAFT/PENDING_APPROVAL) so với tồn khả dụng.
 */
@Builder
public record ProductionWastageReportResponse(
        /** Nguyên liệu theo định mức BOM, chưa cộng tỷ lệ hao hụt cho phép. */
        BigDecimal totalPlannedQuantity,
        BigDecimal totalActualQuantity,
        BigDecimal totalWastageQuantity,
        BigDecimal totalWastageValue,
        long completedOrderCount,
        /** Tổng số thành phẩm đã nhập kho từ các lệnh đã duyệt trong kỳ. */
        BigDecimal totalOutputQuantity,
        long openOrderCount,
        long shortageOrderCount,
        long shortageMaterialCount,
        List<MaterialWastageDetail> materials
) {
    public record MaterialWastageDetail(
            String materialCode,
            String materialName,
            String unitSymbol,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity,
            BigDecimal wastageQuantity,
            double wastageRate,
            /** Tỷ lệ hao hụt cho phép theo BOM (bình quân theo định mức của các lệnh). */
            double thresholdRate,
            int productionOrderCount,
            boolean overThreshold,
            BigDecimal estimatedLossValue
    ) {}
}
