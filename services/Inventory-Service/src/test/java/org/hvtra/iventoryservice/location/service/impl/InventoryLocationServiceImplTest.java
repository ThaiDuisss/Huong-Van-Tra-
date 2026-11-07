package org.hvtra.iventoryservice.location.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.location.dto.response.InventoryLocationHierarchyResponse;
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
import org.springframework.data.domain.Sort;
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
                null, "Kho Lưu Trữ", InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);

        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(InventoryLocation.class))).thenAnswer(invocation -> {
            InventoryLocation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        InventoryLocationResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Kho Lưu Trữ");
        assertThat(response.type()).isEqualTo(InventoryLocationType.WAREHOUSE);
        assertThat(response.code()).contains("KLT-");
        verify(repository).save(any(InventoryLocation.class));
    }

    @Test
    @DisplayName("create - Throws BadRequestException when parent location is self")
    void create_SelfParent_ThrowsBadRequestException() {
        // Resolve parent directly check
        UUID sameId = UUID.randomUUID();
        InventoryLocationRequest request = new InventoryLocationRequest(
                sameId, "Kho Con", InventoryLocationType.WAREHOUSE, InventoryStatus.ACTIVE);
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

    @Test
    @DisplayName("getHierarchy - Success with depth-first ordering and level computation")
    void getHierarchy_Success_DepthFirstOrder() {
        InventoryLocation root1 = new InventoryLocation();
        root1.setId(UUID.randomUUID());
        root1.setBranchId(branchId);
        root1.setCode("KHO-01");
        root1.setName("Kho Tổng");
        root1.setType(InventoryLocationType.WAREHOUSE);
        root1.setStatus(InventoryStatus.ACTIVE);
        root1.setIsDeleted(false);

        InventoryLocation child1 = new InventoryLocation();
        child1.setId(UUID.randomUUID());
        child1.setBranchId(branchId);
        child1.setParentLocation(root1);
        child1.setCode("KV-01");
        child1.setName("Khu Vực A");
        child1.setType(InventoryLocationType.WAREHOUSE);
        child1.setStatus(InventoryStatus.ACTIVE);
        child1.setIsDeleted(false);

        InventoryLocation grandchild1 = new InventoryLocation();
        grandchild1.setId(UUID.randomUUID());
        grandchild1.setBranchId(branchId);
        grandchild1.setParentLocation(child1);
        grandchild1.setCode("KE-01");
        grandchild1.setName("Kệ A1");
        grandchild1.setType(InventoryLocationType.SHELF);
        grandchild1.setStatus(InventoryStatus.ACTIVE);
        grandchild1.setIsDeleted(false);

        InventoryLocation child2 = new InventoryLocation();
        child2.setId(UUID.randomUUID());
        child2.setBranchId(branchId);
        child2.setParentLocation(root1);
        child2.setCode("KV-02");
        child2.setName("Khu Vực B");
        child2.setType(InventoryLocationType.WAREHOUSE);
        child2.setStatus(InventoryStatus.ACTIVE);
        child2.setIsDeleted(false);

        InventoryLocation root2 = new InventoryLocation();
        root2.setId(UUID.randomUUID());
        root2.setBranchId(branchId);
        root2.setCode("HL-01");
        root2.setName("Kho Hàng Hỏng");
        root2.setType(InventoryLocationType.DAMAGED);
        root2.setStatus(InventoryStatus.ACTIVE);
        root2.setIsDeleted(false);

        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(root1, child1, grandchild1, child2, root2));

        List<InventoryLocationHierarchyResponse> result = service.getAll(branchId, null, null, null);

        assertThat(result).hasSize(5);

        // Root 1
        assertThat(result.get(0).id()).isEqualTo(root1.getId());
        assertThat(result.get(0).level()).isEqualTo(0);
        assertThat(result.get(0).tier()).isEqualTo(1);
        assertThat(result.get(0).hasChildren()).isTrue();
        assertThat(result.get(0).childrenCount()).isEqualTo(2);
        assertThat(result.get(0).parentLocationId()).isNull();

        // Child 1
        assertThat(result.get(1).id()).isEqualTo(child1.getId());
        assertThat(result.get(1).level()).isEqualTo(1);
        assertThat(result.get(1).tier()).isEqualTo(2);
        assertThat(result.get(1).hasChildren()).isTrue();
        assertThat(result.get(1).childrenCount()).isEqualTo(1);
        assertThat(result.get(1).parentLocationId()).isEqualTo(root1.getId());
        assertThat(result.get(1).parentLocationName()).isEqualTo("Kho Tổng");

        // Grandchild 1
        assertThat(result.get(2).id()).isEqualTo(grandchild1.getId());
        assertThat(result.get(2).level()).isEqualTo(2);
        assertThat(result.get(2).tier()).isEqualTo(3);
        assertThat(result.get(2).hasChildren()).isFalse();
        assertThat(result.get(2).childrenCount()).isEqualTo(0);
        assertThat(result.get(2).parentLocationId()).isEqualTo(child1.getId());
        assertThat(result.get(2).parentLocationName()).isEqualTo("Khu Vực A");

        // Child 2
        assertThat(result.get(3).id()).isEqualTo(child2.getId());
        assertThat(result.get(3).level()).isEqualTo(1);
        assertThat(result.get(3).tier()).isEqualTo(2);
        assertThat(result.get(3).hasChildren()).isFalse();
        assertThat(result.get(3).childrenCount()).isEqualTo(0);
        assertThat(result.get(3).parentLocationId()).isEqualTo(root1.getId());

        // Root 2
        assertThat(result.get(4).id()).isEqualTo(root2.getId());
        assertThat(result.get(4).level()).isEqualTo(0);
        assertThat(result.get(4).tier()).isEqualTo(1);
        assertThat(result.get(4).hasChildren()).isFalse();
        assertThat(result.get(4).childrenCount()).isEqualTo(0);
        assertThat(result.get(4).parentLocationId()).isNull();
    }

    @Test
    @DisplayName("getAll - Empty result when no locations match criteria")
    void getAll_EmptyResult_ReturnsEmptyList() {
        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(Collections.emptyList());

        List<InventoryLocationHierarchyResponse> result = service.getAll(
                branchId, "NonExistentKeyword", InventoryLocationType.WAREHOUSE, InventoryStatus.INACTIVE);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getAll - Child returned without parent is treated as root level zero")
    void getAll_OrphanChild_TreatedAsRootLevelZero() {
        InventoryLocation parent = new InventoryLocation();
        parent.setId(UUID.randomUUID());
        parent.setCode("KHO-P");
        parent.setName("Parent Warehouse");

        InventoryLocation orphanChild = new InventoryLocation();
        orphanChild.setId(UUID.randomUUID());
        orphanChild.setBranchId(branchId);
        orphanChild.setParentLocation(parent);
        orphanChild.setCode("KV-01");
        orphanChild.setName("Khu Vực Con");
        orphanChild.setType(InventoryLocationType.WAREHOUSE);
        orphanChild.setStatus(InventoryStatus.ACTIVE);
        orphanChild.setIsDeleted(false);

        when(repository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(orphanChild));

        List<InventoryLocationHierarchyResponse> result = service.getAll(branchId, "Khu Vực Con", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(orphanChild.getId());
        assertThat(result.getFirst().level()).isEqualTo(0);
        assertThat(result.getFirst().tier()).isEqualTo(1);
        assertThat(result.getFirst().parentLocationId()).isEqualTo(parent.getId());
        assertThat(result.getFirst().parentLocationName()).isEqualTo("Parent Warehouse");
    }
}

