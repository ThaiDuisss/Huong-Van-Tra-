package org.hvtra.promotionservice.enums.event;

public enum EventStatus {

    DRAFT,       // mới tạo, đang soạn, chưa public

    SCHEDULED,   // đã lên lịch, chưa tới thời gian chạy

    ACTIVE,      // đang chạy, user có thể thấy / áp dụng

    PAUSED,      // tạm dừng thủ công, có thể resume lại

    EXPIRED,     // hết hạn theo endAt, có thể gia hạn để chạy tiếp

    ENDED,       // đã đóng/kết thúc chiến dịch thành công, không mở lại bình thường

    CANCELLED    // đã hủy chiến dịch, không chạy lại
}

/**
 * Trạng thái vòng đời của Event/Campaign.
 *
 * Quy ước:
 * - FE chỉ hiển thị status, không tự set status trực tiếp.
 * - FE gọi API hành động như schedule, activate, pause, resume, extend, end, cancel.
 * - BE/Validator quyết định status mới có hợp lệ hay không.
 *
 * Ý nghĩa các trạng thái:
 *
 * DRAFT:
 * - Event mới tạo, đang soạn thảo.
 * - Chưa public, chưa được áp dụng promotion/coupon.
 *
 * SCHEDULED:
 * - Event đã được lên lịch.
 * - Chưa tới startAt nên chưa chạy.
 *
 * ACTIVE:
 * - Event đang chạy.
 * - Promotion/coupon thuộc event có thể được áp dụng.
 *
 * PAUSED:
 * - Event đang bị tạm dừng thủ công.
 * - Có thể resume lại nếu chưa hết hạn.
 *
 * EXPIRED:
 * - Event đã hết thời gian endAt tự nhiên.
 * - Có thể extend endAt để chạy tiếp nếu business muốn gia hạn.
 * - Không nên dùng ENDED cho trường hợp hết hạn tự nhiên.
 *
 * ENDED:
 * - Event đã được admin/chủ hệ thống đóng/kết thúc chiến dịch.
 * - Được xem là kết thúc thành công.
 * - Không mở lại theo flow bình thường.
 *
 * CANCELLED:
 * - Event bị hủy.
 * - Dùng cho case tạo nhầm, không triển khai nữa, hoặc hủy chiến dịch.
 * - Không mở lại.
 *
 * Rule chuyển trạng thái:
 *
 * DRAFT:
 * - DRAFT -> SCHEDULED
 *   Khi event đã cấu hình xong và startAt ở tương lai.
 *
 * - DRAFT -> ACTIVE
 *   Khi event được kích hoạt ngay, startAt đã tới hoặc không cần startAt.
 *
 * - DRAFT -> CANCELLED
 *   Khi event tạo nhầm hoặc không triển khai nữa.
 *
 *
 * SCHEDULED:
 * - SCHEDULED -> ACTIVE
 *   Khi tới thời gian chạy hoặc admin kích hoạt thủ công nếu hợp lệ.
 *
 * - SCHEDULED -> EXPIRED
 *   Khi event chưa kịp chạy nhưng đã quá endAt.
 *   Thường do scheduler/system tự xử lý.
 *
 * - SCHEDULED -> CANCELLED
 *   Khi hủy event trước khi chạy.
 *
 *
 * ACTIVE:
 * - ACTIVE -> PAUSED
 *   Khi admin tạm dừng event.
 *
 * - ACTIVE -> EXPIRED
 *   Khi now >= endAt.
 *   Chỉ nên do scheduler/system tự chuyển.
 *
 * - ACTIVE -> ENDED
 *   Khi admin chủ động đóng/kết thúc chiến dịch.
 *
 * - ACTIVE -> CANCELLED
 *   Khi event đang chạy nhưng phải hủy vì lỗi/cấu hình sai/quyết định business.
 *
 *
 * PAUSED:
 * - PAUSED -> ACTIVE
 *   Khi resume và event đã tới thời gian chạy, chưa hết hạn.
 *
 * - PAUSED -> SCHEDULED
 *   Khi resume nhưng startAt vẫn ở tương lai.
 *
 * - PAUSED -> EXPIRED
 *   Khi event đang pause nhưng đã quá endAt.
 *   Chỉ nên do scheduler/system tự chuyển.
 *
 * - PAUSED -> ENDED
 *   Khi admin quyết định đóng luôn event đang tạm dừng.
 *
 * - PAUSED -> CANCELLED
 *   Khi admin quyết định hủy event đang tạm dừng.
 *
 *
 * EXPIRED:
 * - EXPIRED -> ACTIVE
 *   Chỉ khi extend endAt thành công.
 *   Không cho activate trực tiếp nếu chưa gia hạn endAt.
 *
 * - EXPIRED -> ENDED
 *   Khi admin xác nhận đóng chiến dịch đã hết hạn.
 *
 *
 * ENDED:
 * - Không chuyển sang trạng thái khác theo flow bình thường.
 * - Nếu muốn mở lại phải có flow đặc biệt như reopen, cần audit log và quyền cao.
 *
 *
 * CANCELLED:
 * - Không chuyển sang trạng thái khác.
 * - Event đã hủy thì không resume, không activate, không extend.
 */