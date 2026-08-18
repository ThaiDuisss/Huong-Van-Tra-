package org.hvtra.iventoryservice.stock.service;

import org.hvtra.iventoryservice.stock.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.stock.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.stock.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;

import java.util.List;

public interface InventoryStockService {
    List<StockBatchResponse> getStocksByProductIds(StockBatchRequest request);
    List<StockVariantBatchResponse> getStocksByVariantIds(StockVariantBatchRequest request);
}
