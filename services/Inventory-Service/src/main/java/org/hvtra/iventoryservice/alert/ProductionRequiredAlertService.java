package org.hvtra.iventoryservice.alert;

import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductionRequiredAlertService {
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryReplenishmentAlertRepository alertRepository;

    @Transactional
    public void createIfAbsent(ProductionRequiredAlertRequest request) {
        var item = itemRepository.findByReferenceIdAndItemType(request.variantId(), InventoryItemType.PRODUCT_VARIANT)
                .orElseThrow(() -> new BadRequestException("Product variant inventory item not found"));
        var location = request.branchId() == null
                ? locationRepository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE)
                : locationRepository.findFirstByBranchIdAndTypeAndStatus(request.branchId(),
                        InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
        var warehouse = location.orElseThrow(() -> new BadRequestException("Active warehouse not found"));
        if (alertRepository.existsOpenProductionAlert(item.getId(), warehouse.getId())) {
            return;
        }
        InventoryReplenishmentAlert alert = new InventoryReplenishmentAlert();
        alert.setInventoryItem(item);
        alert.setLocation(warehouse);
        alert.setAlertType(InventoryReplenishmentAlertType.PRODUCTION_REQUIRED);
        alert.setAvailableQuantitySnapshot(request.sellableQuantity());
        alert.setReorderPointSnapshot(request.productionThreshold());
        alert.setSuggestedQuantity(request.productionBatchQuantity());
        alert.setOpenedAt(Instant.now());
        alertRepository.save(alert);
    }
}
