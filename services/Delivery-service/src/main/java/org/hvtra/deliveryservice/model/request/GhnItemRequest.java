package org.hvtra.deliveryservice.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GhnItemRequest(
        String name,
        String code,
        Integer quantity,
        Integer price,
        Integer length,
        Integer width,
        Integer height,
        Integer weight
) {
}
