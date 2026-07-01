package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerTierYearlyMetricRepository extends JpaRepository<CustomerTierYearlyMetric, UUID> {
    List<CustomerTierYearlyMetric> findAllByIsDeletedFalse();

    List<CustomerTierYearlyMetric> findAllByTierYearAndIsDeletedFalse(Integer tierYear);

    Optional<CustomerTierYearlyMetric> findByIdAndIsDeletedFalse(UUID id);

    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYearAndIsDeletedFalse(UUID customerId, Integer tierYear);

    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYear(UUID customerId, Integer tierYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CustomerTierYearlyMetric m where m.customerId = :customerId and m.tierYear = :tierYear and m.isDeleted = false")
    Optional<CustomerTierYearlyMetric> findByCustomerIdAndTierYearForUpdate(UUID customerId, Integer tierYear);

    List<CustomerTierYearlyMetric> findByTierYear(Integer tierYear);

    Page<CustomerTierYearlyMetric> findByTierYear(Integer tierYear, Pageable pageable);
}
