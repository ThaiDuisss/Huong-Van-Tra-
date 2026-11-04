package org.hvtra.iventoryservice.bundle;

import org.hvtra.iventoryservice.alert.InventoryReplenishmentAlertRepository;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse.MaterialEstimate;
import org.hvtra.iventoryservice.bundle.service.MaterialReorderAlertService;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.InventoryReplenishmentAlert;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaterialReorderAlertService Unit Tests")
class MaterialReorderAlertServiceTest {

    private static final UUID ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440810");
    private static final UUID INGREDIENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440800");
    private static final UUID WAREHOUSE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");

    @Mock
    private InventoryReplenishmentAlertRepository alertRepository;

    private MaterialReorderAlertService service;
    private InventoryItem item;
    private InventoryLocation warehouse;

    @BeforeEach
    void setUp() {
        service = new MaterialReorderAlertService(alertRepository);
        item = new InventoryItem();
        item.setId(ITEM_ID);
        item.setName("Trà Shan Tuyết");
        warehouse = new InventoryLocation();
        warehouse.setId(WAREHOUSE_ID);
    }

    @Test
    @DisplayName("openIfMissing opens a REORDER_REQUIRED alert with rounded snapshots")
    void openIfMissing_WhenMissingWithoutOpenAlert_ShouldOpenAlert() {
        when(alertRepository.existsOpenAlert(ITEM_ID, WAREHOUSE_ID, List.of(
                InventoryReplenishmentAlertType.LOW_STOCK,
                InventoryReplenishmentAlertType.OUT_OF_STOCK,
                InventoryReplenishmentAlertType.REORDER_REQUIRED))).thenReturn(false);

        boolean opened = service.openIfMissing(item, warehouse, estimate("0.051", "0.015", "0.036", "SUPPLIER_LEAD_TIME"));

        assertThat(opened).isTrue();
        ArgumentCaptor<InventoryReplenishmentAlert> alert = ArgumentCaptor.forClass(InventoryReplenishmentAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getAlertType()).isEqualTo(InventoryReplenishmentAlertType.REORDER_REQUIRED);
        assertThat(alert.getValue().getInventoryItem()).isSameAs(item);
        assertThat(alert.getValue().getLocation()).isSameAs(warehouse);
        assertThat(alert.getValue().getAvailableQuantitySnapshot()).isEqualByComparingTo("0.01");
        assertThat(alert.getValue().getReorderPointSnapshot()).isEqualByComparingTo("0.06");
        assertThat(alert.getValue().getSuggestedQuantity()).isEqualByComparingTo("0.04");
        assertThat(alert.getValue().getOpenedAt()).isNotNull();
    }

    @Test
    @DisplayName("openIfMissing does nothing when no quantity is missing")
    void openIfMissing_WhenNothingMissing_ShouldSkip() {
        assertThat(service.openIfMissing(item, warehouse, estimate("0.05", "0.05", "0", null))).isFalse();
        verifyNoInteractions(alertRepository);
    }

    @Test
    @DisplayName("openIfMissing does nothing when an open goods receipt is already on the way")
    void openIfMissing_WhenOpenReceiptExists_ShouldSkip() {
        assertThat(service.openIfMissing(item, warehouse, estimate("0.05", "0.01", "0.04", "OPEN_RECEIPT"))).isFalse();
        verifyNoInteractions(alertRepository);
    }

    @Test
    @DisplayName("openIfMissing does nothing without an inventory item")
    void openIfMissing_WhenItemNull_ShouldSkip() {
        assertThat(service.openIfMissing(null, warehouse, estimate("0.05", "0", "0.05", null))).isFalse();
        verifyNoInteractions(alertRepository);
    }

    @Test
    @DisplayName("openIfMissing does not duplicate an alert that is already open")
    void openIfMissing_WhenAlertAlreadyOpen_ShouldSkip() {
        when(alertRepository.existsOpenAlert(any(), any(), anyCollection())).thenReturn(true);

        assertThat(service.openIfMissing(item, warehouse, estimate("0.05", "0", "0.05", null))).isFalse();
        verify(alertRepository, never()).save(any());
    }

    private MaterialEstimate estimate(String required, String available, String missing, String source) {
        return new MaterialEstimate(INGREDIENT_ID, "Trà Shan Tuyết", new BigDecimal(required), new BigDecimal(available),
                new BigDecimal(missing), Instant.parse("2026-11-05T02:00:00Z"), source, null);
    }
}
