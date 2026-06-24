package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.enums.CustomerTierStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerTierRepository extends JpaRepository<CustomerTier, UUID> {
    List<CustomerTier> findAllByIsDeletedFalse();

    Optional<CustomerTier> findByIdAndIsDeletedFalse(UUID id);

    Optional<CustomerTier> findFirstByMinSpendingLessThanEqualAndStatusAndIsDeletedFalseOrderByMinSpendingDesc(
            BigDecimal totalSpent,
            CustomerTierStatus status
    );

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
