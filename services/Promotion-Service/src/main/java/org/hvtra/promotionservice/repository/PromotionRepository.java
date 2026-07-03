package org.hvtra.promotionservice.repository;

import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.event.EventStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    Optional<Promotion> findByIdAndIsDeletedFalse(UUID id);

    Page<Promotion> findAllByIsDeletedFalse(Pageable pageable);

    Page<Promotion> findAllByEventIdAndIsDeletedFalse(UUID eventId, Pageable pageable);

    default List<Promotion> findActivePromotionsForPreview(
            PromotionStatus promotionStatus,
            EventStatus eventStatus,
            LocalDateTime now
    ) {
        List<Promotion> promotions = findActivePromotionsWithConditionsForPreview(
                promotionStatus,
                eventStatus,
                now
        );

        if (!promotions.isEmpty()) {
            findPromotionsWithBenefitsByIdIn(
                    promotions.stream()
                            .map(Promotion::getId)
                            .toList()
            );
        }

        return promotions;
    }

    @Query("""
            select distinct p
            from Promotion p
            left join fetch p.event e
            left join fetch p.conditions
            where p.status = :promotionStatus
              and e.status = :eventStatus
              and p.startAt <= :now
              and (p.endAt is null or p.endAt >= :now)
            order by p.priority desc
            """)
    List<Promotion> findActivePromotionsWithConditionsForPreview(
            @Param("promotionStatus") PromotionStatus promotionStatus,
            @Param("eventStatus") EventStatus eventStatus,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select distinct p
            from Promotion p
            left join fetch p.benefits
            where p.id in :ids
            """)
    List<Promotion> findPromotionsWithBenefitsByIdIn(@Param("ids") List<UUID> ids);
}
