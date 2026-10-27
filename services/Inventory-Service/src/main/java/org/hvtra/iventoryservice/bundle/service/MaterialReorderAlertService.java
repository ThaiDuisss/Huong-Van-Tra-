package org.hvtra.iventoryservice.bundle.service;

import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Mở cảnh báo REORDER_REQUIRED cho nguyên liệu còn thiếu khi đơn đóng trà phải chờ hàng, cùng quy tắc với
 * BackorderPreparationService: bỏ qua nếu đã có phiếu nhập đang về hoặc đã có cảnh báo đang mở.
 */
@Component
public class MaterialReorderAlertService {

    private static final List<InventoryReplenishmentAlertType> MATERIAL_ALERT_TYPES = List.of(
            InventoryReplenishmentAlertType.LOW_STOCK,
            InventoryReplenishmentAlertType.OUT_OF_STOCK,
            InventoryReplenishmentAlertType.REORDER_REQUIRED);

    private final InventoryReplenishmentAlertRepository alertRepository;

    public MaterialReorderAlertService(InventoryReplenishmentAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /** @return true nếu đã mở cảnh báo mới. */
    public boolean openIfMissing(InventoryItem item, InventoryLocation location, MaterialEstimate material) {
        if (item == null || material.missingQuantity().signum() <= 0
                || "OPEN_RECEIPT".equals(material.arrivalSource())) {
            return false;
        }
        if (alertRepository.existsOpenAlert(item.getId(), location.getId(), MATERIAL_ALERT_TYPES)) {
            return false;
        }
        InventoryReplenishmentAlert alert = new InventoryReplenishmentAlert();
        alert.setInventoryItem(item);
        alert.setLocation(location);
        alert.setAlertType(InventoryReplenishmentAlertType.REORDER_REQUIRED);
        alert.setAvailableQuantitySnapshot(material.availableQuantity().setScale(2, RoundingMode.DOWN));
        alert.setReorderPointSnapshot(material.requiredQuantity().setScale(2, RoundingMode.UP));
        alert.setSuggestedQuantity(material.missingQuantity().setScale(2, RoundingMode.UP));
        alert.setOpenedAt(Instant.now());
        alertRepository.save(alert);
        return true;
    }
}
