package org.hvtra.identityservice.actionlog.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.identityservice.actionlog.service.ActionLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dọn action_log cũ hơn retention mỗi ngày. Tắt bằng hvtra.action-log.purge.enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hvtra.action-log.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ActionLogPurgeJob {

    private final ActionLogService actionLogService;

    @Scheduled(cron = "${hvtra.action-log.purge.cron:0 0 3 * * *}")
    public void run() {
        try {
            actionLogService.purgeExpired();
        } catch (Exception ex) {
            log.error("Action log purge failed: {}", ex.getMessage(), ex);
        }
    }
}
