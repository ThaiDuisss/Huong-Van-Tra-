package org.hvtra.iventoryservice.report.service.impl;

import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.document.repository.InventoryDocumentRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransaction;
import org.hvtra.iventoryservice.domain.entity.movement.InventoryTransactionLine;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.report.dto.*;
import org.hvtra.iventoryservice.report.service.InventoryReportService;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryReportServiceImpl implements InventoryReportService {

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryDocumentRepository documentRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryReportServiceImpl(
            InventoryBalanceRepository balanceRepository,
            InventoryBatchRepository batchRepository,
            InventoryDocumentRepository documentRepository,
            InventoryTransactionRepository transactionRepository) {
        this.balanceRepository = balanceRepository;
        this.batchRepository = batchRepository;
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public InventoryValueReportResponse getInventoryValueReport(LocalDate fromDate, LocalDate toDate, UUID locationId, String categoryCode) {
        List<InventoryBalance> balances = balanceRepository.findAll();

        List<InventoryBalance> filtered = balances.stream()
                .filter(b -> locationId == null || (b.getLocation() != null && locationId.equals(b.getLocation().getId())))
                .toList();

        BigDecimal totalOnHand = filtered.stream()
                .map(InventoryBalance::getQuantityOnHand)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long skuCount = filtered.stream()
                .filter(b -> b.getInventoryItem() != null)
                .map(b -> b.getInventoryItem().getId())
                .distinct()
                .count();

        Map<String, List<InventoryBalance>> byCategoryMap = filtered.stream()
                .filter(b -> b.getInventoryItem() != null)
                .collect(Collectors.groupingBy(b -> b.getInventoryItem().getItemType() != null && b.getInventoryItem().getItemType().name().equals("INGREDIENT") ? "Nguyên liệu" : "Thành phẩm/HH"));

        List<InventoryValueReportResponse.CategoryValueSummary> categorySummaries = new ArrayList<>();
        BigDecimal grandTotalValue = BigDecimal.ZERO;

        for (Map.Entry<String, List<InventoryBalance>> entry : byCategoryMap.entrySet()) {
            String catName = entry.getKey();
            List<InventoryBalance> list = entry.getValue();
            BigDecimal catQty = list.stream().map(InventoryBalance::getQuantityOnHand).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            long catSkus = list.stream().map(b -> b.getInventoryItem().getId()).distinct().count();
            BigDecimal catValue = BigDecimal.ZERO;

            grandTotalValue = grandTotalValue.add(catValue);

            categorySummaries.add(new InventoryValueReportResponse.CategoryValueSummary(
                    catName.equalsIgnoreCase("Nguyên liệu") ? "RAW_MAT" : "FINISHED_GOOD",
                    catName,
                    catSkus,
                    catQty,
                    catValue,
                    0.0
            ));
        }

        Map<UUID, List<InventoryBalance>> byLocationMap = filtered.stream()
                .filter(b -> b.getLocation() != null)
                .collect(Collectors.groupingBy(b -> b.getLocation().getId()));

        List<InventoryValueReportResponse.LocationValueSummary> locationSummaries = byLocationMap.entrySet().stream()
                .map(e -> {
                    var loc = e.getValue().get(0).getLocation();
                    BigDecimal locQty = e.getValue().stream().map(InventoryBalance::getQuantityOnHand).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new InventoryValueReportResponse.LocationValueSummary(
                            loc.getId(),
                            loc.getCode(),
                            loc.getName(),
                            locQty,
                            BigDecimal.ZERO
                    );
                })
                .toList();

        List<InventoryValueReportResponse.ItemValueDetail> topValuedItems = filtered.stream()
                .filter(b -> b.getInventoryItem() != null)
                .limit(50)
                .map(b -> new InventoryValueReportResponse.ItemValueDetail(
                        b.getInventoryItem().getId(),
                        b.getInventoryItem().getReferenceCode() != null ? b.getInventoryItem().getReferenceCode() : "SKU",
                        b.getInventoryItem().getName() != null ? b.getInventoryItem().getName() : "Sản phẩm",
                        b.getInventoryItem().getItemType() != null && b.getInventoryItem().getItemType().name().equals("INGREDIENT") ? "Nguyên liệu" : "Thành phẩm",
                        b.getLocation() != null ? b.getLocation().getName() : "Kho",
                        "Cái",
                        b.getQuantityOnHand(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ))
                .toList();

        return InventoryValueReportResponse.builder()
                .totalValue(grandTotalValue)
                .totalOnHandQuantity(totalOnHand)
                .totalSkuCount(skuCount)
                .categoryBreakdown(categorySummaries)
                .locationBreakdown(locationSummaries)
                .topValuedItems(topValuedItems)
                .build();
    }

    @Override
    public InventoryTurnoverReportResponse getInventoryTurnoverReport(LocalDate fromDate, LocalDate toDate, UUID locationId, String categoryCode) {
        List<InventoryBalance> balances = balanceRepository.findAll();
        List<InventoryTransaction> transactions = transactionRepository.findAll();

        Map<UUID, BigDecimal> soldQuantityMap = new HashMap<>();
        for (InventoryTransaction tx : transactions) {
            if (tx.getType() == InventoryTransactionType.SALE_OUT) {
                for (InventoryTransactionLine line : tx.getLines()) {
                    if (line.getInventoryItem() != null && line.getInventoryItem().getId() != null && line.getQuantity() != null) {
                        soldQuantityMap.merge(line.getInventoryItem().getId(), line.getQuantity(), BigDecimal::add);
                    }
                }
            }
        }

        List<InventoryTurnoverReportResponse.TurnoverItemDetail> items = new ArrayList<>();
        long slowMovingCount = 0;
        double totalTurnover = 0;
        int activeCount = 0;

        for (InventoryBalance b : balances) {
            if (b.getInventoryItem() == null) continue;
            UUID itemId = b.getInventoryItem().getId();
            BigDecimal onHand = b.getQuantityOnHand() != null ? b.getQuantityOnHand() : BigDecimal.ZERO;
            BigDecimal soldQty = soldQuantityMap.getOrDefault(itemId, BigDecimal.ZERO);

            double turnoverRate = 0.0;
            if (onHand.compareTo(BigDecimal.ZERO) > 0 && soldQty.compareTo(BigDecimal.ZERO) > 0) {
                turnoverRate = soldQty.doubleValue() / onHand.doubleValue();
            }

            double dsi = turnoverRate > 0 ? (365.0 / turnoverRate) : (onHand.compareTo(BigDecimal.ZERO) > 0 ? 365.0 : 0.0);
            String abc = turnoverRate >= 2.0 ? "A" : (turnoverRate >= 0.5 ? "B" : "C");

            if (dsi > 90 && onHand.compareTo(BigDecimal.ZERO) > 0) {
                slowMovingCount++;
            }

            if (onHand.compareTo(BigDecimal.ZERO) > 0) {
                totalTurnover += turnoverRate;
                activeCount++;
            }

            items.add(new InventoryTurnoverReportResponse.TurnoverItemDetail(
                    itemId,
                    b.getInventoryItem().getReferenceCode() != null ? b.getInventoryItem().getReferenceCode() : "SKU",
                    b.getInventoryItem().getName() != null ? b.getInventoryItem().getName() : "Mặt hàng",
                    b.getInventoryItem().getItemType() != null && b.getInventoryItem().getItemType().name().equals("INGREDIENT") ? "Nguyên liệu" : "Thành phẩm",
                    "Cái",
                    onHand,
                    soldQty,
                    BigDecimal.ZERO,
                    Math.round(turnoverRate * 100.0) / 100.0,
                    Math.round(dsi * 10.0) / 10.0,
                    abc
            ));
        }

        double avgTurnover = activeCount > 0 ? Math.round((totalTurnover / activeCount) * 100.0) / 100.0 : 0.0;
        double avgDsi = avgTurnover > 0 ? Math.round((365.0 / avgTurnover) * 10.0) / 10.0 : 0.0;

        return InventoryTurnoverReportResponse.builder()
                .periodDays(30)
                .averageTurnoverRate(avgTurnover)
                .averageDsi(avgDsi)
                .slowMovingSkuCount(slowMovingCount)
                .items(items.stream().limit(100).toList())
                .build();
    }

    @Override
    public ProductionWastageReportResponse getProductionWastageReport(LocalDate fromDate, LocalDate toDate) {
        List<InventoryDocument> prodDocs = documentRepository.findAll().stream()
                .filter(d -> !d.getIsDeleted())
                .filter(d -> d.getDocumentType() == InventoryDocumentType.PRODUCTION_ISSUE || d.getDocumentType() == InventoryDocumentType.PRODUCTION_RECEIPT)
                .toList();

        Map<String, ProductionWastageReportResponse.MaterialWastageDetail> materialMap = new LinkedHashMap<>();

        BigDecimal totalPlanned = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (InventoryDocument d : prodDocs) {
            for (InventoryDocumentLine l : d.getLines()) {
                if (l.getInventoryItem() == null) continue;
                String code = l.getInventoryItem().getReferenceCode() != null ? l.getInventoryItem().getReferenceCode() : "MAT-" + l.getInventoryItem().getId().toString().substring(0, 6);
                String name = l.getInventoryItem().getName() != null ? l.getInventoryItem().getName() : "Nguyên liệu";

                BigDecimal planned = l.getOrderedQuantity() != null ? l.getOrderedQuantity() : BigDecimal.ZERO;
                BigDecimal actual = l.getReceivedQuantity() != null ? l.getReceivedQuantity() : planned;

                totalPlanned = totalPlanned.add(planned);
                totalActual = totalActual.add(actual);

                ProductionWastageReportResponse.MaterialWastageDetail existing = materialMap.get(code);
                if (existing == null) {
                    BigDecimal wastage = actual.subtract(planned).max(BigDecimal.ZERO);
                    double rate = planned.compareTo(BigDecimal.ZERO) > 0 ? (wastage.doubleValue() / planned.doubleValue()) * 100.0 : 0.0;

                    materialMap.put(code, new ProductionWastageReportResponse.MaterialWastageDetail(
                            code,
                            name,
                            "Cái",
                            planned,
                            actual,
                            wastage,
                            Math.round(rate * 10.0) / 10.0,
                            BigDecimal.ZERO
                    ));
                } else {
                    BigDecimal newPlanned = existing.plannedQuantity().add(planned);
                    BigDecimal newActual = existing.actualQuantity().add(actual);
                    BigDecimal newWastage = newActual.subtract(newPlanned).max(BigDecimal.ZERO);
                    double rate = newPlanned.compareTo(BigDecimal.ZERO) > 0 ? (newWastage.doubleValue() / newPlanned.doubleValue()) * 100.0 : 0.0;

                    materialMap.put(code, new ProductionWastageReportResponse.MaterialWastageDetail(
                            code,
                            name,
                            existing.unitSymbol(),
                            newPlanned,
                            newActual,
                            newWastage,
                            Math.round(rate * 10.0) / 10.0,
                            BigDecimal.ZERO
                    ));
                }
            }
        }

        BigDecimal totalWastage = totalActual.subtract(totalPlanned).max(BigDecimal.ZERO);

        return ProductionWastageReportResponse.builder()
                .totalPlannedQuantity(totalPlanned)
                .totalActualQuantity(totalActual)
                .totalWastageQuantity(totalWastage)
                .totalWastageValue(BigDecimal.ZERO)
                .materials(new ArrayList<>(materialMap.values()))
                .build();
    }

    @Override
    public DisposalSummaryReportResponse getDisposalSummaryReport(LocalDate fromDate, LocalDate toDate, UUID locationId) {
        List<InventoryBatch> batches = batchRepository.findAll();
        long expiredCount = batches.stream().filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(LocalDate.now())).count();
        long damagedCount = batches.stream().filter(b -> b.getStatus() == InventoryBatchStatus.BLOCKED).count();

        List<InventoryDocument> allDocs = documentRepository.findAll().stream()
                .filter(d -> !d.getIsDeleted())
                .filter(d -> locationId == null || (d.getLocationId() != null && d.getLocationId().equals(locationId)))
                .toList();

        List<InventoryDocument> writeOffDocs = allDocs.stream()
                .filter(d -> d.getDocumentType() == InventoryDocumentType.WRITE_OFF)
                .toList();

        long customerReturnsCount = allDocs.stream()
                .filter(d -> d.getDocumentType() == InventoryDocumentType.CUSTOMER_RETURN_RECEIPT)
                .count();

        long supplierReturnsCount = allDocs.stream()
                .filter(d -> d.getDocumentType() == InventoryDocumentType.SUPPLIER_RETURN)
                .count();

        BigDecimal totalValue = BigDecimal.ZERO;
        List<DisposalSummaryReportResponse.DisposalItemDetail> details = new ArrayList<>();

        for (InventoryDocument d : writeOffDocs) {
            BigDecimal docQty = d.getLines().stream()
                    .map(l -> l.getOrderedQuantity() != null ? l.getOrderedQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            details.add(new DisposalSummaryReportResponse.DisposalItemDetail(
                    d.getDocumentCode(),
                    "Phiếu tiêu hủy kho",
                    "WRITE_OFF",
                    d.getDocumentCode(),
                    docQty,
                    "Cái",
                    BigDecimal.ZERO,
                    d.getNote() != null ? d.getNote() : "Hàng hết hạn / hỏng",
                    d.getCreatedAt()
            ));
        }

        return DisposalSummaryReportResponse.builder()
                .totalDisposalValue(totalValue)
                .expiredBatchesCount(expiredCount)
                .damagedBatchesCount(damagedCount)
                .customerReturnsCount(customerReturnsCount)
                .supplierReturnsCount(supplierReturnsCount)
                .writeOffDocumentsCount(writeOffDocs.size())
                .items(details)
                .build();
    }

    @Override
    public InTransitSummaryResponse getInTransitSummary() {
        List<InventoryDocument> inTransitDocs = documentRepository.findAll().stream()
                .filter(d -> !d.getIsDeleted())
                .filter(d -> (d.getDocumentType() == InventoryDocumentType.TRANSFER_ISSUE || d.getDocumentType() == InventoryDocumentType.TRANSFER_RECEIPT)
                        && (d.getStatus() == InventoryDocumentStatus.PENDING_APPROVAL || d.getStatus() == InventoryDocumentStatus.APPROVED || d.getStatus() == InventoryDocumentStatus.RECEIVING))
                .toList();

        BigDecimal totalQty = BigDecimal.ZERO;
        List<InTransitSummaryResponse.InTransitShipmentDetail> shipments = new ArrayList<>();

        for (InventoryDocument d : inTransitDocs) {
            BigDecimal qty = d.getLines().stream()
                    .map(l -> l.getOrderedQuantity() != null ? l.getOrderedQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalQty = totalQty.add(qty);

            shipments.add(new InTransitSummaryResponse.InTransitShipmentDetail(
                    d.getId(),
                    d.getDocumentCode(),
                    d.getSourceLocation() != null ? d.getSourceLocation().getName() : "Kho xuất",
                    d.getDestinationLocation() != null ? d.getDestinationLocation().getName() : "Kho đích",
                    qty,
                    BigDecimal.ZERO,
                    d.getCreatedAt()
            ));
        }

        return InTransitSummaryResponse.builder()
                .totalInTransitShipments(inTransitDocs.size())
                .totalInTransitQuantity(totalQty)
                .totalInTransitValue(BigDecimal.ZERO)
                .shipments(shipments)
                .build();
    }

    @Override
    public AgedInventoryReportResponse getAgedInventoryReport(Integer minAgeInDays, Integer page, Integer size) {
        int targetMinAge = (minAgeInDays != null && minAgeInDays >= 0) ? minAgeInDays : 0;
        int p = (page != null && page >= 0) ? page : 0;
        int s = (size != null && size > 0) ? size : 20;

        List<InventoryBalance> balances = balanceRepository.findAll();
        LocalDate today = LocalDate.now();

        List<AgedInventoryItemResponse> allAgedItems = new ArrayList<>();
        long over30Count = 0;
        long over60Count = 0;
        long over90Count = 0;
        BigDecimal grandTotalAgedCost = BigDecimal.ZERO;

        for (InventoryBalance balance : balances) {
            if (balance.getQuantityOnHand() == null || balance.getQuantityOnHand().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (balance.getInventoryItem() == null) continue;

            InventoryBatch batch = balance.getInventoryBatch();
            long ageInDays = 0;
            if (batch != null && batch.getReceivedAt() != null) {
                ageInDays = ChronoUnit.DAYS.between(
                        batch.getReceivedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                        today
                );
            } else if (balance.getCreatedAt() != null) {
                ageInDays = ChronoUnit.DAYS.between(
                        balance.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                        today
                );
            }

            if (ageInDays > 30) over30Count++;
            if (ageInDays > 60) over60Count++;
            if (ageInDays > 90) over90Count++;

            if (ageInDays < targetMinAge) continue;

            String bucket = ageInDays > 90 ? ">90" : (ageInDays > 60 ? "61-90" : (ageInDays > 30 ? "31-60" : "0-30"));
            BigDecimal unitCost = BigDecimal.ZERO; // Optional catalog cost integration
            BigDecimal itemTotalCost = balance.getQuantityOnHand().multiply(unitCost);
            grandTotalAgedCost = grandTotalAgedCost.add(itemTotalCost);

            allAgedItems.add(new AgedInventoryItemResponse(
                    batch != null ? batch.getId() : null,
                    batch != null ? batch.getBatchCode() : "N/A",
                    balance.getInventoryItem().getId(),
                    balance.getInventoryItem().getReferenceCode() != null ? balance.getInventoryItem().getReferenceCode() : "N/A",
                    balance.getInventoryItem().getName() != null ? balance.getInventoryItem().getName() : "Sản phẩm",
                    balance.getLocation() != null ? balance.getLocation().getId() : null,
                    balance.getLocation() != null ? balance.getLocation().getName() : "Kho",
                    balance.getQuantityOnHand(),
                    unitCost,
                    itemTotalCost,
                    batch != null ? batch.getReceivedAt() : balance.getCreatedAt(),
                    batch != null ? batch.getExpiryDate() : null,
                    ageInDays,
                    bucket
            ));
        }

        allAgedItems.sort((a, b) -> Long.compare(b.ageInDays(), a.ageInDays()));

        int fromIndex = Math.min(p * s, allAgedItems.size());
        int toIndex = Math.min(fromIndex + s, allAgedItems.size());
        List<AgedInventoryItemResponse> pagedItems = allAgedItems.subList(fromIndex, toIndex);

        return new AgedInventoryReportResponse(
                pagedItems,
                allAgedItems.size(),
                grandTotalAgedCost,
                over30Count,
                over60Count,
                over90Count
        );
    }

    @Override
    public ExpiringBatchesReportResponse getExpiringBatchesReport(Integer daysToExpiry, Integer page, Integer size) {
        int targetDays = (daysToExpiry != null) ? daysToExpiry : 30;
        int p = (page != null && page >= 0) ? page : 0;
        int s = (size != null && size > 0) ? size : 20;

        List<InventoryBatch> batches = batchRepository.findAll();
        LocalDate today = LocalDate.now();

        List<ExpiringBatchItemResponse> expiringList = new ArrayList<>();
        long expiredCount = 0;
        long within15Count = 0;
        long within30Count = 0;

        for (InventoryBatch batch : batches) {
            if (batch.getExpiryDate() == null) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            if (daysLeft < 0) {
                expiredCount++;
            } else if (daysLeft <= 15) {
                within15Count++;
            } else if (daysLeft <= 30) {
                within30Count++;
            }

            if (daysLeft <= targetDays) {
                expiringList.add(new ExpiringBatchItemResponse(
                        batch.getId(),
                        batch.getBatchCode(),
                        batch.getInventoryItem() != null ? batch.getInventoryItem().getId() : null,
                        batch.getInventoryItem() != null && batch.getInventoryItem().getReferenceCode() != null ? batch.getInventoryItem().getReferenceCode() : "N/A",
                        batch.getInventoryItem() != null && batch.getInventoryItem().getName() != null ? batch.getInventoryItem().getName() : "Sản phẩm",
                        null,
                        "Kho",
                        BigDecimal.ZERO,
                        batch.getExpiryDate(),
                        daysLeft,
                        batch.getStatus() != null ? batch.getStatus().name() : "ACTIVE"
                ));
            }
        }

        expiringList.sort(Comparator.comparingLong(ExpiringBatchItemResponse::daysToExpiry));

        int fromIndex = Math.min(p * s, expiringList.size());
        int toIndex = Math.min(fromIndex + s, expiringList.size());
        List<ExpiringBatchItemResponse> pagedList = expiringList.subList(fromIndex, toIndex);

        return new ExpiringBatchesReportResponse(
                pagedList,
                expiringList.size(),
                expiredCount,
                within15Count,
                within30Count
        );
    }
}

