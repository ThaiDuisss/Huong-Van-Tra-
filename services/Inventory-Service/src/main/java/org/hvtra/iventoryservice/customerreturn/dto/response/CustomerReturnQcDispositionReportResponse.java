package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;

public record CustomerReturnQcDispositionReportResponse(
        CustomerReturnQcResult result,
        BigDecimal quantity
) {
}
