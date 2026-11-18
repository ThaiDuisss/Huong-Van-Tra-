package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.LoyaltyTransaction;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    Page<LoyaltyTransaction> findByCustomer_IdAndIsDeletedFalse(UUID customerId, Pageable pageable);

    Optional<LoyaltyTransaction> findByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
            LoyaltyTransactionType type,
            LoyaltyReferenceType referenceType,
            UUID referenceId
    );

    boolean existsByTypeAndReferenceTypeAndReferenceIdAndIsDeletedFalse(
            LoyaltyTransactionType type,
            LoyaltyReferenceType referenceType,
            UUID referenceId
    );

    /** Tổng điểm (có dấu) của một loại giao dịch thuộc một đơn, ví dụ tổng điểm đã trừ lại do hoàn tiền. */
    @Query("""
            select coalesce(sum(t.points), 0) from LoyaltyTransaction t
            where t.orderId = :orderId and t.type = :type and t.isDeleted = false
            """)
    long sumPointsByOrderAndType(@Param("orderId") UUID orderId, @Param("type") LoyaltyTransactionType type);
}
