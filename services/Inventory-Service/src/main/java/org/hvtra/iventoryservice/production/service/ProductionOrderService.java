package org.hvtra.iventoryservice.production.service;

import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductionOrderService {
    PageResponse<ProductionOrderResponse> findAll(
            ProductionOrderStatus status, UUID productVariantId, String search, Pageable pageable);

    ProductionOrderResponse findById(UUID id);

    ProductionOrderResponse create(CreateProductionOrderRequest request);

    ProductionOrderResponse submit(UUID id);

    ProductionOrderResponse approve(UUID id);

    ProductionOrderResponse reject(UUID id);

    ProductionOrderResponse cancel(UUID id, CancelProductionOrderRequest request);
}
