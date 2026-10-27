package org.hvtra.orderservice.domain.enums;

/** Trạng thái đóng gói của dòng bundle, đồng bộ từ yêu cầu đóng gói bên Inventory. */
public enum BundlePackingStatus {
    WAITING_MATERIAL,
    AWAITING_PACKING,
    PACKED,
    CANCELLED
}
