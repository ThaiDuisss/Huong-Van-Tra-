package org.hvtra.iventoryservice.alert;

import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionRequiredAlertServiceTest {
    @Mock InventoryItemRepository itemRepository;
    @Mock InventoryLocationRepository locationRepository;
    @Mock InventoryReplenishmentAlertRepository alertRepository;

    @Test
    void crossingCreatesOneProductionRequiredAlert() {
        UUID variantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setStatus(InventoryStatus.ACTIVE);
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setStatus(InventoryStatus.ACTIVE);
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(location));

        ProductionRequiredAlertService service = new ProductionRequiredAlertService(itemRepository, locationRepository, alertRepository);
        service.createIfAbsent(new ProductionRequiredAlertRequest(variantId, branchId,
                new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("100")));

        verify(alertRepository).save(org.mockito.ArgumentMatchers.argThat(alert ->
                alert.getAlertType().name().equals("PRODUCTION_REQUIRED")
                        && alert.getSuggestedQuantity().compareTo(new BigDecimal("100")) == 0));
    }

    @Test
    void openAlertPreventsDuplicate() {
        UUID variantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setStatus(InventoryStatus.ACTIVE);
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setStatus(InventoryStatus.ACTIVE);
        when(itemRepository.findByReferenceIdAndItemType(variantId, InventoryItemType.PRODUCT_VARIANT))
                .thenReturn(Optional.of(item));
        when(locationRepository.findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(location));
        when(alertRepository.existsOpenProductionAlert(item.getId(), location.getId())).thenReturn(true);

        ProductionRequiredAlertService service = new ProductionRequiredAlertService(itemRepository, locationRepository, alertRepository);
        service.createIfAbsent(new ProductionRequiredAlertRequest(variantId, branchId,
                new BigDecimal("29"), new BigDecimal("30"), new BigDecimal("100")));

        verify(alertRepository, never()).save(any());
    }
}
