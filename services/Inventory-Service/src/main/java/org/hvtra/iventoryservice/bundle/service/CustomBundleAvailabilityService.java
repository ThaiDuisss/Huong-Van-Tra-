package org.hvtra.iventoryservice.bundle.service;

import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleLineRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kiểm tra tồn nguyên liệu của bundle tại đúng kho mà bước giữ chỗ sẽ lấy (kho WAREHOUSE của chi nhánh,
 * cùng quy tắc với InventoryReservationServiceImpl) và ước tính ngày có đủ phần còn thiếu.
 */
@Service
@Transactional(readOnly = true)
public class CustomBundleAvailabilityService {

    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final AvailabilityEstimateService estimateService;

    public CustomBundleAvailabilityService(
            InventoryItemRepository itemRepository,
            InventoryLocationRepository locationRepository,
            InventoryBalanceRepository balanceRepository,
            AvailabilityEstimateService estimateService) {
        this.itemRepository = itemRepository;
        this.locationRepository = locationRepository;
        this.balanceRepository = balanceRepository;
        this.estimateService = estimateService;
    }

    public CustomBundleAvailabilityResponse check(UUID branchId, List<CustomBundleLineRequest> lines) {
        return evaluate(branchId, lines).response();
    }

    /** Dùng cho CustomBundlePackingServiceImpl: kèm kho giữ chỗ và inventory item của từng nguyên liệu. */
    public Evaluation evaluate(UUID branchId, List<CustomBundleLineRequest> lines) {
        Map<UUID, BigDecimal> requiredByIngredient = new LinkedHashMap<>();
        Map<UUID, String> nameByIngredient = new LinkedHashMap<>();
        for (CustomBundleLineRequest line : lines) {
            requiredByIngredient.merge(line.ingredientId(), line.quantity(), BigDecimal::add);
            nameByIngredient.putIfAbsent(line.ingredientId(), line.ingredientName());
        }

        Map<UUID, InventoryItem> itemByIngredient = itemRepository
                .findByReferenceIdInAndItemType(requiredByIngredient.keySet(), InventoryItemType.INGREDIENT).stream()
                .collect(Collectors.toMap(InventoryItem::getReferenceId, Function.identity(), (a, b) -> a));
        for (UUID ingredientId : requiredByIngredient.keySet()) {
            InventoryItem item = itemByIngredient.get(ingredientId);
            if (item == null) {
                String name = nameByIngredient.get(ingredientId);
                throw new BadRequestException(name == null
                        ? "Có nguyên liệu đã chọn chưa có trong kho"
                        : "Nguyên liệu " + name + " chưa có trong kho");
            }
            if (item.getStatus() != InventoryStatus.ACTIVE) {
                throw new BadRequestException("Nguyên liệu " + item.getName() + " đã ngừng hoạt động trong kho");
            }
        }

        InventoryLocation location = resolveWarehouse(branchId);
        Map<UUID, BigDecimal> availableByItem = new LinkedHashMap<>();
        List<UUID> itemIds = itemByIngredient.values().stream().map(InventoryItem::getId).toList();
        for (Object[] row : balanceRepository.sumFefoAvailableByItemsAtLocation(
                itemIds, location.getId(), InventoryStatus.ACTIVE, LocalDate.now())) {
            availableByItem.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<Material> materials = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : requiredByIngredient.entrySet()) {
            InventoryItem item = itemByIngredient.get(entry.getKey());
            BigDecimal available = availableByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            MaterialEstimate estimate = estimateService.estimateMaterialArrival(
                    entry.getKey(), item.getName(), entry.getValue(), available, item);
            materials.add(new Material(item, estimate));
        }
        return new Evaluation(location, materials, toResponse(materials));
    }

    /** Cùng quy tắc với InventoryReservationServiceImpl#resolveLocation khi không truyền locationId. */
    private InventoryLocation resolveWarehouse(UUID branchId) {
        return branchId == null
                ? locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                        .orElseThrow(() -> new BadRequestException("No active WAREHOUSE location found"))
                : locationRepository.findFirstByBranchIdAndTypeAndStatus(
                                branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                        .orElseThrow(() -> new BadRequestException("No active WAREHOUSE location found for branch " + branchId));
    }

    private CustomBundleAvailabilityResponse toResponse(List<Material> materials) {
        Instant now = Instant.now();
        Instant latest = null;
        List<CustomBundleAvailabilityResponse.MaterialLine> lines = new ArrayList<>();
        for (Material material : materials) {
            MaterialEstimate estimate = material.estimate();
            if (estimate.missingQuantity().signum() > 0
                    && (latest == null || estimate.expectedArrivalAt().isAfter(latest))) {
                latest = estimate.expectedArrivalAt();
            }
            lines.add(new CustomBundleAvailabilityResponse.MaterialLine(
                    estimate.ingredientId(),
                    material.item().getReferenceCode(),
                    estimate.name(),
                    estimate.requiredQuantity(),
                    estimate.availableQuantity(),
                    estimate.missingQuantity(),
                    estimate.expectedArrivalAt(),
                    estimate.arrivalSource(),
                    estimate.receiptCode()));
        }
        boolean enough = latest == null;
        return new CustomBundleAvailabilityResponse(enough, latest, enough ? 0 : waitDays(now, latest), lines);
    }

    private static int waitDays(Instant now, Instant eta) {
        if (!eta.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, eta).getSeconds();
        return (int) ((seconds + 86_399) / 86_400);
    }

    public record Material(InventoryItem item, MaterialEstimate estimate) {
    }

    public record Evaluation(InventoryLocation location, List<Material> materials, CustomBundleAvailabilityResponse response) {
        public boolean enough() {
            return response.enough();
        }
    }
}
