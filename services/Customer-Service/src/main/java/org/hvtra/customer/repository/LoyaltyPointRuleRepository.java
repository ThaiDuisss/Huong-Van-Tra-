package org.hvtra.customer.repository;

import org.hvtra.customer.model.entity.LoyaltyPointRule;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoyaltyPointRuleRepository extends JpaRepository<LoyaltyPointRule, UUID> {
    List<LoyaltyPointRule> findAllByIsDeletedFalse();

    Optional<LoyaltyPointRule> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByCodeAndIsDeletedFalse(String code);

    boolean existsByCodeAndIdNotAndIsDeletedFalse(String code, UUID id);

    @Query("""
            select pointRule
            from LoyaltyPointRule pointRule
            where pointRule.isDeleted = false
              and pointRule.status = :status
              and pointRule.effectiveFrom <= :effectiveAt
              and (pointRule.effectiveTo is null or pointRule.effectiveTo > :effectiveAt)
              and pointRule.minOrderAmount <= :orderAmount
              and (
                    (:tierId is null and pointRule.tier is null)
                    or (:tierId is not null and (pointRule.tier.id = :tierId or pointRule.tier is null))
                  )
            order by
              case when pointRule.tier is null then 1 else 0 end,
              pointRule.priority asc,
              pointRule.effectiveFrom desc
            """)
    List<LoyaltyPointRule> findApplicableRules(
            @Param("tierId") UUID tierId,
            @Param("orderAmount") BigDecimal orderAmount,
            @Param("effectiveAt") Instant effectiveAt,
            @Param("status") LoyaltyPointRuleStatus status,
            Pageable pageable
    );
}
