package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {
    Optional<LoyaltyAccount> findByIdAndIsDeletedFalse(UUID id);

    Optional<LoyaltyAccount> findByCustomerIdAndIsDeletedFalse(UUID customerId);

    boolean existsByCustomerId(UUID customerId);
}
