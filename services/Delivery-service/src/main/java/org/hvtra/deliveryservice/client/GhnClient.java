//package org.hvtra.deliveryservice.client;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import org.hvtra.deliveryservice.model.request.*;
//import org.hvtra.deliveryservice.model.response.GhnCancelOrderResponse;
//import org.hvtra.deliveryservice.model.response.GhnCreateOrderResponse;
//import org.hvtra.deliveryservice.model.response.GhnFeeResponse;
//import org.hvtra.deliveryservice.model.response.GhnResponse;
//import org.hvtra.deliveryservice.model.response.GhnServiceResponse;
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestHeader;
//
//import java.util.List;
//
//@FeignClient(
//        name = "ghnClient",
//        url = "${delivery.providers.ghn.base-url}"
//)
//public interface GhnClient {
//
//    @PostMapping("/v2/shipping-order/available-services")
//    GhnResponse<List<GhnServiceResponse>> getAvailableServices(
//            @RequestHeader("Token") String token,ß
//            @RequestBody GhnAvailableServicesRequest request
//    );
//
//    @PostMapping("/v2/shipping-order/fee")
//    GhnResponse<GhnFeeResponse> calculateFee(
//            @RequestHeader("Token") String token,
//            @RequestHeader("ShopId") Integer shopId,
//            @RequestBody GhnFeeRequest request
//    );
//
//    @PostMapping("/v2/shipping-order/create")
//    GhnResponse<GhnCreateOrderResponse> createOrder(
//            @RequestHeader("Token") String token,
//            @RequestHeader("ShopId") Integer shopId,
//            @RequestBody GhnCreateOrderRequest request
//    );
//
//    @PostMapping("/v2/shipping-order/detail")
//    GhnResponse<JsonNode> getOrderDetail(
//            @RequestHeader("Token") String token,
//            @RequestBody GhnOrderDetailRequest request
//    );
//
//    @PostMapping("/v2/shipping-order/detail-by-client-code")
//    GhnResponse<JsonNode> getOrderByClientCode(
//            @RequestHeader("Token") String token,
//            @RequestBody GhnOrderByClientCodeRequest request
//    );
//
//    @PostMapping("/v2/switch-status/cancel")
//    GhnResponse<List<GhnCancelOrderResponse>> cancelOrder(
//            @RequestHeader("Token") String token,
//            @RequestHeader("ShopId") Integer shopId,
//            @RequestBody GhnCancelOrderRequest request
//    );
//}
