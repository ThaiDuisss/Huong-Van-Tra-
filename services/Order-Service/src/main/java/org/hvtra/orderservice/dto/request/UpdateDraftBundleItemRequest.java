package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDraftBundleItemRequest {
    @Size(max = 255)
    private String bundleName;
    @Size(max = 1000)
    private String note;
    @NotEmpty
    private List<@Valid CustomBundleLineRequest> lines;
}
