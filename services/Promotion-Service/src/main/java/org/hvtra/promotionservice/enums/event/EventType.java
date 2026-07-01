package org.hvtra.promotionservice.enums.event;

public enum EventType {
    TIME_BASED,             // sự kiện theo thời gian: Tết, 30/4, 1/5
    ORDER_AMOUNT_BASED,     // sự kiện theo tổng tiền đơn
    ORDER_COUNT_BASED,      // sự kiện theo số đơn đã mua
    ITEM_QUANTITY_BASED,    // sự kiện theo số lượng sản phẩm mua
    LOYALTY_POINT_BASED,    // sự kiện theo điểm tích lũy
    MANUAL_CAMPAIGN         // admin chủ động tạo vì muốn đẩy thị trường/hàng ế
}