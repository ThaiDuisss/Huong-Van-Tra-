package org.hvtra.deliveryservice.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnCreateOrderRequest(
        @JsonProperty("client_order_code")
        String clientOrderCode,

        @JsonProperty("to_name")
        String toName,

        @JsonProperty("to_phone")
        String toPhone,

        @JsonProperty("to_address")
        String toAddress,

        @JsonProperty("to_ward_code")
        String toWardCode,

        @JsonProperty("to_district_id")
        Integer toDistrictId,

        @JsonProperty("cod_amount")
        Integer codAmount,

        @JsonProperty("insurance_value")
        Integer insuranceValue,

        @JsonProperty("service_id")
        Integer serviceId,

        @JsonProperty("service_type_id")
        Integer serviceTypeId,

        @JsonProperty("payment_type_id")
        Integer paymentTypeId,

        @JsonProperty("required_note")
        String requiredNote,

        String content,
        String note,
        Integer weight,
        Integer length,
        Integer width,
        Integer height,
        List<GhnItemRequest> items
) {
}