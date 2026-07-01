package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPurchaseTransactionRepository extends JpaRepository<CustomerPurchaseTransaction, UUID> {
    List<CustomerPurchaseTransaction> findAllByIsDeletedFalse();

    List<CustomerPurchaseTransaction> findAllByCustomerIdAndIsDeletedFalse(UUID customerId);

    List<CustomerPurchaseTransaction> findAllByCustomerIdAndOccurredAtBetweenAndIsDeletedFalse(
            UUID customerId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<CustomerPurchaseTransaction> findByIdAndIsDeletedFalse(UUID id);

    Optional<CustomerPurchaseTransaction> findBySourceEventIdAndIsDeletedFalse(String sourceEventId);

    boolean existsBySourceEventIdAndIsDeletedFalse(String sourceEventId);
}
