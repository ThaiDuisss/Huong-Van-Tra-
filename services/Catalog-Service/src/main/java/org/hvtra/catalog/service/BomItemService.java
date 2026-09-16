package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.BomItemRequest;
import org.hvtra.catalog.dto.response.BomItemResponse;

import java.util.List;
import java.util.UUID;

public interface BomItemService {
    List<BomItemResponse> findByVariant(UUID productVariantId);
    List<BomItemResponse> replaceForVariant(UUID productVariantId, List<BomItemRequest> items);
}
