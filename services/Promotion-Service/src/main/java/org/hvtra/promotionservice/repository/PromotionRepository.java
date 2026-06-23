package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

}
