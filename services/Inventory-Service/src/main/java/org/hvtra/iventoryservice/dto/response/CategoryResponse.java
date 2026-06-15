package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

public record CategoryResponse(Long id, Long parentId, String name, Status status) {
}
