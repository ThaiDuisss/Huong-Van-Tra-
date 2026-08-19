package org.hvtra.pos.service;

import org.hvtra.pos.dto.request.PosProductSearchRequest;
import org.hvtra.pos.dto.response.PageResponse;
import org.hvtra.pos.dto.response.PosProductVariantResponse;

import java.util.UUID;

public interface PosProductService {

    PageResponse<PosProductVariantResponse> searchVariants(PosProductSearchRequest request);

    PosProductVariantResponse getPosView(UUID variantId);
}
