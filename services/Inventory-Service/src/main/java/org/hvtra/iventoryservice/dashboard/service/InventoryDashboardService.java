package org.hvtra.iventoryservice.dashboard.service;

import org.hvtra.iventoryservice.dashboard.dto.response.InventoryDashboardSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiringBatchResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiryQualitySummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryLocationSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryTopSkuResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryDashboardService {

    InventoryDashboardSummaryResponse getOverviewSummary(UUID branchId, UUID locationId);

    List<InventoryLocationSummaryResponse> getLocationBreakdown(UUID branchId);

    List<InventoryTopSkuResponse> getTopSkus(UUID branchId, UUID locationId, int limit);

    InventoryExpiryQualitySummaryResponse getExpiryQualitySummary(UUID branchId, UUID locationId);

    List<InventoryExpiringBatchResponse> getExpiringBatches(UUID branchId, UUID locationId, int daysThreshold, int limit);
}
