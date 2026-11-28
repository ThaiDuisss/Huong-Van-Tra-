package org.hvtra.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDraftBundleItemRequest {
    @Size(max = 255)
    private String bundleName;
    @Size(max = 1000)
    private String note;
    private List<@Valid CustomBundleLineRequest> lines;
    private List<@Valid CustomBundleBomRequest> boms;

    @AssertTrue(message = "Bundle phải có ít nhất một công thức hoặc nguyên liệu")
    public boolean isHasLinesOrBoms() {
        return (lines != null && !lines.isEmpty()) || (boms != null && !boms.isEmpty());
    }
}
