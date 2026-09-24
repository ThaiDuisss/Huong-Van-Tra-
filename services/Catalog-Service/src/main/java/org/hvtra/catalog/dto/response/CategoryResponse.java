package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

/** parentName chỉ có ở API danh sách/chi tiết danh mục (đã fetch sẵn danh mục cha), nơi khác là null. */
public record CategoryResponse(UUID id, UUID parentId, String name, Status status, String categoryCode, Integer defaultReorderPoint,
                               String parentName) {
}

