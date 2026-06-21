package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record CategoryResponse(UUID id, UUID parentId, String name, Status status) {
}

