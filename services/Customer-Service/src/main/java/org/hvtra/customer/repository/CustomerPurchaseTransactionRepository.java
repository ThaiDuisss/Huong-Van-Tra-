package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPurchaseTransactionRepository extends JpaRepository<CustomerPurchaseTransaction, UUID> {
    List<CustomerPurchaseTransaction> findAllByIsDeletedFalse();

    List<CustomerPurchaseTransaction> findAllByCustomerIdAndIsDeletedFalse(UUID customerId);

    List<CustomerPurchaseTransaction> findByCustomerIdOrderByOccurredAtDesc(UUID customerId);

    Page<CustomerPurchaseTransaction> findByCustomerId(UUID customerId, Pageable pageable);

    List<CustomerPurchaseTransaction> findAllByCustomerIdAndOccurredAtBetweenAndIsDeletedFalse(
            UUID customerId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<CustomerPurchaseTransaction> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType transactionType, UUID referenceId);

    List<CustomerPurchaseTransaction> findByCustomerIdAndOccurredAtBetween(
            UUID customerId,
            LocalDateTime from,
            LocalDateTime to
    );
}
