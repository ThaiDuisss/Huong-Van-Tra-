package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {
    List<LoyaltyTransaction> findAllByIsDeletedFalse();

    List<LoyaltyTransaction> findAllByLoyaltyAccountIdAndIsDeletedFalse(UUID loyaltyAccountId);

    Optional<LoyaltyTransaction> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
            LoyaltyTransactionType type,
            LoyaltyReferenceType referenceType,
            UUID referenceId
    );
}
