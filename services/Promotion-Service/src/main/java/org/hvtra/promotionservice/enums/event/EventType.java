package org.hvtra.promotionservice.enums.event;

public enum EventType {
    SEASONAL_CAMPAIGN,      // Tết, 30/4, 1/5, Trung thu
    FLASH_SALE,             // campaign ngắn hạn, đẩy doanh số nhanh
    CLEARANCE_CAMPAIGN,     // xả hàng tồn, hàng chậm bán
    LOYALTY_CAMPAIGN,       // chăm sóc khách hàng thân thiết
    ORDER_INCENTIVE,        // kích thích tăng giá trị / số lượng đơn hàng
    MANUAL_CAMPAIGN         // admin tự tạo chiến dịch đặc biệt
}