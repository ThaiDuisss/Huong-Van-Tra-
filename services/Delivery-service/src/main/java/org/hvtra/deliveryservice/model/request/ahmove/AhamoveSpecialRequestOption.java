package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

//yêu cầu đặc biệt nhuư gọi xe van để bốc hàng vd code: {
//  "code": "BOCXEP_RANGES",
//  "selected_value": "RANGE_20M"
//}
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AhamoveSpecialRequestOption(
        String code,
        @JsonProperty("selected_value") String selectedValue
) {
}
