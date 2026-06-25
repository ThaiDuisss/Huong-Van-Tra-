package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.entity.PromotionCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromotionConditionRepository
        extends JpaRepository<PromotionCondition, UUID> {

    Page<PromotionCondition> findByPromotionAndIsDeletedFalse(
            Promotion promotion,
            Pageable pageable
    );

    Optional<PromotionCondition> findByIdAndPromotionIdAndIsDeletedFalse(
            UUID conditionId,
            UUID promotionId
    );

}
