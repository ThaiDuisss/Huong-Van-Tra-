package org.hvtra.customer.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.service.YearlyTierReviewService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TierYearlyReviewScheduler {
    private final YearlyTierReviewService yearlyTierReviewService;

    @Scheduled(cron = "${loyalty.tier.yearly-review.cron:0 0 1 1 2 *}")
    public void run() {
        log.info("Starting yearly tier review job");
        yearlyTierReviewService.reviewCurrentYear();
        log.info("Completed yearly tier review job");
    }
}
