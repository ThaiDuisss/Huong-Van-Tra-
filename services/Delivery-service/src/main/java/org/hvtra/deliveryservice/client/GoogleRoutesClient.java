package org.hvtra.deliveryservice.client;

import org.hvtra.deliveryservice.service.internalDelivery.routing.google.GoogleRoutesRequest;
import org.hvtra.deliveryservice.service.internalDelivery.routing.google.GoogleRoutesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "googleRoutesClient",
        url = "${delivery.internal-routing.google-routes.base-url}"
)
public interface GoogleRoutesClient {

    @PostMapping(value = "/directions/v2:computeRoutes", consumes = "application/json")
    GoogleRoutesResponse computeRoutes(
            @RequestHeader("X-Goog-Api-Key") String apiKey,
            @RequestHeader("X-Goog-FieldMask") String fieldMask,
            @RequestBody GoogleRoutesRequest request
    );
}
