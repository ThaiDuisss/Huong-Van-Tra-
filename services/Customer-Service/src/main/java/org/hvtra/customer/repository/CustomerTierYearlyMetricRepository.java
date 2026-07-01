package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerTierYearlyMetricRepository extends JpaRepository<CustomerTierYearlyMetric, UUID> {
    List<CustomerTierYearlyMetric> findAllByIsDeletedFalse();

    List<CustomerTierYearlyMetric> findAllByTierYearAndIsDeletedFalse(Integer tierYear);

    Optional<CustomerTierYearlyMetric> findByIdAndIsDeletedFalse(UUID id);

    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYearAndIsDeletedFalse(UUID customerId, Integer tierYear);
}
