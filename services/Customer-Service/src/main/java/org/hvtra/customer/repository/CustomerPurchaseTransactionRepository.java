package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerPurchaseTransactionRepository extends JpaRepository<CustomerPurchaseTransaction, UUID> {
    Page<CustomerPurchaseTransaction> findByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType transactionType, UUID referenceId);
}
