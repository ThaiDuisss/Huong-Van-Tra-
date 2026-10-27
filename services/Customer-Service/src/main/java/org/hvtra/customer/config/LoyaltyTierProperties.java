package org.hvtra.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "loyalty.tier")
@Component
public class LoyaltyTierProperties {
    private int gracePeriodMonths = 1;
    private YearlyReviewProperties yearlyReview = new YearlyReviewProperties();

    @Getter
    @Setter
    public static class YearlyReviewProperties {
        private int month = 2;
        private int day = 1;
        private String cron = "0 0 1 1 2 *";
    }
}
