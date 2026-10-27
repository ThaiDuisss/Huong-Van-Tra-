package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerTierAssignmentRepository extends JpaRepository<CustomerTierAssignment, UUID> {
    List<CustomerTierAssignment> findAllByIsDeletedFalse();

    List<CustomerTierAssignment> findAllByCustomerIdAndIsDeletedFalse(UUID customerId);

    List<CustomerTierAssignment> findAllByCustomerIdAndStatusAndIsDeletedFalse(
            UUID customerId,
            CustomerTierAssignmentStatus status
    );

    List<CustomerTierAssignment> findAllByQualificationYearAndIsDeletedFalse(Integer qualificationYear);

    List<CustomerTierAssignment> findAllByEffectiveUntilBeforeAndStatusAndIsDeletedFalse(
            LocalDateTime effectiveUntil,
            CustomerTierAssignmentStatus status
    );

    Optional<CustomerTierAssignment> findByIdAndIsDeletedFalse(UUID id);

    Optional<CustomerTierAssignment> findByCustomerIdAndStatus(UUID customerId, CustomerTierAssignmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerTierAssignment a where a.customer.id = :customerId and a.status = org.hvtra.customer.model.enums.CustomerTierAssignmentStatus.ACTIVE and a.isDeleted = false")
    Optional<CustomerTierAssignment> findActiveByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    List<CustomerTierAssignment> findByCustomerIdOrderByEffectiveFromDesc(UUID customerId);

    List<CustomerTierAssignment> findByStatusAndEffectiveUntilLessThanEqual(
            CustomerTierAssignmentStatus status,
            LocalDateTime effectiveUntil
    );

    boolean existsByTierIdAndIsDeletedFalse(UUID tierId);
}
