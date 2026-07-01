package org.hvtra.promotionservice.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventApiParameter {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_ID_DESCRIPTION = "ID của sự kiện";
    public static final String EVENT_ID_EXAMPLE = "550e8400-e29b-41d4-a716-446655440000";

    public static final String PAGE = "page";
    public static final String PAGE_DESCRIPTION = "Số trang, bắt đầu từ 0";
    public static final String PAGE_DEFAULT = "0";
    public static final String PAGE_EXAMPLE = "0";

    public static final String SIZE = "size";
    public static final String SIZE_DESCRIPTION = "Số lượng bản ghi trên mỗi trang";
    public static final String SIZE_DEFAULT = "10";
    public static final String SIZE_EXAMPLE = "10";
    public static final int SIZE_MAX = 100;
}
