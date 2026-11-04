package org.hvtra.iventoryservice.warehouse.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.warehouse.dto.LocationInventoryDetailResponse;
import org.hvtra.iventoryservice.warehouse.dto.WarehouseInventorySummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseInventoryServiceImplTest {

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private WarehouseInventoryServiceImpl service;

    private UUID itemId;
    private UUID unitId;
    private UUID locationId;
    private UUID branchId;
    private InventoryItem item;
    private InventoryLocation warehouseLoc;
    private InventoryBalance balance;

    @BeforeEach
    void setUp() {
        itemId = UUID.fromString("550e8400-e29b-41d4-a716-446655440030");
        unitId = UUID.fromString("550e8400-e29b-41d4-a716-446655440031");
        locationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440032");
        branchId = UUID.fromString("550e8400-e29b-41d4-a716-446655440033");

        item = new InventoryItem();
        item.setId(itemId);
        item.setReferenceCode("TRA-001");
        item.setName("Trà Tân Cương Thượng Hạng");
        item.setItemType(InventoryItemType.PRODUCT_VARIANT);
        item.setUnitId(unitId);
        item.setReorderPoint(10);

        warehouseLoc = new InventoryLocation();
        warehouseLoc.setId(locationId);
        warehouseLoc.setName("Kho Tổng");
        warehouseLoc.setType(InventoryLocationType.WAREHOUSE);
        warehouseLoc.setBranchId(branchId);

        balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setLocation(warehouseLoc);
        balance.setQuantityOnHand(new BigDecimal("100.00"));
        balance.setQuantityReserved(new BigDecimal("10.00"));
        balance.setQuantityBlocked(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("getProductInventorySummary - Success grouping balances and resolving unit from catalog")
    void getProductInventorySummary_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance));

        CatalogClient.UnitResponse unitResponse = new CatalogClient.UnitResponse(
                unitId, "Hộp", "hop", 0, "ACTIVE");
        when(catalogClient.getUnitsBatch(List.of(unitId))).thenReturn(List.of(unitResponse));

        Page<WarehouseInventorySummaryResponse> result = service.getProductInventorySummary(
                branchId, "Tân Cương", "PRODUCT_VARIANT", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        WarehouseInventorySummaryResponse summary = result.getContent().getFirst();
        assertThat(summary.itemName()).isEqualTo("Trà Tân Cương Thượng Hạng");
        assertThat(summary.totalQuantity()).isEqualTo(new BigDecimal("100.00"));
        assertThat(summary.warehouseQuantity()).isEqualTo(new BigDecimal("100.00"));
        assertThat(summary.shelfQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(summary.unitSymbol()).isEqualTo("hop");
        assertThat(summary.stockStatus()).isEqualTo("SUFFICIENT");
    }

    @Test
    @DisplayName("getProductInventorySummary - Success when no balances found")
    void getProductInventorySummary_Empty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        Page<WarehouseInventorySummaryResponse> result = service.getProductInventorySummary(
                branchId, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getProductLocationInventory - Success returning location detail with balances")
    void getProductLocationInventory_Success() {
        when(balanceRepository.findAll(any(Specification.class))).thenReturn(List.of(balance));
        CatalogClient.UnitResponse unitResponse = new CatalogClient.UnitResponse(
                unitId, "Hộp", "hop", 0, "ACTIVE");
        when(catalogClient.getUnitsBatch(List.of(unitId))).thenReturn(List.of(unitResponse));

        List<LocationInventoryDetailResponse> details = service.getProductLocationInventory(itemId);

        assertThat(details).isNotNull();
        assertThat(details).hasSize(1);
        LocationInventoryDetailResponse detail = details.getFirst();
        assertThat(detail.locationId()).isEqualTo(locationId);
        assertThat(detail.locationName()).isEqualTo("Kho Tổng");
        assertThat(detail.quantityOnHand()).isEqualTo(new BigDecimal("100.00"));
        assertThat(detail.quantityReserved()).isEqualTo(new BigDecimal("10.00"));
        assertThat(detail.quantityBlocked()).isEqualTo(new BigDecimal("5.00"));
        assertThat(detail.availableQuantity()).isEqualTo(new BigDecimal("85.00"));
        assertThat(detail.batchCount()).isEqualTo(1L);
    }
}
