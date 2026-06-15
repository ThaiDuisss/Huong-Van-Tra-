package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findAllByIsDeletedFalse();

    List<CustomerAddress> findAllByCustomerIdAndIsDeletedFalse(UUID customerId);

    Optional<CustomerAddress> findByIdAndIsDeletedFalse(UUID id);

    @Modifying
    @Query("update CustomerAddress address set address.isDefault = false where address.customer.id = :customerId and address.id <> :addressId and address.isDeleted = false")
    void clearDefaultForCustomer(@Param("customerId") UUID customerId, @Param("addressId") UUID addressId);
}
