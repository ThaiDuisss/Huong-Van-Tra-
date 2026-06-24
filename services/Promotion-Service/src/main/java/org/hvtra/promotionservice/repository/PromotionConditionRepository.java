package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.PromotionCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionConditionRepository
        extends JpaRepository<PromotionCondition, UUID> {

}
