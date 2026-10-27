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
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
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

    // Các truy vấn dưới đây phục vụ nhập hàng loạt: kiểm tra trùng trên toàn bộ bảng
    // (kể cả bản ghi đã xóa mềm) vì ràng buộc UNIQUE của DB không phân biệt is_deleted.
    @Query("select c.phone from Customer c where c.phone in :phones")
    List<String> findExistingPhones(@Param("phones") Collection<String> phones);

    @Query("select lower(c.email) from Customer c where lower(c.email) in :emails")
    List<String> findExistingEmailsLowerCase(@Param("emails") Collection<String> emails);

    @Query("select lower(c.customerCode) from Customer c where lower(c.customerCode) in :codes")
    List<String> findExistingCustomerCodesLowerCase(@Param("codes") Collection<String> codes);


}
