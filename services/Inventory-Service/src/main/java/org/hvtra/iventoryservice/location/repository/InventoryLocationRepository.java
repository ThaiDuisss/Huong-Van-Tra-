package org.hvtra.iventoryservice.location.repository;

import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.location.repository.projection.IdNameView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLocationRepository
        extends JpaRepository<InventoryLocation, UUID>, JpaSpecificationExecutor<InventoryLocation> {
    boolean existsByCode(String code);
    boolean existsByBranchIdAndCode(UUID branchId, String code);
    boolean existsByBranchIdAndCodeAndIdNot(UUID branchId, String code, UUID id);
    Optional<InventoryLocation> findFirstByType(InventoryLocationType type);
    List<InventoryLocation> findByCodeIn(Collection<String> codes);
    Optional<InventoryLocation> findFirstByTypeAndStatus(
            InventoryLocationType type,
            InventoryStatus status
    );
    Optional<InventoryLocation> findFirstByTypeAndStatusOrderByCreatedAtAsc(
            InventoryLocationType type,
            InventoryStatus status
    );
    Optional<InventoryLocation> findFirstByBranchIdAndTypeAndStatus(
            UUID branchId,
            InventoryLocationType type,
            InventoryStatus status
    );

    @Query("""
            SELECT DISTINCT location.branchId
            FROM InventoryLocation location
            WHERE location.status = :status
              AND location.isDeleted = false
            """)
    List<UUID> findDistinctBranchIdsByStatus(@Param("status") InventoryStatus status);

    /** Dropdown chọn vị trí/kho — chỉ id + name, tìm theo tên hoặc mã, lọc theo loại/trạng thái nếu có. */
    @Query("""
            SELECT l.id AS id, l.name AS name
            FROM InventoryLocation l
            WHERE l.isDeleted = false
              AND (:type IS NULL OR l.type = :type)
              AND (:status IS NULL OR l.status = :status)
              AND (:search IS NULL
                   OR LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(l.code) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<IdNameView> findLookup(
            @Param("search") String search,
            @Param("type") InventoryLocationType type,
            @Param("status") InventoryStatus status,
            Pageable pageable);

    /** Lấy tên theo id cho các giá trị đã chọn sẵn trong dropdown. */
    @Query("SELECT l.id AS id, l.name AS name FROM InventoryLocation l WHERE l.id IN :ids")
    List<IdNameView> findLookupByIds(@Param("ids") Collection<UUID> ids);

    /** Các kệ (hoặc vị trí con) thuộc một kho, dùng khi phiếu nhập kệ không chỉ định kệ. */
    List<InventoryLocation> findByParentLocation_IdAndTypeAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
            UUID parentLocationId,
            InventoryLocationType type,
            InventoryStatus status
    );

    Page<InventoryLocation> findByNameContainingIgnoreCaseAndTypeAndIsDeletedFalse(
            String name,
            InventoryLocationType type,
            Pageable pageable
    );
}
