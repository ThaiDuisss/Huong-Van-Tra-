package org.hvtra.iventoryservice.customerreturn.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CustomerReturnQcDispositionReportRequest(
        @NotEmpty List<UUID> returnIds
) {
}
