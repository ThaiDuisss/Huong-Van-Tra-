package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromotionBenefitRepository
        extends JpaRepository<PromotionBenefit, UUID> {

    Page<PromotionBenefit> findAllByPromotionIdAndIsDeletedFalse(
            UUID promotionId,
            Pageable pageable
    );

    Optional<PromotionBenefit> findByIdAndPromotionIdAndIsDeletedFalse(
            UUID benefitId,
            UUID promotionId
    );

}
