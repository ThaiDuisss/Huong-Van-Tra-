package org.hvtra.identityservice.actionlog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình consumer và job dọn action log, prefix {@code hvtra.action-log}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hvtra.action-log")
public class ActionLogProperties {

    /** Số bản ghi tối đa mỗi lần poll, cũng là kích thước batch insert. */
    private int maxPollRecords = 500;

    /** Số lần thử lại cả batch khi insert DB lỗi, trước khi ghi fallback ra file. */
    private int retryAttempts = 3;

    /** Khoảng chờ giữa các lần thử lại (ms). */
    private long retryBackoffMs = 2000;

    /** Số ngày giữ dữ liệu trong bảng. */
    private int retentionDays = 365;

    private Purge purge = new Purge();

    @Getter
    @Setter
    public static class Purge {
        private boolean enabled = true;
        /** Mặc định 03:00 mỗi ngày. */
        private String cron = "0 0 3 * * *";
        /** Số dòng xóa mỗi lượt. */
        private int batchSize = 5000;
    }
}
