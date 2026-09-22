package org.hvtra.commonservice.importing.dto.request;

import java.util.Map;

public record UpdateImportItemRequest(
        Map<String, Object> payload
) {
}
