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

    List<CustomerTier> findAllByStatusAndIsDeletedFalseOrderByMinSpendingDesc(CustomerTierStatus status);

    /** Các hạng đang áp dụng, từ thấp đến cao. */
    List<CustomerTier> findAllByStatusAndIsDeletedFalseOrderByMinSpendingAscMinOrderCountAsc(CustomerTierStatus status);

    Optional<CustomerTier> findByIdAndIsDeletedFalse(UUID id);

    /** Hạng cao nhất mà khách đạt cả ngưỡng chi tiêu lẫn số đơn. */
    default Optional<CustomerTier> findHighestQualifiedTier(BigDecimal totalSpent, Integer totalOrderCount) {
        return findFirstByStatusAndIsDeletedFalseAndMinSpendingLessThanEqualAndMinOrderCountLessThanEqualOrderByMinSpendingDescMinOrderCountDesc(
                CustomerTierStatus.ACTIVE,
                totalSpent,
                totalOrderCount
        );
    }

    Optional<CustomerTier> findFirstByStatusAndIsDeletedFalseAndMinSpendingLessThanEqualAndMinOrderCountLessThanEqualOrderByMinSpendingDescMinOrderCountDesc(
            CustomerTierStatus status,
            BigDecimal totalSpent,
            Integer totalOrderCount
    );

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
