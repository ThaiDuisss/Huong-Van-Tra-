package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.CatalogClient.BomItemResponse;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.BusinessReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.CustomerPerformanceItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.CustomerPurchaseHistoryItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.CustomerReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.IngredientAnalysisReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.IngredientFrequencyItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.IngredientPairItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.OrderFrequencyBucket;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductConsumptionTrendPoint;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductFormulaIngredientItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductFormulaItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductFormulaReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductPerformanceItem;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.ProductReport;
import org.hvtra.orderservice.dto.response.ManagementReportResponse.RevenueTrendPoint;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.hvtra.orderservice.repository.ManagementReportQueryRepository;
import org.hvtra.orderservice.repository.ManagementReportQueryRepository.OrderItemTimelineRow;
import org.hvtra.orderservice.repository.ManagementReportQueryRepository.OrderSummaryRow;
import org.hvtra.orderservice.repository.ManagementReportQueryRepository.VariantSalesAggregateRow;
import org.hvtra.orderservice.service.ManagementReportService;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementReportServiceImpl implements ManagementReportService {

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_LIMIT = 100;

    private final ManagementReportQueryRepository queryRepository;
    private final OrderDateTimeHelper orderDateTimeHelper;
    private final CatalogClient catalogClient;

    @Override
    public BusinessReport getBusinessReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId
    ) {
        DateWindow window = resolveDateWindow(fromDate, toDate);
        List<UUID> variantIds = resolveVariantFilter(productId, categoryId);
        if (variantIds != null && variantIds.isEmpty()) {
            return emptyBusinessReport(window);
        }

        List<OrderSummaryRow> currentOrders = queryRepository.findOrderSummaries(
                window.currentStart(), window.currentEnd(), customerId, variantIds);
        List<OrderSummaryRow> previousOrders = queryRepository.findOrderSummaries(
                window.previousStart(), window.previousEnd(), customerId, variantIds);

        List<OrderItemTimelineRow> currentItems = queryRepository.findOrderItemTimeline(
                window.currentStart(), window.currentEnd(), customerId, variantIds);
        List<OrderItemTimelineRow> previousItems = queryRepository.findOrderItemTimeline(
                window.previousStart(), window.previousEnd(), customerId, variantIds);

        boolean itemFiltered = variantIds != null;
        long totalOrders = currentOrders.size();
        long previousTotalOrders = previousOrders.size();

        BigDecimal totalRevenue = itemFiltered
                ? sumItemRevenue(currentItems)
                : sumOrderRevenue(currentOrders);
        BigDecimal previousTotalRevenue = itemFiltered
                ? sumItemRevenue(previousItems)
                : sumOrderRevenue(previousOrders);

        BigDecimal totalQuantitySold = sumItemQuantity(currentItems);
        BigDecimal previousTotalQuantitySold = sumItemQuantity(previousItems);

        Map<LocalDate, DailyBucket> currentBuckets = bucketByDate(currentOrders, currentItems, itemFiltered);
        Map<LocalDate, DailyBucket> previousBuckets = bucketByDate(previousOrders, previousItems, itemFiltered);

        List<RevenueTrendPoint> trend = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(window.fromDate(), window.toDate()) + 1;
        for (long i = 0; i < days; i++) {
            LocalDate date = window.fromDate().plusDays(i);
            LocalDate prevDate = window.previousFromDate().plusDays(i);
            DailyBucket curr = currentBuckets.getOrDefault(date, DailyBucket.EMPTY);
            DailyBucket prev = previousBuckets.getOrDefault(prevDate, DailyBucket.EMPTY);
            trend.add(new RevenueTrendPoint(
                    date,
                    date.format(LABEL_FORMATTER),
                    curr.revenue,
                    prev.revenue,
                    curr.orderCount,
                    curr.quantity
            ));
        }

        return new BusinessReport(
                window.fromDate(),
                window.toDate(),
                window.previousFromDate(),
                window.previousToDate(),
                totalOrders,
                totalRevenue,
                totalQuantitySold,
                average(totalRevenue, totalOrders),
                previousTotalOrders,
                previousTotalRevenue,
                previousTotalQuantitySold,
                growthPercent(totalRevenue, previousTotalRevenue),
                growthPercent(BigDecimal.valueOf(totalOrders), BigDecimal.valueOf(previousTotalOrders)),
                growthPercent(totalQuantitySold, previousTotalQuantitySold),
                trend
        );
    }

    @Override
    public ProductReport getProductReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId,
            int limit
    ) {
        int safeLimit = normalizeLimit(limit);
        DateWindow window = resolveDateWindow(fromDate, toDate);
        List<UUID> variantIds = resolveVariantFilter(productId, categoryId);
        if (variantIds != null && variantIds.isEmpty()) {
            return new ProductReport(
                    window.fromDate(), window.toDate(), window.previousFromDate(), window.previousToDate(),
                    List.of(), List.of(), List.of(), List.of(), List.of()
            );
        }

        List<OrderItemTimelineRow> currentItems = queryRepository.findOrderItemTimeline(
                window.currentStart(), window.currentEnd(), customerId, variantIds);
        List<OrderItemTimelineRow> previousItems = queryRepository.findOrderItemTimeline(
                window.previousStart(), window.previousEnd(), customerId, variantIds);

        Map<UUID, ProductAccumulator> currentMap = aggregateProducts(currentItems);
        Map<UUID, ProductAccumulator> previousMap = aggregateProducts(previousItems);

        Set<UUID> allVariants = new HashSet<>();
        allVariants.addAll(currentMap.keySet());
        allVariants.addAll(previousMap.keySet());

        List<ProductPerformanceItem> allPerformance = new ArrayList<>();
        for (UUID variantId : allVariants) {
            ProductAccumulator curr = currentMap.get(variantId);
            ProductAccumulator prev = previousMap.get(variantId);
            ProductAccumulator meta = curr != null ? curr : prev;
            BigDecimal currQty = curr != null ? curr.quantity : BigDecimal.ZERO;
            BigDecimal currRev = curr != null ? curr.revenue : BigDecimal.ZERO;
            long currOrders = curr != null ? curr.orderIds.size() : 0L;
            BigDecimal prevQty = prev != null ? prev.quantity : BigDecimal.ZERO;
            BigDecimal prevRev = prev != null ? prev.revenue : BigDecimal.ZERO;

            allPerformance.add(new ProductPerformanceItem(
                    0,
                    variantId,
                    meta.sku,
                    meta.productName,
                    meta.variantName,
                    currQty,
                    currRev,
                    currOrders,
                    prevQty,
                    prevRev,
                    growthPercent(currRev, prevRev)
            ));
        }

        List<ProductPerformanceItem> topSellingByQuantity = rankProducts(
                allPerformance.stream()
                        .filter(p -> p.quantitySold().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(ProductPerformanceItem::quantitySold).reversed()
                                .thenComparing(ProductPerformanceItem::revenue, Comparator.reverseOrder()))
                        .limit(safeLimit)
                        .toList()
        );

        List<ProductPerformanceItem> topByRevenue = rankProducts(
                allPerformance.stream()
                        .filter(p -> p.revenue().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(ProductPerformanceItem::revenue).reversed()
                                .thenComparing(ProductPerformanceItem::quantitySold, Comparator.reverseOrder()))
                        .limit(safeLimit)
                        .toList()
        );

        List<ProductPerformanceItem> fastGrowing = rankProducts(
                allPerformance.stream()
                        .filter(p -> p.growthPercent() != null && p.growthPercent().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(ProductPerformanceItem::growthPercent).reversed()
                                .thenComparing(ProductPerformanceItem::revenue, Comparator.reverseOrder()))
                        .limit(safeLimit)
                        .toList()
        );

        List<ProductPerformanceItem> declining = rankProducts(
                allPerformance.stream()
                        .filter(p -> p.growthPercent() != null && p.growthPercent().compareTo(BigDecimal.ZERO) < 0)
                        .sorted(Comparator.comparing(ProductPerformanceItem::growthPercent)
                                .thenComparing(ProductPerformanceItem::previousRevenue, Comparator.reverseOrder()))
                        .limit(safeLimit)
                        .toList()
        );

        Map<LocalDate, DailyBucket> dailyConsumption = new HashMap<>();
        for (OrderItemTimelineRow row : currentItems) {
            if (row.createdAt() == null) continue;
            LocalDate date = orderDateTimeHelper.toLocalDateTime(row.createdAt()).toLocalDate();
            dailyConsumption.computeIfAbsent(date, d -> new DailyBucket())
                    .addItem(row.quantity(), row.lineTotal());
        }

        List<ProductConsumptionTrendPoint> trend = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(window.fromDate(), window.toDate()) + 1;
        for (long i = 0; i < days; i++) {
            LocalDate date = window.fromDate().plusDays(i);
            DailyBucket bucket = dailyConsumption.getOrDefault(date, DailyBucket.EMPTY);
            trend.add(new ProductConsumptionTrendPoint(
                    date,
                    date.format(LABEL_FORMATTER),
                    bucket.quantity,
                    bucket.revenue
            ));
        }

        return new ProductReport(
                window.fromDate(),
                window.toDate(),
                window.previousFromDate(),
                window.previousToDate(),
                topSellingByQuantity,
                topByRevenue,
                fastGrowing,
                declining,
                trend
        );
    }

    @Override
    public CustomerReport getCustomerReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID productId,
            UUID categoryId,
            UUID customerId,
            int limit
    ) {
        int safeLimit = normalizeLimit(limit);
        DateWindow window = resolveDateWindow(fromDate, toDate);
        List<UUID> variantIds = resolveVariantFilter(productId, categoryId);
        if (variantIds != null && variantIds.isEmpty()) {
            return new CustomerReport(
                    window.fromDate(), window.toDate(), 0, 0, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), List.of(), List.of(), List.of()
            );
        }

        List<ManagementReportQueryRepository.CustomerOrderRow> rows = queryRepository.findCustomerOrders(
                window.currentStart(), window.currentEnd(), customerId, variantIds);

        Map<UUID, CustomerAccumulator> customerMap = new LinkedHashMap<>();
        for (ManagementReportQueryRepository.CustomerOrderRow row : rows) {
            if (row.customerId() == null) {
                continue;
            }
            CustomerAccumulator acc = customerMap.computeIfAbsent(
                    row.customerId(),
                    id -> new CustomerAccumulator(id, row.customerName(), row.customerPhone())
            );
            acc.addOrder(row.createdAt(), row.finalAmount(), row.itemQuantity());
        }

        List<CustomerPerformanceItem> allCustomers = customerMap.values().stream()
                .map(acc -> new CustomerPerformanceItem(
                        0,
                        acc.customerId,
                        acc.customerName != null && !acc.customerName.isBlank() ? acc.customerName : "Khách lẻ",
                        acc.customerPhone,
                        acc.orderCount,
                        acc.totalQuantity,
                        acc.totalRevenue,
                        average(acc.totalRevenue, acc.orderCount),
                        acc.lastOrderAt
                ))
                .toList();

        List<CustomerPerformanceItem> topByOrderCount = rankCustomers(
                allCustomers.stream()
                        .sorted(Comparator.comparingLong(CustomerPerformanceItem::orderCount).reversed()
                                .thenComparing(CustomerPerformanceItem::totalRevenue, Comparator.reverseOrder()))
                        .limit(safeLimit)
                        .toList()
        );

        List<CustomerPerformanceItem> topByRevenue = rankCustomers(
                allCustomers.stream()
                        .sorted(Comparator.comparing(CustomerPerformanceItem::totalRevenue).reversed()
                                .thenComparingLong(CustomerPerformanceItem::orderCount).reversed())
                        .limit(safeLimit)
                        .toList()
        );

        long totalCustomers = allCustomers.size();
        long customerOrdersCount = allCustomers.stream().mapToLong(CustomerPerformanceItem::orderCount).sum();
        BigDecimal totalCustomerRevenue = allCustomers.stream()
                .map(CustomerPerformanceItem::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long returningCustomers = allCustomers.stream().filter(c -> c.orderCount() >= 2).count();
        BigDecimal returningRate = totalCustomers == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(returningCustomers * 100.0 / totalCustomers).setScale(1, RoundingMode.HALF_UP);

        BigDecimal avgDaysBetween = computeAverageDaysBetweenOrders(customerMap.values());
        List<OrderFrequencyBucket> frequencyBuckets = buildFrequencyBuckets(allCustomers, totalCustomers);

        List<CustomerPurchaseHistoryItem> history = rows.stream()
                .limit(50)
                .map(row -> new CustomerPurchaseHistoryItem(
                        row.orderId(),
                        row.orderCode(),
                        row.customerId(),
                        row.customerName() != null && !row.customerName().isBlank() ? row.customerName() : "Khách lẻ",
                        row.customerPhone(),
                        row.createdAt(),
                        defaultZero(row.finalAmount()),
                        defaultZero(row.itemQuantity()),
                        row.status(),
                        row.paymentStatus()
                ))
                .toList();

        return new CustomerReport(
                window.fromDate(),
                window.toDate(),
                totalCustomers,
                customerOrdersCount,
                totalCustomerRevenue,
                average(BigDecimal.valueOf(customerOrdersCount), totalCustomers),
                avgDaysBetween,
                returningRate,
                topByOrderCount,
                topByRevenue,
                frequencyBuckets,
                history
        );
    }

    @Override
    public ProductFormulaReport getProductFormulaReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            UUID productId,
            int page,
            int size
    ) {
        DateWindow window = resolveDateWindow(fromDate, toDate);
        List<ProductFormulaItem> records = buildSortedProductFormulas(window, categoryId, productId);
        List<ProductFormulaItem> topProducts = records.stream().limit(10).toList();
        return new ProductFormulaReport(
                window.fromDate(),
                window.toDate(),
                topProducts,
                paginateList(records, page, size)
        );
    }

    @Override
    public IngredientAnalysisReport getIngredientAnalysisReport(
            LocalDate fromDate,
            LocalDate toDate,
            UUID categoryId,
            UUID productId,
            int page,
            int size
    ) {
        DateWindow window = resolveDateWindow(fromDate, toDate);
        List<ProductFormulaItem> records = buildSortedProductFormulas(window, categoryId, productId);
        List<IngredientFrequencyItem> frequencies = computeIngredientFrequencies(records);
        List<IngredientPairItem> pairs = computeIngredientPairs(records);
        return new IngredientAnalysisReport(
                window.fromDate(),
                window.toDate(),
                frequencies.stream().limit(10).toList(),
                pairs.stream().limit(10).toList(),
                paginateList(frequencies, page, size)
        );
    }

    private List<ProductFormulaItem> buildSortedProductFormulas(
            DateWindow window,
            UUID categoryId,
            UUID productId
    ) {
        List<CatalogVariantResponse> variants = fetchVariantsForFormulaReport(categoryId, productId);
        if (variants.isEmpty()) {
            return List.of();
        }

        List<UUID> variantIds = variants.stream()
                .map(CatalogVariantResponse::id)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, List<BomItemResponse>> bomByVariant = fetchBomItemsBatch(variantIds);
        List<CatalogVariantResponse> variantsWithBom = variants.stream()
                .filter(v -> v.id() != null && !bomByVariant.getOrDefault(v.id(), List.of()).isEmpty())
                .toList();
        if (variantsWithBom.isEmpty()) {
            return List.of();
        }

        List<UUID> bomVariantIds = variantsWithBom.stream().map(CatalogVariantResponse::id).toList();
        Map<UUID, VariantSalesAggregateRow> salesByVariant = new HashMap<>();
        for (VariantSalesAggregateRow row : queryRepository.findVariantSalesAggregates(
                window.currentStart(), window.currentEnd(), bomVariantIds)) {
            salesByVariant.put(row.productVariantId(), row);
        }

        return variantsWithBom.stream()
                .map(variant -> buildProductFormulaItem(
                        variant,
                        bomByVariant.getOrDefault(variant.id(), List.of()),
                        salesByVariant.get(variant.id())
                ))
                .sorted(Comparator.comparing(ProductFormulaItem::quantitySold).reversed()
                        .thenComparing(ProductFormulaItem::productName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private <T> PageResponse<T> paginateList(List<T> items, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        long totalElements = items.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        List<T> content = items.subList(fromIndex, toIndex);
        boolean first = safePage == 0;
        boolean last = safePage >= totalPages - 1;
        return new PageResponse<>(content, safePage, safeSize, totalElements, totalPages, first, last);
    }

    private List<CatalogVariantResponse> fetchVariantsForFormulaReport(
            UUID categoryId,
            UUID productId
    ) {
        try {
            PageResponse<CatalogVariantResponse> page = catalogClient.findAll(
                    null, null, null, null, productId, categoryId, null, 0, 200);
            return page != null && page.content() != null ? page.content() : List.of();
        } catch (Exception ex) {
            log.warn("Không thể tải danh sách biến thể cho báo cáo công thức: {}", ex.getMessage());
            return List.of();
        }
    }

    private Map<UUID, List<BomItemResponse>> fetchBomItemsBatch(List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, List<BomItemResponse>> result = catalogClient.getBomItemsByVariantIds(variantIds);
            return result != null ? result : Map.of();
        } catch (Exception ex) {
            log.warn("Không thể tải batch BOM từ Catalog-Service: {}", ex.getMessage());
            return Map.of();
        }
    }

    private ProductFormulaItem buildProductFormulaItem(
            CatalogVariantResponse variant,
            List<BomItemResponse> rawItems,
            VariantSalesAggregateRow sales
    ) {
        BigDecimal totalQty = rawItems.stream()
                .map(item -> defaultZero(item.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProductFormulaIngredientItem> ingredients = rawItems.stream()
                .map(item -> {
                    BigDecimal qty = defaultZero(item.quantity());
                    BigDecimal ratio = totalQty.compareTo(BigDecimal.ZERO) > 0
                            ? qty.multiply(BigDecimal.valueOf(100)).divide(totalQty, 1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    UUID ingId = item.ingredient() != null && item.ingredient().id() != null
                            ? item.ingredient().id()
                            : item.id();
                    String ingCode = item.ingredient() != null && item.ingredient().ingredientCode() != null
                            ? item.ingredient().ingredientCode()
                            : "";
                    String ingName = item.ingredient() != null && item.ingredient().name() != null
                            ? item.ingredient().name()
                            : "Nguyên liệu";
                    String unitSymbol = item.unit() != null
                            ? (item.unit().symbol() != null ? item.unit().symbol()
                            : (item.unit().name() != null ? item.unit().name() : "g"))
                            : "g";
                    return new ProductFormulaIngredientItem(
                            ingId,
                            ingCode,
                            ingName,
                            qty,
                            unitSymbol,
                            defaultZero(item.wasteRate()),
                            ratio
                    );
                })
                .toList();

        return new ProductFormulaItem(
                variant.id(),
                variant.productId(),
                variant.sku(),
                variant.productName(),
                variant.variantName(),
                variant.categoryName() != null ? variant.categoryName() : "Chưa phân nhóm",
                "BOM " + variant.sku() + " (" + variant.variantName() + ")",
                sales != null ? defaultZero(sales.quantitySold()) : BigDecimal.ZERO,
                sales != null ? sales.orderCount() : 0L,
                ingredients
        );
    }

    private List<IngredientFrequencyItem> computeIngredientFrequencies(List<ProductFormulaItem> records) {
        Map<UUID, IngredientFrequencyAccumulator> map = new LinkedHashMap<>();
        for (ProductFormulaItem record : records) {
            for (ProductFormulaIngredientItem ing : record.ingredients()) {
                IngredientFrequencyAccumulator acc = map.computeIfAbsent(
                        ing.ingredientId(),
                        id -> new IngredientFrequencyAccumulator(id, ing.ingredientCode(), ing.ingredientName(), ing.unitSymbol())
                );
                acc.formulaCount += 1;
                acc.weightedSalesVolume = acc.weightedSalesVolume.add(defaultZero(record.quantitySold()));
                acc.ratioSum = acc.ratioSum.add(defaultZero(ing.ratioPercent()));
            }
        }
        return map.values().stream()
                .map(acc -> new IngredientFrequencyItem(
                        acc.ingredientId,
                        acc.ingredientCode,
                        acc.ingredientName,
                        acc.unitSymbol,
                        acc.formulaCount,
                        acc.weightedSalesVolume,
                        acc.formulaCount > 0
                                ? acc.ratioSum.divide(BigDecimal.valueOf(acc.formulaCount), 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                ))
                .sorted(Comparator.comparing(IngredientFrequencyItem::weightedSalesVolume).reversed()
                        .thenComparing(Comparator.comparingInt(IngredientFrequencyItem::formulaCount).reversed()))
                .toList();
    }

    private List<IngredientPairItem> computeIngredientPairs(List<ProductFormulaItem> records) {
        Map<String, IngredientPairAccumulator> pairMap = new LinkedHashMap<>();
        for (ProductFormulaItem record : records) {
            Map<UUID, ProductFormulaIngredientItem> uniqueMap = new LinkedHashMap<>();
            for (ProductFormulaIngredientItem ing : record.ingredients()) {
                uniqueMap.putIfAbsent(ing.ingredientId(), ing);
            }
            List<ProductFormulaIngredientItem> uniqueIngs = uniqueMap.values().stream()
                    .sorted(Comparator.comparing(ProductFormulaIngredientItem::ingredientName, String::compareToIgnoreCase))
                    .toList();

            for (int i = 0; i < uniqueIngs.size(); i++) {
                for (int j = i + 1; j < uniqueIngs.size(); j++) {
                    ProductFormulaIngredientItem a = uniqueIngs.get(i);
                    ProductFormulaIngredientItem b = uniqueIngs.get(j);
                    String pairKey = a.ingredientId() + "__" + b.ingredientId();
                    IngredientPairAccumulator acc = pairMap.computeIfAbsent(
                            pairKey,
                            k -> new IngredientPairAccumulator(
                                    k,
                                    a.ingredientName() + " (" + a.ingredientCode() + ")",
                                    b.ingredientName() + " (" + b.ingredientCode() + ")"
                            )
                    );
                    acc.coOccurrenceCount += 1;
                    acc.combinedSalesVolume = acc.combinedSalesVolume.add(defaultZero(record.quantitySold()));
                }
            }
        }
        return pairMap.values().stream()
                .map(acc -> new IngredientPairItem(
                        acc.pairKey,
                        acc.ingredientA,
                        acc.ingredientB,
                        acc.coOccurrenceCount,
                        acc.combinedSalesVolume
                ))
                .sorted(Comparator.comparing(IngredientPairItem::combinedSalesVolume).reversed()
                        .thenComparing(Comparator.comparingInt(IngredientPairItem::coOccurrenceCount).reversed()))
                .limit(12)
                .toList();
    }

    private List<UUID> resolveVariantFilter(UUID productId, UUID categoryId) {
        if (productId == null && categoryId == null) {
            return null;
        }
        try {
            PageResponse<CatalogVariantResponse> response = catalogClient.findAll(
                    null, null, null, null, productId, categoryId, null, 0, 500);
            if (response == null || response.content() == null) {
                return List.of();
            }
            return response.content().stream()
                    .map(CatalogVariantResponse::id)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            log.warn("Không thể tra cứu danh sách biến thể từ Catalog-Service: {}", ex.getMessage());
            return List.of();
        }
    }

    private DateWindow resolveDateWindow(LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveTo = toDate != null ? toDate : orderDateTimeHelper.today();
        LocalDate effectiveFrom = fromDate != null ? fromDate : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Từ ngày không được lớn hơn đến ngày");
        }
        long spanDays = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
        LocalDate prevTo = effectiveFrom.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(spanDays - 1);

        return new DateWindow(
                effectiveFrom,
                effectiveTo,
                prevFrom,
                prevTo,
                orderDateTimeHelper.startOfDay(effectiveFrom),
                orderDateTimeHelper.startOfDay(effectiveTo.plusDays(1)),
                orderDateTimeHelper.startOfDay(prevFrom),
                orderDateTimeHelper.startOfDay(prevTo.plusDays(1))
        );
    }

    private BusinessReport emptyBusinessReport(DateWindow window) {
        return new BusinessReport(
                window.fromDate(), window.toDate(), window.previousFromDate(), window.previousToDate(),
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of()
        );
    }

    private Map<LocalDate, DailyBucket> bucketByDate(
            List<OrderSummaryRow> orders,
            List<OrderItemTimelineRow> items,
            boolean itemFiltered
    ) {
        Map<LocalDate, DailyBucket> map = new HashMap<>();
        for (OrderSummaryRow order : orders) {
            if (order.createdAt() == null) continue;
            LocalDate date = orderDateTimeHelper.toLocalDateTime(order.createdAt()).toLocalDate();
            DailyBucket bucket = map.computeIfAbsent(date, d -> new DailyBucket());
            bucket.orderCount++;
            if (!itemFiltered) {
                bucket.revenue = bucket.revenue.add(defaultZero(order.finalAmount()));
            }
        }
        for (OrderItemTimelineRow item : items) {
            if (item.createdAt() == null) continue;
            LocalDate date = orderDateTimeHelper.toLocalDateTime(item.createdAt()).toLocalDate();
            DailyBucket bucket = map.computeIfAbsent(date, d -> new DailyBucket());
            bucket.quantity = bucket.quantity.add(defaultZero(item.quantity()));
            if (itemFiltered) {
                bucket.revenue = bucket.revenue.add(defaultZero(item.lineTotal()));
            }
        }
        return map;
    }

    private Map<UUID, ProductAccumulator> aggregateProducts(List<OrderItemTimelineRow> items) {
        Map<UUID, ProductAccumulator> map = new HashMap<>();
        for (OrderItemTimelineRow item : items) {
            if (item.productVariantId() == null) continue;
            ProductAccumulator acc = map.computeIfAbsent(
                    item.productVariantId(),
                    id -> new ProductAccumulator(item.sku(), item.productName(), item.variantName())
            );
            acc.quantity = acc.quantity.add(defaultZero(item.quantity()));
            acc.revenue = acc.revenue.add(defaultZero(item.lineTotal()));
            if (item.orderId() != null) {
                acc.orderIds.add(item.orderId());
            }
        }
        return map;
    }

    private List<ProductPerformanceItem> rankProducts(List<ProductPerformanceItem> items) {
        List<ProductPerformanceItem> ranked = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ProductPerformanceItem item = items.get(i);
            ranked.add(new ProductPerformanceItem(
                    i + 1,
                    item.productVariantId(),
                    item.sku(),
                    item.productName(),
                    item.variantName(),
                    item.quantitySold(),
                    item.revenue(),
                    item.orderCount(),
                    item.previousQuantitySold(),
                    item.previousRevenue(),
                    item.growthPercent()
            ));
        }
        return ranked;
    }

    private List<CustomerPerformanceItem> rankCustomers(List<CustomerPerformanceItem> items) {
        List<CustomerPerformanceItem> ranked = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CustomerPerformanceItem item = items.get(i);
            ranked.add(new CustomerPerformanceItem(
                    i + 1,
                    item.customerId(),
                    item.customerName(),
                    item.customerPhone(),
                    item.orderCount(),
                    item.totalQuantity(),
                    item.totalRevenue(),
                    item.averageOrderValue(),
                    item.lastOrderAt()
            ));
        }
        return ranked;
    }

    private BigDecimal computeAverageDaysBetweenOrders(Iterable<CustomerAccumulator> customers) {
        List<Double> gaps = new ArrayList<>();
        for (CustomerAccumulator c : customers) {
            if (c.orderTimestamps.size() < 2) continue;
            c.orderTimestamps.sort(Comparator.naturalOrder());
            for (int i = 1; i < c.orderTimestamps.size(); i++) {
                long hours = ChronoUnit.HOURS.between(c.orderTimestamps.get(i - 1), c.orderTimestamps.get(i));
                gaps.add(hours / 24.0);
            }
        }
        if (gaps.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double avg = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
    }

    private List<OrderFrequencyBucket> buildFrequencyBuckets(
            List<CustomerPerformanceItem> customers,
            long totalCustomers
    ) {
        long oneOrder = customers.stream().filter(c -> c.orderCount() == 1).count();
        long twoToThree = customers.stream().filter(c -> c.orderCount() >= 2 && c.orderCount() <= 3).count();
        long fourToFive = customers.stream().filter(c -> c.orderCount() >= 4 && c.orderCount() <= 5).count();
        long sixPlus = customers.stream().filter(c -> c.orderCount() >= 6).count();

        return List.of(
                bucket("1 đơn", oneOrder, totalCustomers),
                bucket("2 - 3 đơn", twoToThree, totalCustomers),
                bucket("4 - 5 đơn", fourToFive, totalCustomers),
                bucket("Từ 6 đơn trở lên", sixPlus, totalCustomers)
        );
    }

    private OrderFrequencyBucket bucket(String label, long count, long total) {
        BigDecimal pct = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(count * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        return new OrderFrequencyBucket(label, count, pct);
    }

    private BigDecimal sumOrderRevenue(List<OrderSummaryRow> orders) {
        return orders.stream()
                .map(row -> defaultZero(row.finalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumItemRevenue(List<OrderItemTimelineRow> items) {
        return items.stream()
                .map(row -> defaultZero(row.lineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumItemQuantity(List<OrderItemTimelineRow> items) {
        return items.stream()
                .map(row -> defaultZero(row.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal growthPercent(BigDecimal current, BigDecimal previous) {
        BigDecimal curr = defaultZero(current);
        BigDecimal prev = defaultZero(previous);
        if (prev.compareTo(BigDecimal.ZERO) == 0) {
            return curr.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return curr.subtract(prev)
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal total, long count) {
        if (count <= 0) return BigDecimal.ZERO;
        return defaultZero(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private record DateWindow(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate previousFromDate,
            LocalDate previousToDate,
            Instant currentStart,
            Instant currentEnd,
            Instant previousStart,
            Instant previousEnd
    ) {
    }

    private static final class DailyBucket {
        static final DailyBucket EMPTY = new DailyBucket();
        long orderCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;

        void addItem(BigDecimal qty, BigDecimal rev) {
            if (qty != null) quantity = quantity.add(qty);
            if (rev != null) revenue = revenue.add(rev);
        }
    }

    private static final class ProductAccumulator {
        final String sku;
        final String productName;
        final String variantName;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        final Set<UUID> orderIds = new HashSet<>();

        ProductAccumulator(String sku, String productName, String variantName) {
            this.sku = sku;
            this.productName = productName;
            this.variantName = variantName;
        }
    }

    private static final class CustomerAccumulator {
        final UUID customerId;
        final String customerName;
        final String customerPhone;
        long orderCount = 0;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        Instant lastOrderAt;
        final List<Instant> orderTimestamps = new ArrayList<>();

        CustomerAccumulator(UUID customerId, String customerName, String customerPhone) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.customerPhone = customerPhone;
        }

        void addOrder(Instant createdAt, BigDecimal amount, BigDecimal qty) {
            orderCount++;
            if (amount != null) totalRevenue = totalRevenue.add(amount);
            if (qty != null) totalQuantity = totalQuantity.add(qty);
            if (createdAt != null) {
                orderTimestamps.add(createdAt);
                if (lastOrderAt == null || createdAt.isAfter(lastOrderAt)) {
                    lastOrderAt = createdAt;
                }
            }
        }
    }

    private static final class IngredientFrequencyAccumulator {
        final UUID ingredientId;
        final String ingredientCode;
        final String ingredientName;
        final String unitSymbol;
        int formulaCount = 0;
        BigDecimal weightedSalesVolume = BigDecimal.ZERO;
        BigDecimal ratioSum = BigDecimal.ZERO;

        IngredientFrequencyAccumulator(UUID ingredientId, String ingredientCode, String ingredientName, String unitSymbol) {
            this.ingredientId = ingredientId;
            this.ingredientCode = ingredientCode;
            this.ingredientName = ingredientName;
            this.unitSymbol = unitSymbol;
        }
    }

    private static final class IngredientPairAccumulator {
        final String pairKey;
        final String ingredientA;
        final String ingredientB;
        int coOccurrenceCount = 0;
        BigDecimal combinedSalesVolume = BigDecimal.ZERO;

        IngredientPairAccumulator(String pairKey, String ingredientA, String ingredientB) {
            this.pairKey = pairKey;
            this.ingredientA = ingredientA;
            this.ingredientB = ingredientB;
        }
    }
}
