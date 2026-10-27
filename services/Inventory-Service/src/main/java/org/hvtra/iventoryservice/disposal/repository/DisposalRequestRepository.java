package org.hvtra.iventoryservice.disposal.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.disposal.DisposalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisposalRequestRepository
        extends JpaRepository<DisposalRequest, UUID>, JpaSpecificationExecutor<DisposalRequest> {

    boolean existsByRequestCode(String requestCode);

    @Query("""
            select distinct request
            from DisposalRequest request
            join fetch request.sourceInspection inspection
            left join fetch request.lines lines
            left join fetch lines.inventoryItem
            where request.id = :id
              and request.isDeleted = false
            """)
    Optional<DisposalRequest> findDetailById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct request
            from DisposalRequest request
            join fetch request.sourceInspection inspection
            left join fetch request.lines lines
            left join fetch lines.inventoryItem
            where request.id = :id
              and request.isDeleted = false
            """)
    Optional<DisposalRequest> findDetailByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select distinct request
            from DisposalRequest request
            join fetch request.sourceInspection inspection
            left join fetch request.lines lines
            left join fetch lines.inventoryItem
            where inspection.id = :inspectionId
              and request.isDeleted = false
            """)
    Optional<DisposalRequest> findBySourceInspectionId(@Param("inspectionId") UUID inspectionId);
}
