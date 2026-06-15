package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerTierRepository extends JpaRepository<CustomerTier, UUID> {
    List<CustomerTier> findAllByIsDeletedFalse();

    Optional<CustomerTier> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
