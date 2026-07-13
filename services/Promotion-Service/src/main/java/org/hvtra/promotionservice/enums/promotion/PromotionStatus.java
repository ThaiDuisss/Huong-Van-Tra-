package org.hvtra.promotionservice.enums.promotion;

public enum PromotionStatus {

    DRAFT,       // moi tao, dang cau hinh, chua san sang ap dung

    SCHEDULED,   // da len lich, cho toi startAt de kich hoat

    ACTIVE,      // dang duoc phep ap dung cho don hang/user

    PAUSED,      // tam dung thu cong, co the resume lai

    EXPIRED,     // da het han theo endAt, co the gia han neu business cho phep

    ENDED,       // da dong/ket thuc thanh cong, khong mo lai theo flow binh thuong

    CANCELLED    // bi huy, khong dung lai nua
}
