package org.hvtra.iventoryservice.service;

import org.hvtra.iventoryservice.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.dto.response.StockBatchResponse;

import java.util.List;

public interface InventoryStockService {
    List<StockBatchResponse> getStocksByProductIds(StockBatchRequest request);
}
