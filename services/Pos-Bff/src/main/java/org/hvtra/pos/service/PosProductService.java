package org.hvtra.pos.service;

import org.hvtra.pos.dto.request.PosProductSearchRequest;
import org.hvtra.pos.dto.response.PosProductVariantResponse;

import java.util.List;
import java.util.UUID;

public interface PosProductService {

    List<PosProductVariantResponse> searchVariants(PosProductSearchRequest request);

    PosProductVariantResponse getPosView(UUID variantId);
}
