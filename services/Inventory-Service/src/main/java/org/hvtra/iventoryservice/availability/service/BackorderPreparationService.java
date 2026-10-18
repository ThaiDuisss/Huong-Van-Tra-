package org.hvtra.iventoryservice.availability.service;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.availability.AvailabilityScenario;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateRequest;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.LineEstimate;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService.LinePlan;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService.MaterialNeed;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.production.dto.request.CreateProductionOrderRequest;
import org.hvtra.iventoryservice.production.dto.response.ProductionOrderResponse;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.hvtra.iventoryservice.production.service.ProductionOrderService;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chuẩn bị hàng cho đơn đã thu tiền nhưng chưa có hàng giao:
 * <ul>
 *   <li>Đủ nguyên liệu: tạo lệnh sản xuất theo đơn và gửi duyệt ngay.</li>
 *   <li>Thiếu nguyên liệu: tạo lệnh sản xuất nháp gắn với đơn và mở cảnh báo cần nhập nguyên liệu.</li>
 *   <li>Mặt hàng đang được sản xuất đủ số lượng: không tạo thêm, chỉ trả về lệnh đang chạy.</li>
 * </ul>
 * Gọi lại cho cùng một đơn không tạo lệnh thứ hai. Mỗi mặt hàng chạy trong transaction riêng để một mặt
 * hàng lỗi không làm hỏng các mặt hàng còn lại.
 */
@Slf4j
@Service
public class BackorderPreparationService {

    private static final List<InventoryReplenishmentAlertType> MATERIAL_ALERT_TYPES = List.of(
            InventoryReplenishmentAlertType.LOW_STOCK,
            InventoryReplenishmentAlertType.OUT_OF_STOCK,
            InventoryReplenishmentAlertType.REORDER_REQUIRED);

    private final AvailabilityEstimateService estimateService;
    private final AvailabilityQueryRepository queries;
    private final ProductionOrderService productionOrderService;
    private final ProductionOrderRepository productionOrderRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryReplenishmentAlertRepository alertRepository;
    private final TransactionTemplate transaction;

