package org.hvtra.promotionservice.enums.promotion;

public enum PromotionStatus {

    DRAFT,       // mới tạo, đang cấu hình, chưa sẵn sàng áp dụng

    SCHEDULED,   // đã cấu hình xong, chờ tới startAt để kích hoạt

    ACTIVE,      // đang được phép áp dụng cho đơn hàng/user

    INACTIVE,    // tạm khóa thủ công, không cho áp dụng dù còn thời gian

    EXPIRED,     // đã hết hạn theo endAt, có thể gia hạn nếu business cho phép

    CANCELLED    // bị hủy, không dùng lại nữa
}

/**
 * Trạng thái vòng đời của Promotion.
 *
 * Promotion là rule ưu đãi cụ thể, ví dụ:
 * - Giảm 10%
 * - Giảm 50.000đ
 * - Freeship
 * - Mua 2 tặng 1
 * - Coupon theo mã
 *
 * Promotion có thể thuộc Event/Campaign hoặc có thể chạy độc lập.
 *
 * Quy ước:
 * - FE chỉ hiển thị status, không tự set status trực tiếp.
 * - FE gọi API hành động như:
 *   createDraft, schedule, activate, deactivate, resume, extend, expire, cancel.
 * - BE/Validator quyết định status mới có hợp lệ hay không.
 * - Khi check promotion có áp dụng được không, BE phải check cả:
 *   + promotion.status == ACTIVE
 *   + promotion.startAt <= now
 *   + promotion.endAt == null hoặc promotion.endAt > now
 *   + event.status == ACTIVE nếu promotion thuộc event
 *   + usage limit / audience / condition / coupon quantity nếu có
 *
 *
 * Ý nghĩa các trạng thái:
 *
 * DRAFT:
 * - Promotion mới tạo, đang cấu hình.
 * - Chưa được áp dụng cho order/user.
 * - Có thể chỉnh sửa thoải mái.
 *
 * SCHEDULED:
 * - Promotion đã cấu hình xong.
 * - Chờ tới startAt để tự động chuyển ACTIVE.
 * - Chưa được áp dụng trước thời gian bắt đầu.
 *
 * ACTIVE:
 * - Promotion đang chạy.
 * - Có thể được áp dụng nếu thỏa điều kiện business.
 * - Đây là trạng thái duy nhất cho phép apply discount/coupon.
 *
 * INACTIVE:
 * - Promotion bị tạm khóa thủ công.
 * - Dùng khi admin muốn ngưng áp dụng tạm thời nhưng chưa hủy.
 * - Khác EXPIRED vì promotion vẫn còn thời gian.
 * - Khác CANCELLED vì vẫn có thể bật lại nếu hợp lệ.
 *
 * EXPIRED:
 * - Promotion đã hết thời gian áp dụng theo endAt.
 * - Không được apply nữa.
 * - Có thể extend endAt để chạy tiếp nếu business cho phép.
 *
 * CANCELLED:
 * - Promotion bị hủy.
 * - Dùng cho case tạo nhầm, sai rule, sai cấu hình hoặc không muốn triển khai nữa.
 * - Không activate/resume/extend lại theo flow bình thường.
 *
 *
 * Rule chuyển trạng thái:
 *
 * DRAFT:
 * - DRAFT -> SCHEDULED
 *   Khi promotion đã cấu hình xong và startAt ở tương lai.
 *
 * - DRAFT -> ACTIVE
 *   Khi promotion được kích hoạt ngay, startAt đã tới hoặc không cần startAt.
 *
 * - DRAFT -> CANCELLED
 *   Khi promotion tạo nhầm hoặc không triển khai nữa.
 *
 *
 * SCHEDULED:
 * - SCHEDULED -> ACTIVE
 *   Khi tới thời gian startAt hoặc admin kích hoạt thủ công nếu hợp lệ.
 *
 * - SCHEDULED -> EXPIRED
 *   Khi promotion chưa kịp chạy nhưng đã quá endAt.
 *   Thường do scheduler/system tự xử lý.
 *
 * - SCHEDULED -> CANCELLED
 *   Khi admin hủy promotion trước khi chạy.
 *
 *
 * ACTIVE:
 * - ACTIVE -> INACTIVE
 *   Khi admin tạm khóa promotion.
 *   Ví dụ: rule sai, discount quá lớn, muốn kiểm tra lại.
 *
 * - ACTIVE -> EXPIRED
 *   Khi now >= endAt.
 *   Chỉ nên do scheduler/system tự chuyển.
 *
 * - ACTIVE -> CANCELLED
 *   Khi promotion đang chạy nhưng phải hủy hẳn.
 *   Ví dụ: lỗi nghiêm trọng, cấu hình sai, campaign thay đổi.
 *
 *
 * INACTIVE:
 * - INACTIVE -> ACTIVE
 *   Khi admin bật lại promotion và promotion đã tới startAt, chưa hết hạn.
 *
 * - INACTIVE -> SCHEDULED
 *   Khi admin bật lại promotion nhưng startAt vẫn ở tương lai.
 *
 * - INACTIVE -> EXPIRED
 *   Khi promotion đang bị khóa nhưng đã quá endAt.
 *   Chỉ nên do scheduler/system tự chuyển.
 *
 * - INACTIVE -> CANCELLED
 *   Khi admin quyết định hủy hẳn promotion đang bị khóa.
 *
 *
 * EXPIRED:
 * - EXPIRED -> ACTIVE
 *   Chỉ khi extend endAt thành công và promotion đang trong thời gian được phép chạy.
 *
 * - EXPIRED -> SCHEDULED
 *   Khi extend lại nhưng startAt mới vẫn ở tương lai.
 *
 * - EXPIRED -> CANCELLED
 *   Khi admin quyết định hủy promotion đã hết hạn.
 *
 *
 * CANCELLED:
 * - Không chuyển sang trạng thái khác theo flow bình thường.
 * - Promotion đã hủy thì không resume, không activate, không extend.
 * - Nếu muốn dùng lại nên tạo promotion mới để giữ audit rõ ràng.
 */