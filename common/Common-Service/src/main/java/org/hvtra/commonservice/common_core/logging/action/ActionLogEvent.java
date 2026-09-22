package org.hvtra.commonservice.common_core.logging.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Một hành động của người dùng. Được serialize thành JSON và gửi lên topic
 * {@code action.log}, phía File-Service consume rồi lưu bảng {@code action_log}.
 * Các trường giữ gọn, không chứa body đầy đủ để bảng không phình.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionLogEvent {

    /** Trùng với X-Request-ID, dùng để nối với file log kỹ thuật. */
    private String requestId;
    private String userId;
    private String username;
    /** Tên service phát sinh hành động. */
    private String service;
    /** Mã hành động, ví dụ ORDER_CANCEL. */
    private String action;
    private String entityType;
    private String entityId;
    private String httpMethod;
    private String path;
    private String ip;
    /** SUCCESS hoặc FAIL. */
    private String status;
    /** Thông điệp lỗi rút gọn khi status = FAIL. */
    private String errorMessage;
    private Long durationMs;
    /** Mô tả bổ sung, đã che thông tin nhạy cảm và cắt độ dài. */
    private String detail;
    private Instant occurredAt;
}
