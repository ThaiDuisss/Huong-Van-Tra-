package org.hvtra.iventoryservice.batch.service.impl;

import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.stock.dto.response.WarehouseBatchResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryBatchServiceImplTest {

    @Mock
    private InventoryBatchRepository batchRepository;

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private InventoryBatchServiceImpl service;

    private UUID itemId;
    private UUID locationId;
    private UUID batchId;
    private UUID unitId;
    private InventoryItem item;
    private InventoryLocation location;
    private InventoryBatch batch;
    private InventoryBalance balance;

    @BeforeEach
    void setUp() {
        itemId = UUID.fromString("550e8400-e29b-41d4-a716-446655440070");
        locationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440071");
        batchId = UUID.fromString("550e8400-e29b-41d4-a716-446655440072");
        unitId = UUID.fromString("550e8400-e29b-41d4-a716-446655440073");

        item = new InventoryItem();
        item.setId(itemId);
        item.setReferenceCode("TRA-001");
        item.setItemType(InventoryItemType.PRODUCT_VARIANT);
        item.setUnitId(unitId);

        location = new InventoryLocation();
        location.setId(locationId);
        location.setType(InventoryLocationType.WAREHOUSE);

        batch = new InventoryBatch();
        batch.setId(batchId);
        batch.setBatchCode("BATCH-202611-001");
        batch.setManufactureDate(LocalDate.now().minusDays(10));
        batch.setExpiryDate(LocalDate.now().plusDays(90));
        batch.setStatus(InventoryBatchStatus.AVAILABLE);

        balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setLocation(location);
        balance.setInventoryBatch(batch);
        balance.setQuantityOnHand(new BigDecimal("150.00"));
        balance.setQuantityReserved(new BigDecimal("20.00"));
        balance.setQuantityBlocked(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getBatchesByItemAndLocation - Success returning batches sorted by FEFO")
    void getBatchesByItemAndLocation_Success() {
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance));

        CatalogClient.UnitResponse unitResponse = new CatalogClient.UnitResponse(
                unitId, "Hộp", "hop", 0, "ACTIVE");
        when(catalogClient.getUnitsBatch(List.of(unitId))).thenReturn(List.of(unitResponse));

        List<WarehouseBatchResponse> result = service.getBatchesByItemAndLocation(itemId, locationId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        WarehouseBatchResponse res = result.getFirst();
        assertThat(res.batchCode()).isEqualTo("BATCH-202611-001");
        assertThat(res.status()).isEqualTo("AVAILABLE");
        assertThat(res.unitSymbol()).isEqualTo("hop");
        assertThat(res.quantityOnHand()).isEqualTo(new BigDecimal("150.00"));
        assertThat(res.availableQuantity()).isEqualTo(new BigDecimal("130.00"));
    }

    @Test
    @DisplayName("getBatchesByItemAndLocation - Returns empty list when no balances")
    void getBatchesByItemAndLocation_Empty() {
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        List<WarehouseBatchResponse> result = service.getBatchesByItemAndLocation(itemId, locationId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getBatchDetail - Success returning detail of specific batch and location")
    void getBatchDetail_Success() {
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance));
        CatalogClient.UnitResponse unitResponse = new CatalogClient.UnitResponse(
                unitId, "Hộp", "hop", 0, "ACTIVE");
        when(catalogClient.getUnitsBatch(List.of(unitId))).thenReturn(List.of(unitResponse));

        WarehouseBatchResponse res = service.getBatchDetail(batchId, locationId);

        assertThat(res).isNotNull();
        assertThat(res.batchId()).isEqualTo(batchId);
        assertThat(res.batchCode()).isEqualTo("BATCH-202611-001");
        assertThat(res.quantityOnHand()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("getBatchDetail - Throws ResourceNotFoundException when batch balance not found")
    void getBatchDetail_NotFound_ThrowsException() {
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.getBatchDetail(batchId, locationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("InventoryBalance");
    }
}

