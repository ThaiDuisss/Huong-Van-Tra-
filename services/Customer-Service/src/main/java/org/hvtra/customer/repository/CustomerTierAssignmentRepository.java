package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
