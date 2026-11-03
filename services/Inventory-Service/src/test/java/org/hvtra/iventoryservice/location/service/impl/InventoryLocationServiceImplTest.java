package org.hvtra.iventoryservice.location.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationResponse;
import org.hvtra.iventoryservice.location.dto.response.LookupResponse;
import org.hvtra.iventoryservice.location.mapper.InventoryLocationMapper;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.location.repository.projection.IdNameView;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryLocationServiceImplTest {

    @Mock
    private InventoryLocationRepository repository;

    @Spy
    private InventoryLocationMapper mapper = new InventoryLocationMapper();

    @InjectMocks
    private InventoryLocationServiceImpl service;

    private UUID locationId;
    private UUID branchId;
    private InventoryLocation location;

    @BeforeEach
    void setUp() {
        locationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
        branchId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");

        location = new InventoryLocation();
        location.setId(locationId);
        location.setBranchId(branchId);
        location.setCode("KHO-1001");
        location.setName("Kho Tổng Tân Cương");
        location.setType(InventoryLocationType.WAREHOUSE);
        location.setStatus(InventoryStatus.ACTIVE);
        location.setIsDeleted(false);
    }

    @Test
    @DisplayName("findAll - Success with search query and filters")
    void findAll_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<InventoryLocation> page = new PageImpl<>(List.of(location), pageable, 1);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<InventoryLocationResponse> response = service.findAll(
                branchId, "Tân Cương", InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE, pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Kho Tổng Tân Cương");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("lookup - Success with list of IDs")
    void lookup_WithIds_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        IdNameView view = mock(IdNameView.class);
        when(view.getId()).thenReturn(locationId);
        when(view.getName()).thenReturn("Kho Tổng Tân Cương");
        when(repository.findLookupByIds(List.of(locationId))).thenReturn(List.of(view));

        PageResponse<LookupResponse> response = service.lookup(
                null, null, null, List.of(locationId), pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Kho Tổng Tân Cương");
    }

    @Test
    @DisplayName("lookup - Success with keyword search")
    void lookup_WithSearch_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        IdNameView view = mock(IdNameView.class);
        when(view.getId()).thenReturn(locationId);
        when(view.getName()).thenReturn("Kho Tổng Tân Cương");
        Page<IdNameView> viewPage = new PageImpl<>(List.of(view), pageable, 1);
        when(repository.findLookup(eq("Kho"), eq(InventoryLocationType.WAREHOUSE), eq(InventoryStatus.ACTIVE), eq(pageable)))
                .thenReturn(viewPage);

        PageResponse<LookupResponse> response = service.lookup(
                "Kho", InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE, Collections.emptyList(), pageable);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Kho Tổng Tân Cương");
    }

    @Test
    @DisplayName("findById - Success when location exists")
    void findById_Success() {
        when(repository.findById(locationId)).thenReturn(Optional.of(location));

        InventoryLocationResponse response = service.findById(locationId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(locationId);
        assertThat(response.code()).isEqualTo("KHO-1001");
        assertThat(response.name()).isEqualTo("Kho Tổng Tân Cương");
    }

    @Test
    @DisplayName("findById - Throws ResourceNotFoundException when id not found")
    void findById_NotFound() {
        UUID nonExistentId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory location");
    }

    @Test
    @DisplayName("create - Success with generated code and no parent")
    void create_Success() {
        InventoryLocationRequest request = new InventoryLocationRequest(
                null, "Kho Lưu Trữ", InventoryLocationType.STORAGE, InventoryStatus.ACTIVE);

        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(InventoryLocation.class))).thenAnswer(invocation -> {
            InventoryLocation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InventoryLocationResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Kho Lưu Trữ");
        assertThat(response.type()).isEqualTo(InventoryLocationType.STORAGE);
        assertThat(response.code()).contains("KLT-");
        verify(repository).save(any(InventoryLocation.class));
    }

    @Test
    @DisplayName("create - Throws BadRequestException when parent location is self")
    void create_SelfParent_ThrowsBadRequestException() {
        // Resolve parent directly check
        UUID sameId = UUID.randomUUID();
        InventoryLocationRequest request = new InventoryLocationRequest(
                sameId, "Kho Con", InventoryLocationType.STORAGE, InventoryStatus.ACTIVE);
        when(repository.findById(sameId)).thenReturn(Optional.of(location));

        // When parent is location, and current location is also sameId in resolveParent
        assertThatThrownBy(() -> service.update(sameId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    @DisplayName("update - Success when updating existing location")
    void update_Success() {
        InventoryLocationRequest request = new InventoryLocationRequest(
                null, "Kho Tổng Tân Cương - Đổi Tên", InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
        when(repository.findById(locationId)).thenReturn(Optional.of(location));
        when(repository.save(any(InventoryLocation.class))).thenReturn(location);

        InventoryLocationResponse response = service.update(locationId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Kho Tổng Tân Cương - Đổi Tên");
        verify(repository).save(location);
    }

    @Test
    @DisplayName("updateStatus - Success changing status to INACTIVE")
    void updateStatus_Success() {
        when(repository.findById(locationId)).thenReturn(Optional.of(location));
        when(repository.save(any(InventoryLocation.class))).thenReturn(location);

        InventoryLocationResponse response = service.updateStatus(locationId, InventoryStatus.INACTIVE);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InventoryStatus.INACTIVE);
        verify(repository).save(location);
    }

    @Test
    @DisplayName("findDefaultWarehouse - Success by branchId")
    void findDefaultWarehouse_ByBranch_Success() {
        when(repository.findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(location));

        InventoryLocationResponse response = service.findDefaultWarehouse(branchId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(locationId);
        assertThat(response.type()).isEqualTo(InventoryLocationType.WAREHOUSE);
    }

    @Test
    @DisplayName("findDefaultWarehouse - Fallback to global active warehouse")
    void findDefaultWarehouse_FallbackGlobal_Success() {
        when(repository.findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.of(location));

        InventoryLocationResponse response = service.findDefaultWarehouse(branchId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(locationId);
    }

    @Test
    @DisplayName("findDefaultWarehouse - Throws ResourceNotFoundException when no warehouse exists")
    void findDefaultWarehouse_NotFound_ThrowsException() {
        when(repository.findFirstByBranchIdAndTypeAndStatus(branchId, InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTypeAndStatus(InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.findFirstByType(InventoryLocationType.WAREHOUSE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDefaultWarehouse(branchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No WAREHOUSE location found at all");
    }
}

