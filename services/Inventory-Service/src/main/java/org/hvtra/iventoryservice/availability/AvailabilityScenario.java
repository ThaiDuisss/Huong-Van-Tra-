package org.hvtra.iventoryservice.availability;

/** Tình huống của một mặt hàng khi khách đặt nhiều hơn số đang có (kệ + kho). */
public enum AvailabilityScenario {
    /** Kệ và kho đủ hàng, không phải chờ. */
    AVAILABLE,
    /** Đang có lệnh sản xuất đủ bù phần thiếu. */
    IN_PRODUCTION,
    /** Hết thành phẩm nhưng đủ nguyên liệu: sản xuất theo đơn. */
    PRODUCE_FROM_STOCK_MATERIALS,
    /** Thiếu nguyên liệu nhưng đã có phiếu nhập đang về. */
    WAIT_MATERIAL_RECEIPT,
    /** Thiếu nguyên liệu và chưa có phiếu nhập: phải đặt mua. */
    WAIT_MATERIAL_PURCHASE,
    /** Mặt hàng chưa có định mức nguyên liệu nên không sản xuất theo đơn được. */
    NOT_PRODUCIBLE
}
