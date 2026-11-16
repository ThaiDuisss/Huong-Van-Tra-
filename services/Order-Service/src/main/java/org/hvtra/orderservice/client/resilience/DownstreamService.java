package org.hvtra.orderservice.client.resilience;

import java.util.Arrays;
import java.util.Optional;

/**
 * Downstream services Order-Service calls through OpenFeign, and the Resilience4j instance
 * (circuit breaker + bulkhead + retry, configured in application.yml) that guards each of them.
 * Every Feign client targeting the same service shares that service's instance.
 */
public enum DownstreamService {

    PAYMENT("payment-service", "paymentService", "thanh toán"),
    INVENTORY("inventory-service", "inventoryService", "tồn kho"),
    CATALOG("catalog-service", "catalogService", "danh mục sản phẩm"),
    CUSTOMER("customer-service", "customerService", "khách hàng");

    private final String feignName;
    private final String instanceName;
    private final String displayName;

    DownstreamService(String feignName, String instanceName, String displayName) {
        this.feignName = feignName;
        this.instanceName = instanceName;
        this.displayName = displayName;
    }

    /** The {@code name} of the {@code @FeignClient}, not its contextId. */
    public String feignName() {
        return feignName;
    }

    public String instanceName() {
        return instanceName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<DownstreamService> fromFeignName(String feignName) {
        return Arrays.stream(values())
                .filter(service -> service.feignName.equalsIgnoreCase(feignName))
                .findFirst();
    }
}
