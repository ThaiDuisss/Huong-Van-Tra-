package org.hvtra.deliveryservice.model.response.ghn;

import java.util.UUID;

public record GhnQuoteResponse(
        UUID shipmentId,
        Integer serviceId,
        Integer serviceTypeId,
        Long totalFee,
        Long serviceFee,
        Long insuranceFee,
        Long codFee,
        GhnFeeResponse detail
) {
}
