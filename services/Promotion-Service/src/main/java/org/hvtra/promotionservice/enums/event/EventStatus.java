package org.hvtra.promotionservice.enums.event;

public enum EventStatus {

    DRAFT,       // mới tạo, đang soạn, chưa public

    SCHEDULED,   // đã lên lịch, chưa tới thời gian chạy

    ACTIVE,      // đang chạy, user có thể thấy / áp dụng

    PAUSED,      // tạm dừng thủ công, có thể resume lại

    ENDED,       // đã kết thúc tự nhiên theo end_at

    CANCELLED    // đã hủy vĩnh viễn, không chạy lại
}