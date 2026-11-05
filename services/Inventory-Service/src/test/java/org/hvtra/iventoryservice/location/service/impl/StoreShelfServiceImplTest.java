package org.hvtra.iventoryservice.location.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.dto.request.StoreShelfRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;
import org.hvtra.iventoryservice.location.mapper.InventoryLocationMapper;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreShelfServiceImplTest {

    @Mock
    private InventoryLocationRepository repository;

    @Spy
    private InventoryLocationMapper mapper = new InventoryLocationMapper();

    @InjectMocks
    private StoreShelfServiceImpl service;

    private UUID shelfId;
    private UUID warehouseId;
    private InventoryLocation warehouse;
    private InventoryLocation shelf;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
        shelfId = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");

        warehouse = new InventoryLocation();
        warehouse.setId(warehouseId);
        warehouse.setName("Kho Tổng");
        warehouse.setType(InventoryLocationType.WAREHOUSE);
        warehouse.setStatus(InventoryStatus.ACTIVE);
        warehouse.setIsDeleted(false);

        shelf = new InventoryLocation();
        shelf.setId(shelfId);
        shelf.setName("Kệ A1 Trưng Bày");
        shelf.setCode("KE-1001");
        shelf.setType(InventoryLocationType.SHELF);
        shelf.setStatus(InventoryStatus.ACTIVE);
        shelf.setIsDeleted(false);
        shelf.setParentLocation(warehouse);
    }

    @Test
    @DisplayName("lookup - Success with keyword search")
    void lookup_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<InventoryLocation> page = new PageImpl<>(List.of(shelf), pageable, 1);
        when(repository.findByNameContainingIgnoreCaseAndTypeAndIsDeletedFalse("Kệ", InventoryLocationType.SHELF, pageable))
                .thenReturn(page);

        PageResponse<LookupResponse> response = service.lookup("Kệ", pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Kệ A1 Trưng Bày");
        assertThat(response.content().getFirst().id()).isEqualTo(shelfId.toString());
    }

    @Test
    @DisplayName("findAll - Success when warehouse exists and shelves returned")
    void findAll_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(warehouse));
        Page<InventoryLocation> page = new PageImpl<>(List.of(shelf), pageable, 1);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<InventoryLocationResponse> response = service.findAll("Kệ A1", InventoryStatus.ACTIVE, pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Kệ A1 Trưng Bày");
        assertThat(response.content().getFirst().type()).isEqualTo(InventoryLocationType.SHELF);
    }

    @Test
    @DisplayName("findAll - Throws ResourceNotFoundException when no active warehouse found")
    void findAll_NoWarehouse_ThrowsException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(null, null, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy kho WAREHOUSE nào đang hoạt động");
    }

    @Test
    @DisplayName("findById - Success when shelf exists")
    void findById_Success() {
        when(repository.findById(shelfId)).thenReturn(Optional.of(shelf));

        InventoryLocationResponse response = service.findById(shelfId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(shelfId);
        assertThat(response.name()).isEqualTo("Kệ A1 Trưng Bày");
        assertThat(response.type()).isEqualTo(InventoryLocationType.SHELF);
    }

    @Test
    @DisplayName("findById - Throws ResourceNotFoundException when id is not SHELF type")
    void findById_NotShelfType_ThrowsException() {
        when(repository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.findById(warehouseId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Kệ");
    }

    @Test
    @DisplayName("create - Success creating new shelf")
    void create_Success() {
        StoreShelfRequest request = new StoreShelfRequest("Kệ Trà Ô Long", InventoryStatus.ACTIVE);
        when(repository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(warehouse));
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(InventoryLocation.class))).thenAnswer(invocation -> {
            InventoryLocation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InventoryLocationResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Kệ Trà Ô Long");
        assertThat(response.type()).isEqualTo(InventoryLocationType.SHELF);
        assertThat(response.code()).contains("KTOL-");
        verify(repository).save(any(InventoryLocation.class));
    }

    @Test
    @DisplayName("update - Success updating shelf name and status")
    void update_Success() {
        StoreShelfRequest request = new StoreShelfRequest("Kệ A1 - Đổi Tên", InventoryStatus.INACTIVE);
        when(repository.findById(shelfId)).thenReturn(Optional.of(shelf));
        when(repository.save(any(InventoryLocation.class))).thenReturn(shelf);

        InventoryLocationResponse response = service.update(shelfId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Kệ A1 - Đổi Tên");
        assertThat(response.status()).isEqualTo(InventoryStatus.INACTIVE);
        verify(repository).save(shelf);
    }

    @Test
    @DisplayName("updateStatus - Success updating shelf status")
    void updateStatus_Success() {
        when(repository.findById(shelfId)).thenReturn(Optional.of(shelf));
        when(repository.save(any(InventoryLocation.class))).thenReturn(shelf);

        InventoryLocationResponse response = service.updateStatus(shelfId, InventoryStatus.INACTIVE);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InventoryStatus.INACTIVE);
        verify(repository).save(shelf);
    }

    @Test
    @DisplayName("delete - Success soft deleting shelf")
    void delete_Success() {
        when(repository.findById(shelfId)).thenReturn(Optional.of(shelf));

        service.delete(shelfId);

        assertThat(shelf.getIsDeleted()).isTrue();
        verify(repository).save(shelf);
    }

    @Test
    @DisplayName("delete - Throws ResourceNotFoundException when shelf already deleted")
    void delete_AlreadyDeleted_ThrowsException() {
        shelf.setIsDeleted(true);
        when(repository.findById(shelfId)).thenReturn(Optional.of(shelf));

        assertThatThrownBy(() -> service.delete(shelfId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Kệ");
    }
}

