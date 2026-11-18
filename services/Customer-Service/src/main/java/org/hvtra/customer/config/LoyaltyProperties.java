package org.hvtra.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "loyalty")
public class LoyaltyProperties {

    private Points points = new Points();

    @Getter
    @Setter
    public static class Points {
        /** Số tiền (VND) thanh toán để được 1 điểm, trước khi nhân hệ số của hạng. */
        private BigDecimal spendPerPoint = new BigDecimal("10000");
    }
}
