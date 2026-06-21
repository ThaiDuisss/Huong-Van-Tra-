package org.hvtra.catalog.client;

import lombok.Builder;
import org.hvtra.catalog.enums.Status;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface InventoryClient {
    @PostMapping("/inventory-items")
    void createProductVariantItem(
            @RequestBody CreateInventoryItemRequest request
    );

    @Builder
    record CreateInventoryItemRequest(
            UUID referenceId,
            String itemType,
            UUID baseUnitId,
            String status
    ) {
    }
}

