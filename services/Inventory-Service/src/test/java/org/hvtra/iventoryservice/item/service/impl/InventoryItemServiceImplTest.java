package org.hvtra.iventoryservice.item.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemLookupResponse;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.item.mapper.InventoryItemMapper;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.item.service.InventoryItemServiceImpl;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceImplTest {

    @Mock
    private InventoryItemRepository repository;

    @Spy
    private InventoryItemMapper mapper = new InventoryItemMapper();

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private InventoryItemServiceImpl service;

    private UUID itemId;
    private UUID referenceId;
    private UUID unitId;
    private InventoryItem item;

    @BeforeEach
    void setUp() {
        itemId = UUID.fromString("550e8400-e29b-41d4-a716-446655440040");
        referenceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440041");
        unitId = UUID.fromString("550e8400-e29b-41d4-a716-446655440042");

        item = new InventoryItem();
        item.setId(itemId);
        item.setReferenceId(referenceId);
        item.setReferenceCode("NL-0001");
        item.setName("Búp Trà Xanh Tân Cương");
        item.setItemType(InventoryItemType.INGREDIENT);
        item.setUnitId(unitId);
        item.setStatus(InventoryStatus.ACTIVE);
        item.setReorderPoint(20);
    }

    @Test
    @DisplayName("findAll - Success with search parameters")
    void findAll_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<InventoryItem> page = new PageImpl<>(List.of(item), pageable, 1);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<InventoryItemResponse> response = service.findAll(
                referenceId, "NL-0001", InventoryItemType.INGREDIENT, InventoryStatus.ACTIVE, pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().referenceCode()).isEqualTo("NL-0001");
        assertThat(response.content().getFirst().name()).isEqualTo("Búp Trà Xanh Tân Cương");
    }

    @Test
    @DisplayName("findById - Success when item exists")
    void findById_Success() {
        when(repository.findById(itemId)).thenReturn(Optional.of(item));

        InventoryItemResponse response = service.findById(itemId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(itemId);
        assertThat(response.name()).isEqualTo("Búp Trà Xanh Tân Cương");
        assertThat(response.reorderPoint()).isEqualTo(20);
    }

    @Test
    @DisplayName("findById - Throws ResourceNotFoundException when id not found")
    void findById_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory item");
    }

    @Test
    @DisplayName("create - Success when reference not duplicate")
    void create_Success() {
        InventoryItemRequest request = new InventoryItemRequest(
                referenceId, "NL-0002", "Búp Trà Ô Long", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 15);

        when(repository.existsByReferenceIdAndItemType(referenceId, InventoryItemType.INGREDIENT)).thenReturn(false);
        when(repository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InventoryItemResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Búp Trà Ô Long");
        assertThat(response.referenceCode()).isEqualTo("NL-0002");
        verify(repository).save(any(InventoryItem.class));
    }

    @Test
    @DisplayName("create - Throws BadRequestException when reference already exists")
    void create_DuplicateReference_ThrowsBadRequestException() {
        InventoryItemRequest request = new InventoryItemRequest(
                referenceId, "NL-0001", "Búp Trà Xanh", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 10);

        when(repository.existsByReferenceIdAndItemType(referenceId, InventoryItemType.INGREDIENT)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists for reference");
    }

    @Test
    @DisplayName("update - Success when updating item")
    void update_Success() {
        InventoryItemRequest request = new InventoryItemRequest(
                referenceId, "NL-0001", "Búp Trà Xanh - Cập Nhật", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 30);

        when(repository.existsByReferenceIdAndItemTypeAndIdNot(referenceId, InventoryItemType.INGREDIENT, itemId)).thenReturn(false);
        when(repository.findById(itemId)).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenReturn(item);

        InventoryItemResponse response = service.update(itemId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Búp Trà Xanh - Cập Nhật");
        assertThat(response.reorderPoint()).isEqualTo(30);
        verify(repository).save(item);
    }

    @Test
    @DisplayName("update - Throws BadRequestException when duplicate with another item")
    void update_DuplicateWithOtherId_ThrowsBadRequestException() {
        InventoryItemRequest request = new InventoryItemRequest(
                referenceId, "NL-0001", "Búp Trà Xanh", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 10);

        when(repository.existsByReferenceIdAndItemTypeAndIdNot(referenceId, InventoryItemType.INGREDIENT, itemId)).thenReturn(true);

        assertThatThrownBy(() -> service.update(itemId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists for reference");
    }

    @Test
    @DisplayName("updateByReference - Existing item updated")
    void updateByReference_Existing_Success() {
        InventoryItemRequest request = new InventoryItemRequest(
                referenceId, "NL-0001", "Búp Trà Cập Nhật Tên", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 25);

        when(repository.findByReferenceIdAndItemType(referenceId, InventoryItemType.INGREDIENT)).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenReturn(item);

        InventoryItemResponse response = service.updateByReference(referenceId, InventoryItemType.INGREDIENT, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Búp Trà Cập Nhật Tên");
        verify(repository).save(item);
    }

    @Test
    @DisplayName("updateByReference - Non-existing item created newly")
    void updateByReference_NonExisting_Created() {
        UUID newRefId = UUID.randomUUID();
        InventoryItemRequest request = new InventoryItemRequest(
                newRefId, "NL-0099", "Nguyên Liệu Mới", InventoryItemType.INGREDIENT, unitId, InventoryStatus.ACTIVE, 5);

        when(repository.findByReferenceIdAndItemType(newRefId, InventoryItemType.INGREDIENT)).thenReturn(Optional.empty());
        when(repository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InventoryItemResponse response = service.updateByReference(newRefId, InventoryItemType.INGREDIENT, request);

        assertThat(response).isNotNull();
        assertThat(response.referenceCode()).isEqualTo("NL-0099");
        assertThat(response.referenceId()).isEqualTo(newRefId);
        verify(repository).save(any(InventoryItem.class));
    }

    @Test
    @DisplayName("updateStatus - Success updating item status")
    void updateStatus_Success() {
        when(repository.findById(itemId)).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenReturn(item);

        InventoryItemResponse response = service.updateStatus(itemId, InventoryStatus.INACTIVE);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InventoryStatus.INACTIVE);
        verify(repository).save(item);
    }

    @Test
    @DisplayName("lookupIngredientsBySupplier - Success with unit names from catalog")
    void lookupIngredientsBySupplier_Success() {
        UUID supplierId = UUID.fromString("550e8400-e29b-41d4-a716-446655440049");
        when(catalogClient.getSupplierIngredientIds(supplierId)).thenReturn(List.of(referenceId));
        when(repository.findByReferenceIdInAndItemTypeAndStatusOrderByNameAsc(
                List.of(referenceId), InventoryItemType.INGREDIENT, InventoryStatus.ACTIVE)).thenReturn(List.of(item));

        CatalogClient.UnitResponse unitResponse = new CatalogClient.UnitResponse(
                unitId, "Kilogram", "kg", 2, "ACTIVE");
        when(catalogClient.getUnitsBatch(List.of(unitId))).thenReturn(List.of(unitResponse));

        List<InventoryItemLookupResponse> result = service.lookupIngredientsBySupplier(supplierId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        InventoryItemLookupResponse lookup = result.getFirst();
        assertThat(lookup.id()).isEqualTo(itemId);
        assertThat(lookup.name()).isEqualTo("Búp Trà Xanh Tân Cương");
        assertThat(lookup.unitName()).isEqualTo("kg");
    }

    @Test
    @DisplayName("lookupIngredientsBySupplier - Returns empty when supplier has no ingredients")
    void lookupIngredientsBySupplier_Empty() {
        UUID supplierId = UUID.fromString("550e8400-e29b-41d4-a716-446655440049");
        when(catalogClient.getSupplierIngredientIds(supplierId)).thenReturn(Collections.emptyList());

        List<InventoryItemLookupResponse> result = service.lookupIngredientsBySupplier(supplierId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}

