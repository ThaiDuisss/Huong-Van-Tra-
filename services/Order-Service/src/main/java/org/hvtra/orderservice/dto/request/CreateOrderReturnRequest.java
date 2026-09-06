package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderReturnRequest {

    @NotEmpty
    @Valid
    @Builder.Default
    private List<CreateOrderReturnItemRequest> items = new ArrayList<>();

    private String note;
}
