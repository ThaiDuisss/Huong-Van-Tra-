package org.hvtra.promotionservice.repository.promotion;

import org.hvtra.promotionservice.entity.PromotionTarget;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromotionTargetRepository extends JpaRepository<PromotionTarget, UUID> {

    Optional<PromotionTarget> findByIdAndPromotionIdAndIsDeletedFalse(
            UUID id,
            UUID promotionId
    );

    Page<PromotionTarget> findAllByPromotionIdAndIsDeletedFalse(
            UUID promotionId,
            Pageable pageable
    );

    boolean existsByPromotionIdAndTargetTypeAndTargetIdAndIsDeletedFalse(
            UUID promotionId,
            PromotionTargetType targetType,
            UUID targetId
    );

    boolean existsByPromotionIdAndTargetTypeAndTargetIdAndIdNotAndIsDeletedFalse(
            UUID promotionId,
            PromotionTargetType targetType,
            UUID targetId,
            UUID id
    );
}
