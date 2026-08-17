package org.hvtra.deliveryservice.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GhnCreateOrderRequest(
        @JsonProperty("client_order_code") String clientOrderCode,
        @JsonProperty("from_name") String fromName,
        @JsonProperty("from_phone") String fromPhone,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("from_ward_code") String fromWardCode,
        @JsonProperty("from_district_id") Integer fromDistrictId,
        @JsonProperty("return_name") String returnName,
        @JsonProperty("return_phone") String returnPhone,
        @JsonProperty("return_address") String returnAddress,
        @JsonProperty("return_ward_code") String returnWardCode,
        @JsonProperty("return_district_id") Integer returnDistrictId,
        @JsonProperty("to_name") String toName,
        @JsonProperty("to_phone") String toPhone,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("to_ward_code") String toWardCode,
        @JsonProperty("to_district_id") Integer toDistrictId,
        @JsonProperty("cod_amount") Integer codAmount,
        @JsonProperty("insurance_value") Integer insuranceValue,
        @JsonProperty("service_id") Integer serviceId,
        @JsonProperty("service_type_id") Integer serviceTypeId,
        @JsonProperty("payment_type_id") Integer paymentTypeId,
        @JsonProperty("required_note") String requiredNote,
        String content,
        String note,
        Integer weight,
        Integer length,
        Integer width,
        Integer height,
        String coupon,
        @JsonProperty("pick_shift") List<Integer> pickShift,
        List<GhnItemRequest> items
) {
}
