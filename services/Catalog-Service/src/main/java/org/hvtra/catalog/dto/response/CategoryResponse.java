package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

public record CategoryResponse(Long id, Long parentId, String name, Status status) {
}
