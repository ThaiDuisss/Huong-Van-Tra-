package org.hvtra.iventoryservice.availability.service;

import org.hvtra.iventoryservice.availability.AvailabilityProperties;
import org.hvtra.iventoryservice.availability.AvailabilityScenario;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateRequest;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.LineEstimate;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryReservation;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;
import org.hvtra.iventoryservice.domain.support.InventoryLocationSaleability;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.reservation.repository.InventoryReservationRepository;
import org.hvtra.iventoryservice.stock.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.stock.service.InventoryStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ước tính ngày có hàng cho phần khách đặt vượt quá tồn (kệ + kho), theo thứ tự:
 * <ol>
 *   <li>Mặt hàng đang được sản xuất: lấy thời điểm dự kiến xong của lệnh đang chạy.</li>
 *   <li>Đủ nguyên liệu: hôm nay + thời gian sản xuất.</li>
 *   <li>Thiếu nguyên liệu, đã có phiếu nhập: ngày dự kiến về + thời gian sản xuất.</li>
 *   <li>Thiếu nguyên liệu, chưa có phiếu nhập: thời gian nhập thường gặp (lịch sử phiếu nhập) + thời gian sản xuất.</li>
 * </ol>
 * Thời gian sản xuất và thời gian nhập là số trung bình lưu sẵn trên mặt hàng (xem {@link SupplyLeadTimeService});
 * chưa có lịch sử sản xuất thì cộng 1 ngày.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityEstimateService {

    // Lệnh APPROVED đã nhập thành phẩm vào tồn nên không còn là hàng "đang sản xuất".
    static final List<ProductionOrderStatus> OPEN_PRODUCTION_STATUSES = List.of(
            ProductionOrderStatus.PENDING_APPROVAL);
    static final List<InventoryDocumentStatus> OPEN_RECEIPT_STATUSES = List.of(
            InventoryDocumentStatus.PENDING_APPROVAL, InventoryDocumentStatus.APPROVED,
            InventoryDocumentStatus.RECEIVING, InventoryDocumentStatus.PENDING_EXCEPTION_APPROVAL);

    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InventoryStockService stockService;
    private final CatalogClient catalogClient;
    private final InventoryItemRepository itemRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryReservationRepository reservationRepository;
    private final AvailabilityQueryRepository queries;
    private final AvailabilityProperties properties;
    private Clock clock = Clock.systemUTC();

    public AvailabilityEstimateService(
            InventoryStockService stockService,
            CatalogClient catalogClient,
            InventoryItemRepository itemRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryReservationRepository reservationRepository,
            AvailabilityQueryRepository queries,
            AvailabilityProperties properties) {
        this.stockService = stockService;
        this.catalogClient = catalogClient;
        this.itemRepository = itemRepository;
        this.balanceRepository = balanceRepository;
        this.reservationRepository = reservationRepository;
        this.queries = queries;
        this.properties = properties;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public AvailabilityEstimateResponse estimate(AvailabilityEstimateRequest request) {
        return toResponse(plan(request));
    }

    /** Kế hoạch chi tiết cho từng mặt hàng; {@link BackorderPreparationService} dùng để tạo lệnh sản xuất. */
    List<LinePlan> plan(AvailabilityEstimateRequest request) {
        Map<UUID, BigDecimal> quantityByVariant = new LinkedHashMap<>();
        for (AvailabilityEstimateRequest.Line line : request.lines()) {
            quantityByVariant.merge(line.productVariantId(), line.quantity(), BigDecimal::add);
        }
        List<UUID> variantIds = new ArrayList<>(quantityByVariant.keySet());

        Map<UUID, StockVariantBatchResponse> stockByVariant = stockService
                .getStocksByVariantIds(new StockVariantBatchRequest(variantIds, null)).stream()
                .collect(Collectors.toMap(StockVariantBatchResponse::variantId, Function.identity(), (a, b) -> a));
        Map<UUID, InventoryItem> itemByVariant = itemRepository
                .findByReferenceIdInAndItemType(variantIds, InventoryItemType.PRODUCT_VARIANT).stream()
                .collect(Collectors.toMap(InventoryItem::getReferenceId, Function.identity(), (a, b) -> a));

        Instant now = clock.instant();
        List<LinePlan> plans = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantityByVariant.entrySet()) {
            plans.add(planLine(entry.getKey(), entry.getValue(), stockByVariant.get(entry.getKey()),
                    itemByVariant.get(entry.getKey()), request.orderId(), now));
        }
        return plans;
    }

    AvailabilityEstimateResponse toResponse(List<LinePlan> plans) {
        boolean backorder = plans.stream().anyMatch(plan -> plan.estimate().scenario() != AvailabilityScenario.AVAILABLE);
        Instant latest = null;
        boolean unknown = false;
        for (LinePlan plan : plans) {
            if (plan.estimate().scenario() == AvailabilityScenario.AVAILABLE) {
                continue;
            }
            Instant eta = plan.estimate().estimatedAvailableAt();
            if (eta == null) {
                unknown = true;
            } else if (latest == null || eta.isAfter(latest)) {
                latest = eta;
            }
        }
        return new AvailabilityEstimateResponse(backorder, unknown ? null : latest,
                plans.stream().map(LinePlan::estimate).toList());
    }

    private LinePlan planLine(UUID variantId, BigDecimal requested, StockVariantBatchResponse stock,
                              InventoryItem variantItem, UUID orderId, Instant now) {
        BigDecimal shelf = stock == null ? BigDecimal.ZERO : zero(stock.shelfQuantity());
        BigDecimal warehouse = stock == null ? BigDecimal.ZERO : zero(stock.warehouseQuantity());
        BigDecimal available = stock == null ? BigDecimal.ZERO : zero(stock.availableQuantity());
        BigDecimal missing = requested.subtract(available).max(BigDecimal.ZERO);
        String itemName = variantItem == null ? null : variantItem.getName();

        if (missing.signum() <= 0) {
            return new LinePlan(new LineEstimate(variantId, itemName, requested, shelf, warehouse, BigDecimal.ZERO,
                    AvailabilityScenario.AVAILABLE, now, 0, null, null, null, false, List.of(),
                    "Đủ hàng để bán ngay."), List.of(), BigDecimal.ZERO, List.of());
        }

        ProductionLead lead = productionLead(variantItem);

        // 1. Mặt hàng có đang được sản xuất không, bao giờ xong.
        List<ProductionOrder> usedOrders = new ArrayList<>();
        BigDecimal remaining = missing;
        Instant productionEta = null;
        for (ProductionOrder order : queries.findOpenProductionOrders(variantId, OPEN_PRODUCTION_STATUSES)) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (order.getSourceOrderId() != null && !order.getSourceOrderId().equals(orderId)) {
                continue; // lệnh này đã dành cho một đơn chờ hàng khác
            }
            usedOrders.add(order);
            remaining = remaining.subtract(zero(order.getPlannedQuantity()));
            Instant eta = expectedCompletion(order, lead.duration(), now);
            if (productionEta == null || eta.isAfter(productionEta)) {
                productionEta = eta;
            }
        }
        remaining = remaining.max(BigDecimal.ZERO);
        String productionOrderCode = usedOrders.isEmpty() ? null : usedOrders.get(usedOrders.size() - 1).getOrderCode();

        if (remaining.signum() <= 0) {
            String message = "Mặt hàng đang được sản xuất theo lệnh " + productionOrderCode
                    + ", dự kiến xong ngày " + formatDate(productionEta) + ".";
            return new LinePlan(lineEstimate(variantId, itemName, requested, shelf, warehouse, missing,
                    AvailabilityScenario.IN_PRODUCTION, productionEta, lead, productionOrderCode, false, List.of(),
                    message, now), usedOrders, BigDecimal.ZERO, List.of());
        }

        // 2. Phần còn lại phải sản xuất mới: xét nguyên liệu.
        List<CatalogClient.BomItemResponse> bom = safeBom(variantId);
        if (bom.isEmpty()) {
            String message = "Mặt hàng chưa có định mức nguyên liệu nên chưa ước tính được ngày có hàng.";
            return new LinePlan(lineEstimate(variantId, itemName, requested, shelf, warehouse, missing,
                    AvailabilityScenario.NOT_PRODUCIBLE, null, lead, productionOrderCode, true, List.of(),
                    message, now), usedOrders, remaining, List.of());
        }

        List<MaterialNeed> needs = materialNeeds(variantId, bom, remaining, now);
        List<MaterialEstimate> materials = needs.stream().map(MaterialNeed::estimate).toList();
        Instant materialReadyAt = now;
        boolean anyMissing = false;
        boolean allCoveredByReceipts = true;
        for (MaterialNeed need : needs) {
            MaterialEstimate estimate = need.estimate();
            if (estimate.missingQuantity().signum() <= 0) {
                continue;
            }
            anyMissing = true;
            if (!"OPEN_RECEIPT".equals(estimate.arrivalSource())) {
                allCoveredByReceipts = false;
            }
            if (estimate.expectedArrivalAt() != null && estimate.expectedArrivalAt().isAfter(materialReadyAt)) {
                materialReadyAt = estimate.expectedArrivalAt();
            }
        }

        Instant eta = materialReadyAt.plus(lead.duration());
        if (productionEta != null && productionEta.isAfter(eta)) {
            eta = productionEta;
        }

        AvailabilityScenario scenario;
        String message;
        if (!anyMissing) {
            scenario = AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS;
            message = "Kho hết thành phẩm nhưng đủ nguyên liệu. Sản xuất theo đơn, dự kiến có hàng ngày "
                    + formatDate(eta) + " (" + lead.describe() + ").";
        } else {
            String names = needs.stream()
                    .filter(need -> need.estimate().missingQuantity().signum() > 0)
                    .map(need -> need.estimate().name())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            if (allCoveredByReceipts) {
                scenario = AvailabilityScenario.WAIT_MATERIAL_RECEIPT;
                message = "Thiếu nguyên liệu (" + names + ") nhưng đã có phiếu nhập, dự kiến về ngày "
                        + formatDate(materialReadyAt) + ". Cộng " + lead.describe()
                        + ", dự kiến có hàng ngày " + formatDate(eta) + ".";
            } else {
                scenario = AvailabilityScenario.WAIT_MATERIAL_PURCHASE;
                message = "Thiếu nguyên liệu (" + names + ") và chưa có phiếu nhập đủ. Theo lịch sử nhập hàng, "
                        + "nguyên liệu dự kiến về ngày " + formatDate(materialReadyAt) + ". Cộng " + lead.describe()
                        + ", dự kiến có hàng ngày " + formatDate(eta) + ".";
            }
        }

        return new LinePlan(lineEstimate(variantId, itemName, requested, shelf, warehouse, missing, scenario, eta,
                lead, productionOrderCode, anyMissing, materials, message, now), usedOrders, remaining, needs);
    }

    private LineEstimate lineEstimate(UUID variantId, String itemName, BigDecimal requested, BigDecimal shelf,
                                      BigDecimal warehouse, BigDecimal missing, AvailabilityScenario scenario,
                                      Instant eta, ProductionLead lead, String productionOrderCode,
                                      boolean depositRecommended, List<MaterialEstimate> materials, String message,
                                      Instant now) {
        Integer waitDays = eta == null ? null : waitDays(now, eta);
        return new LineEstimate(variantId, itemName, requested, shelf, warehouse, missing, scenario, eta, waitDays,
                lead.days(), lead.source(), productionOrderCode, depositRecommended, materials, message);
    }

    // ------------------------------------------------------------------ production duration

    /**
     * Thời gian sản xuất trung bình đã lưu sẵn trên mặt hàng (tính lại mỗi khi một lệnh sản xuất hoàn tất).
     * Mặt hàng chưa có lịch sử thì lấy trung bình của các thành phẩm khác; vẫn chưa có thì cộng 1 ngày.
     */
    ProductionLead productionLead(InventoryItem variantItem) {
        Duration stored = storedLead(variantItem);
        if (stored != null) {
            return new ProductionLead(stored, "VARIANT_HISTORY");
        }
        Duration overall = averageLead(InventoryItemType.PRODUCT_VARIANT);
        if (overall != null) {
            return new ProductionLead(overall, "ALL_HISTORY");
        }
        return new ProductionLead(Duration.ofDays(Math.max(1, properties.getDefaultProductionDays())), "DEFAULT");
    }

    private Duration storedLead(InventoryItem item) {
        Long seconds = item == null ? null : item.getAvgSupplyLeadSeconds();
        return seconds == null || seconds <= 0 ? null : Duration.ofSeconds(seconds);
    }

    private Duration averageLead(InventoryItemType itemType) {
        Double seconds = queries.averageSupplyLeadSeconds(itemType);
        return seconds == null || seconds <= 0 ? null : Duration.ofSeconds(Math.round(seconds));
    }

    private Instant expectedCompletion(ProductionOrder order, Duration lead, Instant now) {
        Instant start = order.getCreatedAt() == null ? now : order.getCreatedAt();
        Instant expected = start.plus(lead);
        return expected.isBefore(now) ? now : expected;
    }

    // ------------------------------------------------------------------ materials

    private List<MaterialNeed> materialNeeds(UUID variantId, List<CatalogClient.BomItemResponse> bom,
                                             BigDecimal quantityToProduce, Instant now) {
        Map<UUID, BigDecimal> requiredByIngredient = new LinkedHashMap<>();
        Map<UUID, String> nameByIngredient = new LinkedHashMap<>();
        for (CatalogClient.BomItemResponse bomItem : bom) {
            if (bomItem.ingredient() == null || bomItem.quantity() == null) {
                continue;
            }
            BigDecimal waste = bomItem.wasteRate() == null ? BigDecimal.ZERO : bomItem.wasteRate();
            BigDecimal multiplier = BigDecimal.ONE.add(waste.divide(HUNDRED, 4, RoundingMode.HALF_UP));
            BigDecimal required = bomItem.quantity().multiply(quantityToProduce).multiply(multiplier)
                    .setScale(4, RoundingMode.HALF_UP);
            requiredByIngredient.merge(bomItem.ingredient().id(), required, BigDecimal::add);
            nameByIngredient.putIfAbsent(bomItem.ingredient().id(), bomItem.ingredient().name());
        }
        if (requiredByIngredient.isEmpty()) {
            return List.of();
        }

        Map<UUID, InventoryItem> itemByIngredient = itemRepository
                .findByReferenceIdInAndItemType(requiredByIngredient.keySet(), InventoryItemType.INGREDIENT).stream()
                .collect(Collectors.toMap(InventoryItem::getReferenceId, Function.identity(), (a, b) -> a));
        List<UUID> itemIds = itemByIngredient.values().stream().map(InventoryItem::getId).toList();

        Map<UUID, BigDecimal> availableByItem = new LinkedHashMap<>();
        if (!itemIds.isEmpty()) {
            for (InventoryBalance balance : balanceRepository.findSellableBatchBalancesByInventoryItemIds(
                    itemIds, InventoryStatus.ACTIVE, InventoryLocationSaleability.nonSaleableTypes(),
                    LocalDate.now(clock.withZone(STORE_ZONE)))) {
                availableByItem.merge(balance.getInventoryItem().getId(), balance.getAvailableQuantity(), BigDecimal::add);
            }
        }

        // Nguyên liệu đã giữ chỗ để sản xuất bù chính mặt hàng này sẽ được trả lại cho lệnh sản xuất khi duyệt.
        Map<UUID, List<InventoryDocumentLine>> openReceiptLinesByItem = itemIds.isEmpty()
                ? Map.of()
                : queries.findOpenReceiptLines(itemIds, OPEN_RECEIPT_STATUSES).stream()
                        .collect(Collectors.groupingBy(line -> line.getInventoryItem().getId()));

        List<MaterialNeed> needs = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : requiredByIngredient.entrySet()) {
            UUID ingredientId = entry.getKey();
            BigDecimal required = entry.getValue();
            InventoryItem item = itemByIngredient.get(ingredientId);
            String name = item != null ? item.getName() : nameByIngredient.get(ingredientId);
            BigDecimal available = BigDecimal.ZERO;
            if (item != null) {
                available = zero(availableByItem.get(item.getId())).add(plannedForVariant(variantId, item.getId()));
            }
            BigDecimal missing = required.subtract(available).max(BigDecimal.ZERO);
            if (missing.signum() <= 0) {
                needs.add(new MaterialNeed(item, new MaterialEstimate(ingredientId, name, required, available,
                        BigDecimal.ZERO, now, "IN_STOCK", null)));
                continue;
            }
            needs.add(new MaterialNeed(item, materialArrival(ingredientId, name, required, available, missing, item,
                    item == null ? List.of() : openReceiptLinesByItem.getOrDefault(item.getId(), List.of()), now)));
        }
        return needs;
    }

    private BigDecimal plannedForVariant(UUID variantId, UUID itemId) {
        return reservationRepository
                .findByProductVariantIdAndInventoryItem_IdAndReservationTypeAndStatusOrderByCreatedAtAsc(
                        variantId, itemId, ReservationType.MATERIAL, ReservationStatus.RESERVED).stream()
                .map(InventoryReservation::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ước tính ngày có đủ một nguyên liệu, dùng chung cho đóng trà theo yêu cầu (tồn tính theo kho giữ chỗ
     * do nơi gọi truyền vào). Đủ thì trả IN_STOCK, thiếu thì đi qua cùng logic phiếu nhập/lịch sử nhập.
     */
    public MaterialEstimate estimateMaterialArrival(UUID ingredientId, String name, BigDecimal required,
                                                    BigDecimal available, InventoryItem item) {
        Instant now = clock.instant();
        BigDecimal missing = required.subtract(available).max(BigDecimal.ZERO);
        if (missing.signum() <= 0) {
            return new MaterialEstimate(ingredientId, name, required, available, BigDecimal.ZERO, now, "IN_STOCK", null);
        }
        List<InventoryDocumentLine> openLines = item == null
                ? List.of()
                : queries.findOpenReceiptLines(List.of(item.getId()), OPEN_RECEIPT_STATUSES);
        return materialArrival(ingredientId, name, required, available, missing, item, openLines, now);
    }

    /** Nguyên liệu thiếu: ưu tiên phiếu nhập đang về; không đủ thì suy từ lịch sử phiếu nhập. */
    private MaterialEstimate materialArrival(UUID ingredientId, String name, BigDecimal required, BigDecimal available,
                                             BigDecimal missing, InventoryItem item,
                                             List<InventoryDocumentLine> openLines, Instant now) {
        Duration receiptLead = null;
        List<IncomingReceipt> incoming = new ArrayList<>();
        for (InventoryDocumentLine line : openLines) {
            InventoryDocument document = line.getDocument();
            BigDecimal quantity = incomingQuantity(line, document.getStatus());
            if (quantity.signum() <= 0) {
                continue;
            }
            Instant arrival = document.getExpectedDate();
            if (arrival == null) {
                if (receiptLead == null) {
                    receiptLead = receiptLead(item).duration();
                }
                Instant created = document.getCreatedAt() == null ? now : document.getCreatedAt();
                arrival = created.plus(receiptLead);
            }
            if (arrival.isBefore(now)) {
                arrival = now;
            }
            incoming.add(new IncomingReceipt(arrival, quantity, document.getDocumentCode()));
        }
        incoming.sort(Comparator.comparing(IncomingReceipt::arrival));

        BigDecimal covered = BigDecimal.ZERO;
        for (IncomingReceipt receipt : incoming) {
            covered = covered.add(receipt.quantity());
            if (covered.compareTo(missing) >= 0) {
                return new MaterialEstimate(ingredientId, name, required, available, missing, receipt.arrival(),
                        "OPEN_RECEIPT", receipt.code());
            }
        }

        ReceiptLead history = receiptLead(item);
        return new MaterialEstimate(ingredientId, name, required, available, missing, now.plus(history.duration()),
                history.source(), null);
    }

    private BigDecimal incomingQuantity(InventoryDocumentLine line, InventoryDocumentStatus status) {
        return switch (status) {
            case PENDING_EXCEPTION_APPROVAL -> zero(line.getAcceptedQuantity());
            default -> zero(line.getOrderedQuantity());
        };
    }

    /**
     * Thời gian nhập hàng trung bình đã lưu sẵn trên nguyên liệu (tính lại mỗi khi một phiếu nhập ghi sổ).
     * Nguyên liệu chưa có lịch sử thì lấy trung bình của các nguyên liệu khác; vẫn chưa có thì dùng mặc định.
     */
    ReceiptLead receiptLead(InventoryItem item) {
        Duration stored = storedLead(item);
        if (stored != null) {
            return new ReceiptLead(stored, "RECEIPT_HISTORY");
        }
        Duration overall = averageLead(InventoryItemType.INGREDIENT);
        if (overall != null) {
            return new ReceiptLead(overall, "RECEIPT_HISTORY");
        }
        return new ReceiptLead(Duration.ofDays(Math.max(1, properties.getDefaultReceiptLeadDays())), "DEFAULT");
    }

    // ------------------------------------------------------------------ helpers

    private List<CatalogClient.BomItemResponse> safeBom(UUID variantId) {
        List<CatalogClient.BomItemResponse> bom = catalogClient.getBomItems(variantId);
        return bom == null ? List.of() : bom;
    }

    static int waitDays(Instant now, Instant eta) {
        if (!eta.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, eta).getSeconds();
        return (int) ((seconds + 86_399) / 86_400);
    }

    private static String formatDate(Instant instant) {
        return instant == null ? "chưa xác định" : DATE.format(instant.atZone(STORE_ZONE));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ------------------------------------------------------------------ value types

    /**
     * @param usedProductionOrders lệnh sản xuất đang mở được tính để bù phần thiếu
     * @param quantityToProduce    phần còn phải sản xuất mới (0 nếu lệnh đang mở đã đủ)
     */
    record LinePlan(LineEstimate estimate, List<ProductionOrder> usedProductionOrders, BigDecimal quantityToProduce,
                    List<MaterialNeed> materialNeeds) {}

    record MaterialNeed(InventoryItem item, MaterialEstimate estimate) {}

    record ProductionLead(Duration duration, String source) {
        BigDecimal days() {
            return BigDecimal.valueOf(duration.getSeconds()).divide(BigDecimal.valueOf(86_400), 1, RoundingMode.UP);
        }

        String describe() {
            String days = days().stripTrailingZeros().toPlainString().replace('.', ',');
            return "DEFAULT".equals(source)
                    ? "thời gian sản xuất mặc định " + days + " ngày vì chưa có lịch sử"
                    : "thời gian sản xuất trung bình " + days + " ngày";
        }
    }

    record ReceiptLead(Duration duration, String source) {}

    private record IncomingReceipt(Instant arrival, BigDecimal quantity, String code) {}

    static final Set<AvailabilityScenario> WAITING_FOR_MATERIALS = Set.of(
            AvailabilityScenario.WAIT_MATERIAL_RECEIPT, AvailabilityScenario.WAIT_MATERIAL_PURCHASE);
}
