package org.hvtra.iventoryservice.customerreturn.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateCustomerReturnInspectionRequest(
        String note,
        @NotEmpty List<@Valid CreateCustomerReturnInspectionLineRequest> lines
) {
}
