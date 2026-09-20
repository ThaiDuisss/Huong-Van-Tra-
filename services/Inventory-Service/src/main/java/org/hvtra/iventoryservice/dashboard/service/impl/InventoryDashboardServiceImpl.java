package org.hvtra.iventoryservice.dashboard.service.impl;

import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryDashboardSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryExpiryQualitySummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryLocationSummaryResponse;
import org.hvtra.iventoryservice.dashboard.dto.response.InventoryTopSkuResponse;
import org.hvtra.iventoryservice.dashboard.service.InventoryDashboardService;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryDashboardServiceImpl implements InventoryDashboardService {

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryDocumentRepository documentRepository;
    private final InventoryBatchRepository batchRepository;
    private final CatalogClient catalogClient;

    public InventoryDashboardServiceImpl(
            InventoryBalanceRepository balanceRepository,
            InventoryDocumentRepository documentRepository,
            InventoryBatchRepository batchRepository,
            CatalogClient catalogClient) {
        this.balanceRepository = balanceRepository;
        this.documentRepository = documentRepository;
        this.batchRepository = batchRepository;
        this.catalogClient = catalogClient;
    }

    @Override
    public InventoryDashboardSummaryResponse getOverviewSummary(UUID branchId, UUID locationId) {
        List<InventoryBalance> balances = balanceRepository.findAll();

        List<InventoryBalance> filteredBalances = balances.stream()
                .filter(b -> locationId == null || (b.getLocation() != null && locationId.equals(b.getLocation().getId())))
                .filter(b -> branchId == null || (b.getLocation() != null && branchId.equals(b.getLocation().getBranchId())))
                .toList();

        long skuCount = filteredBalances.stream()
                .filter(b -> b.getInventoryItem() != null)
                .map(b -> b.getInventoryItem().getId())
                .distinct()
                .count();

        BigDecimal totalOnHand = filteredBalances.stream()
                .map(InventoryBalance::getQuantityOnHand)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalReserved = filteredBalances.stream()
                .map(InventoryBalance::getQuantityReserved)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBlocked = filteredBalances.stream()
                .map(InventoryBalance::getQuantityBlocked)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAvailable = totalOnHand.subtract(totalReserved).subtract(totalBlocked);

        long belowReorderCount = filteredBalances.stream()
                .filter(b -> b.getInventoryItem() != null && b.getInventoryItem().getReorderPoint() != null && b.getInventoryItem().getReorderPoint() > 0)
                .filter(b -> {
                    BigDecimal onHand = b.getQuantityOnHand() != null ? b.getQuantityOnHand() : BigDecimal.ZERO;
                    BigDecimal reserved = b.getQuantityReserved() != null ? b.getQuantityReserved() : BigDecimal.ZERO;
                    BigDecimal blocked = b.getQuantityBlocked() != null ? b.getQuantityBlocked() : BigDecimal.ZERO;
                    BigDecimal avail = onHand.subtract(reserved).subtract(blocked);
                    return avail.compareTo(BigDecimal.valueOf(b.getInventoryItem().getReorderPoint())) <= 0;
                })
                .count();

        List<UUID> variantIds = filteredBalances.stream()
                .filter(b -> b.getInventoryItem() != null && b.getInventoryItem().getItemType() == InventoryItemType.PRODUCT_VARIANT)
                .map(b -> b.getInventoryItem().getReferenceId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, BigDecimal> costPriceMap = new HashMap<>();
        if (!variantIds.isEmpty()) {
            try {
                int batchSize = 100;
                for (int i = 0; i < variantIds.size(); i += batchSize) {
                    List<UUID> batch = variantIds.subList(i, Math.min(i + batchSize, variantIds.size()));
                    List<CatalogClient.VariantResponse> variants = catalogClient.getVariantsBatch(batch);
                    if (variants != null) {
                        for (CatalogClient.VariantResponse v : variants) {
                            if (v.id() != null && v.costPrice() != null) {
                                costPriceMap.put(v.id(), v.costPrice());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Fail-safe: fallback gracefully if catalog service is unreachable or times out
            }
        }

        BigDecimal totalInventoryValue = filteredBalances.stream()
                .map(b -> {
                    if (b.getInventoryItem() == null || b.getQuantityOnHand() == null) {
                        return BigDecimal.ZERO;
                    }
                    BigDecimal costPrice = costPriceMap.getOrDefault(b.getInventoryItem().getReferenceId(), BigDecimal.ZERO);
                    return b.getQuantityOnHand().multiply(costPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingDocumentsCount = documentRepository.findAll().stream()
                .filter(d -> !d.getIsDeleted())
                .filter(d -> d.getStatus() == InventoryDocumentStatus.DRAFT
                        || d.getStatus() == InventoryDocumentStatus.PENDING_APPROVAL
                        || d.getStatus() == InventoryDocumentStatus.RECEIVING
                        || d.getStatus() == InventoryDocumentStatus.QC_PENDING)
                .count();

        return new InventoryDashboardSummaryResponse(
                skuCount,
                totalOnHand,
                totalAvailable.max(BigDecimal.ZERO),
                totalReserved,
                totalBlocked,
                totalInventoryValue,
                belowReorderCount,
                pendingDocumentsCount
        );
    }

    @Override
    public List<InventoryLocationSummaryResponse> getLocationBreakdown(UUID branchId) {
        List<InventoryBalance> balances = balanceRepository.findAll();

        return balances.stream()
                .filter(b -> b.getLocation() != null)
                .filter(b -> branchId == null || branchId.equals(b.getLocation().getBranchId()))
                .collect(java.util.stream.Collectors.groupingBy(InventoryBalance::getLocation))
                .entrySet().stream()
                .map(entry -> {
                    var loc = entry.getKey();
                    var list = entry.getValue();
                    BigDecimal onHand = list.stream().map(InventoryBalance::getQuantityOnHand).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal reserved = list.stream().map(InventoryBalance::getQuantityReserved).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal blocked = list.stream().map(InventoryBalance::getQuantityBlocked).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal available = onHand.subtract(reserved).subtract(blocked).max(BigDecimal.ZERO);

                    return new InventoryLocationSummaryResponse(
                            loc.getId(),
                            loc.getCode(),
                            loc.getName(),
                            onHand,
                            available,
                            reserved
                    );
                })
                .toList();
    }

    @Override
    public List<InventoryTopSkuResponse> getTopSkus(UUID branchId, UUID locationId, int limit) {
        List<InventoryBalance> balances = balanceRepository.findAll();

        return balances.stream()
                .filter(b -> locationId == null || (b.getLocation() != null && locationId.equals(b.getLocation().getId())))
                .sorted((b1, b2) -> b2.getQuantityOnHand().compareTo(b1.getQuantityOnHand()))
                .limit(limit > 0 ? limit : 6)
                .map(b -> new InventoryTopSkuResponse(
                        b.getInventoryItem().getId(),
                        b.getInventoryItem().getReferenceCode() != null ? b.getInventoryItem().getReferenceCode() : "N/A",
                        b.getInventoryItem().getName() != null ? b.getInventoryItem().getName() : "Sản phẩm",
                        b.getLocation() != null ? b.getLocation().getName() : "Kho",
                        b.getQuantityOnHand(),
                        b.getAvailableQuantity(),
                        "Cái"
                ))
                .toList();
    }

    @Override
    public InventoryExpiryQualitySummaryResponse getExpiryQualitySummary(UUID branchId, UUID locationId) {
        LocalDate today = LocalDate.now();
        LocalDate in7Days = today.plusDays(7);
        LocalDate in30Days = today.plusDays(30);

        long exp7 = batchRepository.findAll().stream()
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> b.getExpiryDate().isAfter(today) && b.getExpiryDate().isBefore(in7Days))
                .count();

        long exp30 = batchRepository.findAll().stream()
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> b.getExpiryDate().isAfter(today) && b.getExpiryDate().isBefore(in30Days))
                .count();

        long expiredUnhandled = batchRepository.findAll().stream()
                .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today))
                .count();

        long quarantineOverSla = batchRepository.findAll().stream()
                .filter(b -> b.getStatus() == org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus.AWAITING_QC
                        || b.getStatus() == org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus.QUARANTINED)
                .filter(b -> b.getReceivedAt() != null && b.getReceivedAt().isBefore(java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)))
                .count();

        long pendingDisposalApprovals = documentRepository.findAll().stream()
                .filter(d -> !d.getIsDeleted())
                .filter(d -> d.getDocumentType() == org.hvtra.iventoryservice.domain.enums.InventoryDocumentType.WRITE_OFF
                        && d.getStatus() == InventoryDocumentStatus.PENDING_APPROVAL)
                .count();

        return new InventoryExpiryQualitySummaryResponse(
                exp7,
                exp30,
                expiredUnhandled,
                quarantineOverSla,
                BigDecimal.ZERO,
                pendingDisposalApprovals
        );
    }
}

