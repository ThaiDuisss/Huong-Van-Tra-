package org.hvtra.iventoryservice.bundle.mapper;

import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePacking;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePackingLine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomBundlePackingMapper {

    /** Chi tiết: packing phải được load kèm lines + inventoryItem (findByIdWithLines). */
    public CustomBundlePackingResponse toResponse(CustomBundlePacking packing) {
        return build(packing, packing.getLines().stream().map(this::toLine).toList());
    }

    /** Danh sách phân trang: không đọc lines (lazy) để tránh N+1. */
    public CustomBundlePackingResponse toSummary(CustomBundlePacking packing) {
        return build(packing, List.of());
    }

    private CustomBundlePackingResponse build(CustomBundlePacking packing, List<CustomBundlePackingResponse.Line> lines) {
        return new CustomBundlePackingResponse(
                packing.getId(),
                packing.getPackingCode(),
                packing.getOrderId(),
                packing.getOrderCode(),
                packing.getOrderItemId(),
                packing.getBundleName(),
                packing.getBranchId(),
                packing.getLocationId(),
                packing.getStatus(),
                packing.getExpectedTotalWeight(),
                packing.getActualTotalWeight(),
                packing.getPackagingType(),
                packing.getNumberOfPackages(),
                packing.getPackingNote(),
                packing.getExpectedAvailableAt(),
                packing.getReservedAt(),
                packing.getPackedBy(),
                packing.getPackedAt(),
                packing.getIssueTransactionCode(),
                packing.getCancelledAt(),
                packing.getCancelReason(),
                packing.getCreatedAt(),
                lines);
    }

    private CustomBundlePackingResponse.Line toLine(CustomBundlePackingLine line) {
        return new CustomBundlePackingResponse.Line(
                line.getId(),
                line.getIngredientId(),
                line.getInventoryItem().getReferenceCode(),
                line.getIngredientName(),
                line.getRequiredQuantity(),
                line.isPicked());
    }
}
