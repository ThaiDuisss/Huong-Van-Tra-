package org.hvtra.iventoryservice.integration.order.client;

import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnInventoryCommandResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderReturnClient {

    @GetMapping("/api/orders/returns/{returnId}")
    OrderReturnResponse getReturnById(@PathVariable("returnId") UUID returnId);

    @GetMapping("/api/orders/returns/{returnId}/inventory-dispositions/{commandId}")
    OrderReturnInventoryCommandResponse getInventoryDisposition(
            @PathVariable("returnId") UUID returnId,
            @PathVariable("commandId") UUID commandId);
}
