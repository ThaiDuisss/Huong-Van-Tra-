package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AhamoveCreateOrderRequest(
        @JsonProperty("order_time") long orderTime,
        @JsonProperty("idle_until") Long idleUntil,
        List<AhamovePathRequest> path,
        @JsonProperty("service_id") String serviceId,
        List<AhamoveSpecialRequest> requests,
        @JsonProperty("payment_method") String paymentMethod,
        //ghi chú cho tài xế
        String remarks,
        @JsonProperty("promo_code") String promoCode,
        List<AhamoveItemRequest> items,
        @JsonProperty("package_detail") List<AhamovePackageDetailRequest> packageDetail,
        @JsonProperty("route_optimized") Boolean routeOptimized
) {
}
