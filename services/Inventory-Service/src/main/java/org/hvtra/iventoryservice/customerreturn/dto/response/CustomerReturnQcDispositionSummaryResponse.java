package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerReturnQcDispositionSummaryResponse(
        CustomerReturnQcResult result,
        BigDecimal quantity,
        UUID destinationLocationId,
        String destinationLocationName
) {
    public CustomerReturnQcDispositionSummaryResponse(
            CustomerReturnQcResult result,
            BigDecimal quantity
    ) {
        this(result, quantity, null, null);
    }
}
