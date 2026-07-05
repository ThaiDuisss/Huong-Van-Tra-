package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.enums.CustomerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByIdAndIsDeletedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id and c.isDeleted = false")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    Page<Customer> findByStatusAndIsDeletedFalse(CustomerStatus status, Pageable pageable);

    long countByIsDeletedFalse();

    long countByTierIsNullAndIsDeletedFalse();

    long countByTier_IdAndIsDeletedFalse(UUID tierId);

    boolean existsByCustomerCode(String customerCode);

    boolean existsByCustomerCodeAndIdNot(String customerCode, UUID id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
