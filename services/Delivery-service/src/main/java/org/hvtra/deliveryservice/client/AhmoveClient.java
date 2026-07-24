package org.hvtra.deliveryservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveCancelOrderRequest;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveCreateOrderRequest;
import org.hvtra.deliveryservice.model.request.ahmove.AhamoveEstimateOrderRequest;
import org.hvtra.deliveryservice.model.response.ahmove.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "ahamoveClient", url = "${delivery.providers.ahamove.base-url}")

public interface AhmoveClient {
    @GetMapping("/v3/cities")
    List<AhamoveCityResponse> getCities(
            @RequestParam("country_id") String countryId
    );
    @GetMapping("/v3/services")
    List<AhamoveServiceResponse> getServices(
            @RequestParam(value = "city_id", required = false) String cityId,
            @RequestParam(value = "lat", required = false) String latitude,
            @RequestParam(value = "lng", required = false) String longitude,
            @RequestParam(value = "delivery_type", required = false) String deliveryType
    );

    @GetMapping("/v3/services/{serviceId}")
    List<AhamoveServiceResponse> getServiceDetail(
            @PathVariable("serviceId") String serviceId
    );

    @PostMapping(value = "/v3/orders/estimates", consumes = "application/json")
    List<AhamoveEstimateResponse> estimate(
            @RequestBody AhamoveEstimateOrderRequest request
    );

    @PostMapping(value = "/v3/orders", consumes = "application/json")
    AhamoveCreateOrderResponse createOrder(
            @RequestBody AhamoveCreateOrderRequest request
    );

    @GetMapping("/v3/orders/{orderId}")
    AhamoveOrderDetailResponse getOrderDetail(
            @PathVariable("orderId") String orderId
    );

    @GetMapping("/v3/orders/tracks/{trackingNumber}")
    List<AhamoveOrderDetailResponse> getOrdersByTrackingNumber(
            @PathVariable("trackingNumber") String trackingNumber
    );

    @GetMapping("/v3/orders/{orderId}/shared-link")
    AhamoveTrackingLinkResponse getTrackingLink(
            @PathVariable("orderId") String orderId
    );

    @DeleteMapping(value = "/v3/orders/{orderId}", consumes = "application/json")
    JsonNode cancelOrder(
            @PathVariable("orderId") String orderId,
            @RequestBody AhamoveCancelOrderRequest request
    );
}
