package org.hvtra.orderservice.client.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "order.feign-resilience")
public class FeignResilienceProperties {

    /**
     * Per Resilience4j instance: a failed read is retried only if it failed within this time.
     * Keeps a read timeout from being paid twice; a fast failure (refused connection, 503) still
     * gets its second attempt.
     */
    private Map<String, Duration> retryBudget = new HashMap<>();

    public Map<String, Duration> getRetryBudget() {
        return retryBudget;
    }

    public void setRetryBudget(Map<String, Duration> retryBudget) {
        this.retryBudget = retryBudget;
    }
}
