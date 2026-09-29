package org.hvtra.iventoryservice.batch.service;

import org.hvtra.iventoryservice.stock.dto.response.WarehouseBatchResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryBatchService {
    /**
     * Lấy danh sách batch của một item tại một location, sắp xếp theo FEFO (expiry date)
     */
    List<WarehouseBatchResponse> getBatchesByItemAndLocation(UUID itemId, UUID locationId);

    /**
     * Lấy batch chi tiết với quantity per location
     */
    WarehouseBatchResponse getBatchDetail(UUID batchId, UUID locationId);
}
