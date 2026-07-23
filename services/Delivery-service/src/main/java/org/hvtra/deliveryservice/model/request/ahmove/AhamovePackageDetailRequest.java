package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AhamovePackageDetailRequest(
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String description
) {
}
