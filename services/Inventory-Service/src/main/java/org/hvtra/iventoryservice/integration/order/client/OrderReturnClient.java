package org.hvtra.iventoryservice.integration.order.client;

import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnInventoryCommandResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Raw Feign proxy. Application code must inject plain {@code OrderReturnClient}: the
 * {@code @Primary} {@link ResilientOrderReturnClient} is what gets injected. This bean is
 * non-primary and reachable only through its qualifier.
 */
@FeignClient(name = "order-service", primary = false, qualifiers = OrderReturnClient.FEIGN_QUALIFIER)
public interface OrderReturnClient {

    String FEIGN_QUALIFIER = "orderReturnFeignClient";

    @GetMapping("/api/orders/returns/{returnId}")
    OrderReturnResponse getReturnById(@PathVariable("returnId") UUID returnId);

    @GetMapping("/api/orders/returns/{returnId}/inventory-dispositions/{commandId}")
    OrderReturnInventoryCommandResponse getInventoryDisposition(
            @PathVariable("returnId") UUID returnId,
            @PathVariable("commandId") UUID commandId);
}
