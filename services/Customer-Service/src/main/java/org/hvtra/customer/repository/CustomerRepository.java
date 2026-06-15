package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findAllByIsDeletedFalse();

    Optional<Customer> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCustomerCode(String customerCode);

    boolean existsByCustomerCodeAndIdNot(String customerCode, UUID id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
