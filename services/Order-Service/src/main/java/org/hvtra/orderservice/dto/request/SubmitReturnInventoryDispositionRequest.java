package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReturnInventoryDispositionRequest {

    @NotNull
    private UUID commandId;

    @NotEmpty
    @Valid
    @Builder.Default
    private List<SubmitReturnInventoryDispositionLineRequest> lines = new ArrayList<>();
}
