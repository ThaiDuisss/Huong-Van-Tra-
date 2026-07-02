package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.dto.response.StockVariantBatchResponse;

import java.util.List;

public interface InventoryStockService {
    List<StockBatchResponse> getStocksByProductIds(StockBatchRequest request);
    List<StockVariantBatchResponse> getStocksByVariantIds(StockVariantBatchRequest request);
}
