package org.hvtra.iventoryservice.dashboard.service.impl;

import org.hvtra.iventoryservice.dashboard.dto.response.InventoryDashboardSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiringBatchResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiryQualitySummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryLocationSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryTopSkuResponse;
import org.hvtra.iventoryservice.dashboard.repository.InventoryDashboardRepository;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardExpiryQualityRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardExpiringBatchRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.DashboardKpiRow;
import org.hvtra.iventoryservice.dashboard.repository.projection.VariantOnHandRow;
import org.hvtra.iventoryservice.dashboard.service.InventoryDashboardService;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryDashboardServiceImpl implements InventoryDashboardService {

    private final InventoryDashboardRepository dashboardRepository;
    private final CatalogClient catalogClient;

    public InventoryDashboardServiceImpl(
            InventoryDashboardRepository dashboardRepository,
            CatalogClient catalogClient) {
        this.dashboardRepository = dashboardRepository;
        this.catalogClient = catalogClient;
    }

    @Override
    public InventoryDashboardSummaryResponse getOverviewSummary(UUID branchId, UUID locationId) {
        DashboardKpiRow kpi = dashboardRepository.getOverviewKpi(branchId, locationId);

        List<VariantOnHandRow> variantRows = dashboardRepository.getVariantOnHandQuantities(branchId, locationId);
        BigDecimal totalInventoryValue = BigDecimal.ZERO;

        if (variantRows != null && !variantRows.isEmpty()) {
            List<UUID> variantIds = variantRows.stream()
                    .map(r -> {
                        try {
                            return UUID.fromString(r.getReferenceId());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            Map<UUID, BigDecimal> costPriceMap = new HashMap<>();
            try {
                int batchSize = 100;
                for (int i = 0; i < variantIds.size(); i += batchSize) {
                    List<UUID> batch = variantIds.subList(i, Math.min(i + batchSize, variantIds.size()));
                    List<CatalogClient.VariantCostResponse> variants = catalogClient.getVariantCostsBatch(batch);
                    if (variants != null) {
                        for (CatalogClient.VariantCostResponse v : variants) {
                            if (v.id() != null && v.costPrice() != null) {
                                costPriceMap.put(v.id(), v.costPrice());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Fail-safe: fallback gracefully if catalog service is unreachable
            }

            for (VariantOnHandRow row : variantRows) {
                try {
                    UUID vid = UUID.fromString(row.getReferenceId());
                    BigDecimal cost = costPriceMap.getOrDefault(vid, BigDecimal.ZERO);
                    if (row.getTotalOnHand() != null) {
                        totalInventoryValue = totalInventoryValue.add(row.getTotalOnHand().multiply(cost));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        long skuCount = kpi != null && kpi.getSkuCount() != null ? kpi.getSkuCount() : 0L;
        BigDecimal totalOnHand = kpi != null && kpi.getTotalOnHand() != null ? kpi.getTotalOnHand() : BigDecimal.ZERO;
        BigDecimal totalAvailable = kpi != null && kpi.getTotalAvailable() != null ? kpi.getTotalAvailable().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal totalReserved = kpi != null && kpi.getTotalReserved() != null ? kpi.getTotalReserved() : BigDecimal.ZERO;
        BigDecimal totalBlocked = kpi != null && kpi.getTotalBlocked() != null ? kpi.getTotalBlocked() : BigDecimal.ZERO;
        long belowReorderCount = kpi != null && kpi.getBelowReorderCount() != null ? kpi.getBelowReorderCount() : 0L;
        long pendingDocumentsCount = kpi != null && kpi.getPendingDocumentsCount() != null ? kpi.getPendingDocumentsCount() : 0L;

        return new InventoryDashboardSummaryResponse(
                skuCount,
                totalOnHand,
                totalAvailable,
                totalReserved,
                totalBlocked,
                totalInventoryValue,
                belowReorderCount,
                pendingDocumentsCount
        );
    }

    @Override
    public List<InventoryLocationSummaryResponse> getLocationBreakdown(UUID branchId) {
        return dashboardRepository.getLocationBreakdown(branchId).stream()
                .map(r -> new InventoryLocationSummaryResponse(
                        UUID.fromString(r.getLocationId()),
                        r.getLocationCode(),
                        r.getLocationName(),
                        r.getTotalOnHand(),
                        r.getTotalAvailable(),
                        r.getTotalReserved()
                ))
                .toList();
    }

    @Override
    public List<InventoryTopSkuResponse> getTopSkus(UUID branchId, UUID locationId, int limit) {
        return dashboardRepository.getTopSkus(branchId, locationId, limit > 0 ? limit : 6).stream()
                .map(r -> new InventoryTopSkuResponse(
                        UUID.fromString(r.getInventoryItemId()),
                        r.getSkuCode(),
                        r.getProductName(),
                        r.getLocationName(),
                        r.getQuantityOnHand(),
                        r.getAvailableQuantity(),
                        r.getUnitSymbol()
                ))
                .toList();
    }

    @Override
    public InventoryExpiryQualitySummaryResponse getExpiryQualitySummary(UUID branchId, UUID locationId) {
        DashboardExpiryQualityRow row = dashboardRepository.getExpiryQualitySummary(branchId);
        long exp7 = row != null && row.getExpiry7Days() != null ? row.getExpiry7Days() : 0L;
        long exp30 = row != null && row.getExpiry30Days() != null ? row.getExpiry30Days() : 0L;
        long expiredUnhandled = row != null && row.getExpiredUnhandled() != null ? row.getExpiredUnhandled() : 0L;
        long quarantineOverSla = row != null && row.getQuarantineOverSla() != null ? row.getQuarantineOverSla() : 0L;
        long pendingDisposal = row != null && row.getPendingDisposalApprovals() != null ? row.getPendingDisposalApprovals() : 0L;

        return new InventoryExpiryQualitySummaryResponse(
                exp7,
                exp30,
                expiredUnhandled,
                quarantineOverSla,
                BigDecimal.ZERO,
                pendingDisposal
        );
    }

    @Override
    public List<InventoryExpiringBatchResponse> getExpiringBatches(UUID branchId, UUID locationId, int daysThreshold, int limit) {
        int effectiveLimit = limit > 0 ? limit : 5;
        int effectiveDays = daysThreshold > 0 ? daysThreshold : 30;
        List<DashboardExpiringBatchRow> rows = dashboardRepository.getExpiringBatches(branchId, locationId, effectiveDays, effectiveLimit);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(r -> {
                    UUID id = null;
                    try {
                        if (r.getId() != null) id = UUID.fromString(r.getId());
                    } catch (Exception ignored) {}

                    UUID itemId = null;
                    try {
                        if (r.getInventoryItemId() != null) itemId = UUID.fromString(r.getInventoryItemId());
                    } catch (Exception ignored) {}

                    UUID locId = null;
                    try {
                        if (r.getLocationId() != null) locId = UUID.fromString(r.getLocationId());
                    } catch (Exception ignored) {}

                    long days = r.getDaysRemaining() != null ? r.getDaysRemaining() : 0L;
                    String urgency;
                    String urgencyLabel;
                    if (days < 0) {
                        urgency = "CRITICAL";
                        urgencyLabel = "Đã hết hạn";
                    } else if (days <= 7) {
                        urgency = "CRITICAL";
                        urgencyLabel = "Rất gấp";
                    } else if (days <= 15) {
                        urgency = "HIGH";
                        urgencyLabel = "Gấp";
                    } else {
                        urgency = "MEDIUM";
                        urgencyLabel = "Lưu ý";
                    }

                    return new InventoryExpiringBatchResponse(
                            id,
                            r.getBatchCode(),
                            itemId,
                            r.getSkuCode(),
                            r.getProductName(),
                            locId,
                            r.getLocationName(),
                            r.getOnHandQuantity() != null ? r.getOnHandQuantity() : BigDecimal.ZERO,
                            r.getAvailableQuantity() != null ? r.getAvailableQuantity() : BigDecimal.ZERO,
                            r.getUnit() != null ? r.getUnit() : "đơn vị",
                            r.getExpiryDate(),
                            days,
                            urgency,
                            urgencyLabel,
                            r.getStatus() != null ? r.getStatus() : "ACTIVE"
                    );
                })
                .toList();
    }
}
