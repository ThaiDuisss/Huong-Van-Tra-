package org.hvtra.iventoryservice.production.service;

import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.production.dto.request.CancelProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CompleteProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.BomOptionResponse;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductionOrderService {
    PageResponse<ProductionOrderResponse> findAll(
            ProductionOrderStatus status, UUID productVariantId, String search, Pageable pageable);

    ProductionOrderResponse findById(UUID id);

    List<BomOptionResponse> getBomOptions(UUID productVariantId);

    ProductionOrderResponse create(CreateProductionOrderRequest request);

    ProductionOrderResponse submit(UUID id);

    ProductionOrderResponse approve(UUID id);

    ProductionOrderResponse release(UUID id);

    ProductionOrderResponse start(UUID id);

    ProductionOrderResponse complete(UUID id, CompleteProductionOrderRequest request);

    ProductionOrderResponse cancel(UUID id, CancelProductionOrderRequest request);
}
