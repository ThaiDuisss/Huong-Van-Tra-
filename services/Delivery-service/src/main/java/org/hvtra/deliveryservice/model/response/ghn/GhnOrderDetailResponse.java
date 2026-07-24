package org.hvtra.deliveryservice.model.response.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnOrderDetailResponse(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("client_order_code") String clientOrderCode,
        String status,
        @JsonProperty("service_id") Integer serviceId,
        @JsonProperty("service_type_id") Integer serviceTypeId,
        @JsonProperty("cod_amount") Long codAmount,
        @JsonProperty("total_fee") Long totalFee,
        String leadtime,
        String reason,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("updated_date") String updatedDate,
        @JsonProperty("finish_date") String finishDate,
        @JsonProperty("cod_collect_date") String codCollectDate,
        @JsonProperty("cod_transfer_date") String codTransferDate,
        @JsonProperty("is_cod_collected") Boolean codCollected,
        @JsonProperty("is_cod_transferred") Boolean codTransferred
) {
}
