package org.hvtra.promotionservice.enums.condition;

public enum ConditionType {

    // Điều kiện theo đơn hàng
    MIN_ORDER_AMOUNT,       // đơn tối thiểu
    MAX_ORDER_AMOUNT,       // đơn tối đa
    MIN_ITEM_QUANTITY,      // tổng số lượng sản phẩm trong đơn >= X

    // Điều kiện theo lịch sử khách hàng
    MIN_ORDER_COUNT,        // khách đã mua đủ X đơn
    MIN_TOTAL_SPENT,        // tổng chi tiêu tích lũy đạt X tiền
    MIN_LOYALTY_POINT,      // điểm tích lũy đạt X điểm
    FIRST_PURCHASE,         // khách mua lần đầu

    // Điều kiện theo khách hàng
    CUSTOMER_TIER,          // VIP, GOLD, SILVER
    CUSTOMER_ID,            // áp dụng cho khách cụ thể

    // Điều kiện theo phạm vi sản phẩm
    // Best practice: nên dùng PromotionTarget.
    // Hai type này giữ lại để xử lý rule đặc biệt nếu cần.
    PRODUCT_ID,
    CATEGORY_ID,

    // Điều kiện theo ngữ cảnh bán hàng
    CHANNEL,                // POS, ONLINE
    BRANCH,                 // chi nhánh
    PAYMENT_METHOD          // CASH, BANK_TRANSFER
}