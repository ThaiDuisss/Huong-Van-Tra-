package org.hvtra.catalog.client;

import org.hvtra.catalog.enums.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestInventoryClient implements InventoryClient {
    private final RestClient restClient;

    public RestInventoryClient(
            RestClient.Builder loadBalancedRestClientBuilder,
            @Value("${clients.inventory.base-url}") String baseUrl) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public void createProductVariantItem(Long variantId, Long baseUnitId, Status status) {
        restClient.post()
                .uri("/inventory-items")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateInventoryItemRequest(
                        variantId,
                        "PRODUCT_VARIANT",
                        baseUnitId,
                        status.name()))
                .retrieve()
                .toBodilessEntity();
    }

    private record CreateInventoryItemRequest(
            Long referenceId,
            String itemType,
            Long baseUnitId,
            String status) {
    }
}
