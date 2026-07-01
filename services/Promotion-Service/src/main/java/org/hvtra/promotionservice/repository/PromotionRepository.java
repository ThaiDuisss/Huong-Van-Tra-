package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    Optional<Promotion> findByIdAndIsDeletedFalse(UUID id);

    Page<Promotion> findAllByIsDeletedFalse(Pageable pageable);

    Page<Promotion> findAllByEventIdAndIsDeletedFalse(UUID eventId, Pageable pageable);

    Page<Promotion> findAllByStatusAndIsDeletedFalse(PromotionStatus status, Pageable pageable);

    Page<Promotion> findAllByTypeAndIsDeletedFalse(PromotionType type, Pageable pageable);

    Page<Promotion> findAllByStartAtLessThanEqualAndEndAtGreaterThanEqualAndStatusAndIsDeletedFalse(
            LocalDateTime startAt,
            LocalDateTime endAt,
            PromotionStatus status,
            Pageable pageable
    );
}
