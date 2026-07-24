package org.hvtra.deliveryservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.deliveryservice.model.request.ghn.*;
import org.hvtra.deliveryservice.model.response.ghn.GhnCancelOrderResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnCreateOrderResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnFeeResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnServiceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(
        name = "ghnClient",
        url = "${delivery.providers.ghn.base-url}"
)
public interface GhnClient {

    @PostMapping("/v2/shipping-order/available-services")
    GhnResponse<List<GhnServiceResponse>> getAvailableServices(
            @RequestBody GhnAvailableServicesRequest request
    );

    @PostMapping("/v2/shipping-order/fee")
    GhnResponse<GhnFeeResponse> calculateFee(
            @RequestHeader("ShopId") Integer shopId,
            @RequestBody GhnFeeRequest request
    );

    @PostMapping("/v2/shipping-order/create")
    GhnResponse<GhnCreateOrderResponse> createOrder(
            @RequestHeader("ShopId") Integer shopId,
            @RequestBody GhnCreateOrderRequest request
    );

    @PostMapping("/v2/shipping-order/detail")
    GhnResponse<JsonNode> getOrderDetail(
            @RequestBody GhnOrderDetailRequest request
    );

    @PostMapping("/v2/shipping-order/detail-by-client-code")
    GhnResponse<JsonNode> getOrderByClientCode(
            @RequestBody GhnOrderByClientCodeRequest request
    );

    @PostMapping("/v2/switch-status/cancel")
    GhnResponse<List<GhnCancelOrderResponse>> cancelOrder(
            @RequestHeader("ShopId") Integer shopId,
            @RequestBody GhnCancelOrderRequest request
    );
}
