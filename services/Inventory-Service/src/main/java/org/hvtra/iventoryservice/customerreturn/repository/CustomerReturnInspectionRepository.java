package org.hvtra.iventoryservice.customerreturn.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse;
import org.hvtra.iventoryservice.domain.entity.customerreturn.CustomerReturnInspection;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CustomerReturnInspectionRepository
        extends JpaRepository<CustomerReturnInspection, UUID>, JpaSpecificationExecutor<CustomerReturnInspection> {

    boolean existsByInspectionCode(String inspectionCode);

    Optional<CustomerReturnInspection> findByCommandIdAndIsDeletedFalse(UUID commandId);

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            left join fetch lines.inventoryItem
            left join fetch lines.destinationLocation
            where inspection.id = :id
              and inspection.isDeleted = false
            """)
    Optional<CustomerReturnInspection> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            left join fetch lines.inventoryItem
            left join fetch lines.destinationLocation
            where inspection.id in :ids
              and inspection.isDeleted = false
            """)
    List<CustomerReturnInspection> findAllByIdInWithLines(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            left join fetch lines.inventoryItem
            left join fetch lines.destinationLocation
            where inspection.id = :id
              and inspection.isDeleted = false
            """)
    Optional<CustomerReturnInspection> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            where inspection.returnId = :returnId
              and inspection.isDeleted = false
            order by inspection.createdAt asc, inspection.id asc
            """)
    List<CustomerReturnInspection> findByReturnIdForUpdate(@Param("returnId") UUID returnId);

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            where inspection.returnId = :returnId
              and inspection.isDeleted = false
              and inspection.status = :status
            """)
    List<CustomerReturnInspection> findByReturnIdAndStatus(
            @Param("returnId") UUID returnId,
            @Param("status") CustomerReturnInspectionStatus status);

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            where inspection.returnId in :returnIds
              and inspection.isDeleted = false
              and inspection.status = :status
            """)
    List<CustomerReturnInspection> findByReturnIdInAndStatus(
            @Param("returnIds") Collection<UUID> returnIds,
            @Param("status") CustomerReturnInspectionStatus status
    );

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            left join fetch lines.destinationLocation
            where inspection.returnId = :returnId
              and inspection.isDeleted = false
            order by inspection.createdAt desc, inspection.id desc
            """)
    List<CustomerReturnInspection> findQcProgressByReturnId(@Param("returnId") UUID returnId);

    @Query("""
            select distinct inspection
            from CustomerReturnInspection inspection
            left join fetch inspection.lines lines
            left join fetch lines.destinationLocation
            where inspection.returnId in :returnIds
              and inspection.isDeleted = false
            order by inspection.createdAt desc, inspection.id desc
            """)
    List<CustomerReturnInspection> findQcProgressByReturnIds(
            @Param("returnIds") Collection<UUID> returnIds
    );

    @Query("""
            select new org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse(
                lines.result,
                coalesce(sum(lines.quantity), 0)
            )
            from CustomerReturnInspection inspection
            join inspection.lines lines
            where inspection.returnId in :returnIds
              and inspection.isDeleted = false
              and inspection.status = :status
              and lines.isDeleted = false
              and lines.posted = true
            group by lines.result
            """)
    List<CustomerReturnQcDispositionReportResponse> sumDispositionByReturnIds(
            @Param("returnIds") Collection<UUID> returnIds,
            @Param("status") CustomerReturnInspectionStatus status
    );

    default List<CustomerReturnQcDispositionReportResponse> sumDispositionByReturnIds(Collection<UUID> returnIds) {
        return sumDispositionByReturnIds(returnIds, CustomerReturnInspectionStatus.POSTED);
    }
}