    public BackorderPreparationService(
            AvailabilityEstimateService estimateService,
            AvailabilityQueryRepository queries,
            ProductionOrderService productionOrderService,
            ProductionOrderRepository productionOrderRepository,
            InventoryLocationRepository locationRepository,
            InventoryReplenishmentAlertRepository alertRepository,
            PlatformTransactionManager transactionManager) {
        this.estimateService = estimateService;
        this.queries = queries;
        this.productionOrderService = productionOrderService;
        this.productionOrderRepository = productionOrderRepository;
        this.locationRepository = locationRepository;
        this.alertRepository = alertRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public AvailabilityEstimateResponse prepare(AvailabilityEstimateRequest request) {
        if (request.orderId() == null) {
            throw new BadRequestException("orderId is required");
        }
        List<LinePlan> plans = estimateService.plan(request);
        InventoryLocation warehouse = resolveWarehouse(request.branchId());

        List<LinePlan> prepared = new ArrayList<>();
        for (LinePlan plan : plans) {
            String productionOrderCode = null;
            try {
                productionOrderCode = transaction.execute(status -> prepareLine(request, plan, warehouse));
            } catch (RuntimeException exception) {
                log.warn("Backorder preparation failed. orderId={}, variantId={}, reason={}",
                        request.orderId(), plan.estimate().productVariantId(), exception.getMessage());
            }
            LineEstimate estimate = productionOrderCode == null
                    ? plan.estimate()
                    : plan.estimate().withProductionOrderCode(productionOrderCode);
            prepared.add(new LinePlan(estimate, plan.usedProductionOrders(), plan.quantityToProduce(),
                    plan.materialNeeds()));
        }
        return estimateService.toResponse(prepared);
    }

    /** @return mã lệnh sản xuất phục vụ mặt hàng này (null nếu không có). */
    private String prepareLine(AvailabilityEstimateRequest request, LinePlan plan, InventoryLocation warehouse) {
        LineEstimate estimate = plan.estimate();
        AvailabilityScenario scenario = estimate.scenario();
        if (scenario == AvailabilityScenario.AVAILABLE || scenario == AvailabilityScenario.NOT_PRODUCIBLE) {
            return estimate.productionOrderCode();
        }

        List<ProductionOrder> linked = queries.findBySourceOrderAndVariant(request.orderId(), estimate.productVariantId());
        if (!linked.isEmpty()) {
            return linked.get(linked.size() - 1).getOrderCode();
        }
        if (plan.quantityToProduce().signum() <= 0) {
            return estimate.productionOrderCode(); // lệnh đang chạy đã đủ bù
        }
        if (warehouse == null) {
            throw new BadRequestException("Active warehouse not found");
        }

        BigDecimal plannedQuantity = plan.quantityToProduce().setScale(2, RoundingMode.UP);
        String orderLabel = request.orderCode() == null || request.orderCode().isBlank()
                ? request.orderId().toString()
                : request.orderCode();
        boolean materialsReady = scenario == AvailabilityScenario.PRODUCE_FROM_STOCK_MATERIALS;
        CreateProductionOrderRequest createRequest = new CreateProductionOrderRequest(
                estimate.productVariantId(), plannedQuantity, warehouse.getId(), "Sản xuất theo đơn " + orderLabel);

        if (!materialsReady) {
            // Thiếu nguyên liệu: mở cảnh báo nhập nguyên liệu trước, rồi lập lệnh nháp (không kiểm tra tồn
            // nguyên liệu) để kho duyệt khi nguyên liệu về. Trước đây create() ném OutOfStock nên đơn chờ hàng
            // không có lệnh sản xuất lẫn cảnh báo.
            openMaterialAlerts(plan, warehouse);
        }
        ProductionOrderResponse created = materialsReady
                ? productionOrderService.create(createRequest)
                : productionOrderService.createDraft(createRequest);

        ProductionOrder order = productionOrderRepository.findById(created.id())
                .orElseThrow(() -> new BadRequestException("Production order not found after creation"));
        order.setSourceOrderId(request.orderId());
        order.setSourceOrderCode(request.orderCode());
        productionOrderRepository.save(order);

        if (materialsReady) {
            productionOrderService.submit(created.id());
        }
        return created.orderCode();
    }

    /** Thiếu nguyên liệu mà chưa có phiếu nhập: mở cảnh báo để bộ phận kho lập phiếu nhập. */
    private void openMaterialAlerts(LinePlan plan, InventoryLocation warehouse) {
        for (MaterialNeed need : plan.materialNeeds()) {
            var material = need.estimate();
            if (need.item() == null || material.missingQuantity().signum() <= 0
                    || "OPEN_RECEIPT".equals(material.arrivalSource())) {
                continue;
            }
            if (alertRepository.existsOpenAlert(need.item().getId(), warehouse.getId(),
                    MATERIAL_ALERT_TYPES)) {
                continue;
            }
            InventoryReplenishmentAlert alert = new InventoryReplenishmentAlert();
            alert.setInventoryItem(need.item());
            alert.setLocation(warehouse);
            alert.setAlertType(InventoryReplenishmentAlertType.REORDER_REQUIRED);
            alert.setAvailableQuantitySnapshot(material.availableQuantity().setScale(2, RoundingMode.DOWN));
            alert.setReorderPointSnapshot(material.requiredQuantity().setScale(2, RoundingMode.UP));
            alert.setSuggestedQuantity(material.missingQuantity().setScale(2, RoundingMode.UP));
            alert.setOpenedAt(Instant.now());
            alertRepository.save(alert);
        }
    }

    private InventoryLocation resolveWarehouse(UUID branchId) {
        if (branchId != null) {
            var byBranch = locationRepository.findFirstByBranchIdAndTypeAndStatus(
                    branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
            if (byBranch.isPresent()) {
                return byBranch.get();
            }
        }
        return locationRepository.findFirstByTypeAndStatusOrderByCreatedAtAsc(
                InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE).orElse(null);
    }
}
