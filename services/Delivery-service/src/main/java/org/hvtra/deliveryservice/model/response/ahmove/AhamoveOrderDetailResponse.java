package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveOrderDetailResponse(
        @JsonProperty("_id") String id,
        String status,
        @JsonProperty("sub_status") String subStatus,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("city_id") String cityId,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("total_fee") Long totalFee,
        @JsonProperty("total_price") Long totalPrice,
        @JsonProperty("total_pay") Long totalPay,
        @JsonProperty("distance_fee") Long distanceFee,
        @JsonProperty("request_fee") Long requestFee,
        @JsonProperty("stop_fee") Long stopFee,
        @JsonProperty("vat_fee") Long vatFee,
        Long discount,
        Double distance,
        Long duration,
        @JsonProperty("order_time") Double orderTime,
        @JsonProperty("create_time") Double createTime,
        @JsonProperty("accept_time") Double acceptTime,
        @JsonProperty("pickup_time") Double pickupTime,
        @JsonProperty("complete_time") Double completeTime,
        @JsonProperty("cancel_time") Double cancelTime,
        @JsonProperty("cancel_comment") String cancelComment,
        @JsonProperty("cancel_by_user") Boolean cancelByUser,
        @JsonProperty("supplier_id") String supplierId,
        @JsonProperty("supplier_name") String supplierName,
        @JsonProperty("shared_link") String sharedLink,
        List<AhamovePathResponse> path
) {
    public Long resolvedTotalFee() {
        if (totalFee != null) {
            return totalFee;
        }
        if (totalPrice != null) {
            return totalPrice;
        }
        return totalPay;
    }
}
