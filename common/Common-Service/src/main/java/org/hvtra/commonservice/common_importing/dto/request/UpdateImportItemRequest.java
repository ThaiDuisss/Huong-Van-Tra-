package org.hvtra.commonservice.common_importing.dto.request;

import java.util.Map;

public record UpdateImportItemRequest(
        Map<String, Object> payload
) {
}
