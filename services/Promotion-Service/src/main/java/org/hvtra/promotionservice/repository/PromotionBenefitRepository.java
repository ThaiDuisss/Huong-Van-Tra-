package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.PromotionBenefit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionBenefitRepository
        extends JpaRepository<PromotionBenefit, UUID> {

}
