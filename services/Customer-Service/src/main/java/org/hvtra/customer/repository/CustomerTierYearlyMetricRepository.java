package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerTierYearlyMetricRepository extends JpaRepository<CustomerTierYearlyMetric, UUID> {
    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYear(UUID customerId, Integer tierYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CustomerTierYearlyMetric m where m.customerId = :customerId and m.tierYear = :tierYear and m.isDeleted = false")
    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYearForUpdate(
            @Param("customerId") UUID customerId,
            @Param("tierYear") Integer tierYear
    );
}
