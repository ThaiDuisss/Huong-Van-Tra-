package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.PosProductSearchRequest;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.hvtra.orderservice.dto.response.PosProductVariantResponse;

import java.util.UUID;

public interface PosProductService {

    PageResponse<PosProductVariantResponse> searchVariants(PosProductSearchRequest request);

    PosProductVariantResponse getPosView(UUID variantId);
}

