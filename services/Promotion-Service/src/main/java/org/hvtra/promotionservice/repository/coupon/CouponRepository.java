package org.hvtra.promotionservice.repository.coupon;

import jakarta.persistence.LockModeType;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndIsDeletedFalse(UUID id);

    Page<Coupon> findAllByIsDeletedFalse(Pageable pageable);

    Page<Coupon> findAllByPromotionIdAndIsDeletedFalse(UUID promotionId, Pageable pageable);

    boolean existsByCodeAndIsDeletedFalse(String code);

    Optional<Coupon> findByCodeAndIsDeletedFalse(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Coupon c
            join fetch c.promotion
            where c.code = :code
              and c.isDeleted = false
            """)
    Optional<Coupon> findByCodeAndIsDeletedFalseForUpdate(@Param("code") String code);

    Page<Coupon> findAllByStatusAndIsDeletedFalse(CouponStatus status, Pageable pageable);

    @Query("""
            select c
            from Coupon c
            where c.isDeleted = false
              and c.promotion.id = :promotionId
              and c.status = :status
              and (c.expiresAt is null or c.expiresAt >= :now)
              and c.usedCount < c.usageLimit
              and (
                    c.scope = org.hvtra.promotionservice.enums.coupon.CouponScope.PUBLIC
                    or (:customerId is not null and c.scope = org.hvtra.promotionservice.enums.coupon.CouponScope.PERSONAL and c.customerId = :customerId)
                  )
              and (:couponCode is null or lower(c.code) = lower(:couponCode))
            order by
              case when c.scope = org.hvtra.promotionservice.enums.coupon.CouponScope.PERSONAL then 0 else 1 end,
              c.createdAt asc
            """)
    List<Coupon> findPreviewEligibleCoupons(
            @Param("promotionId") UUID promotionId,
            @Param("customerId") UUID customerId,
            @Param("couponCode") String couponCode,
            @Param("status") CouponStatus status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select c
            from Coupon c
            left join c.promotion p
            where c.isDeleted = false
              and (
                    :keyword is null
                    or lower(c.code) like lower(concat('%', :keyword, '%'))
                    or lower(c.snapshotPromotionName) like lower(concat('%', :keyword, '%'))
                    or lower(p.name) like lower(concat('%', :keyword, '%'))
                  )
              and (:promotionId is null or p.id = :promotionId)
              and (:status is null or c.status = :status)
              and (:scope is null or c.scope = :scope)
              and (:customerId is null or c.customerId = :customerId)
              and (:expiresFrom is null or c.expiresAt >= :expiresFrom)
              and (:expiresTo is null or c.expiresAt <= :expiresTo)
            """)
    Page<Coupon> searchCoupons(
            @Param("keyword") String keyword,
            @Param("promotionId") UUID promotionId,
            @Param("status") CouponStatus status,
            @Param("scope") CouponScope scope,
            @Param("customerId") UUID customerId,
            @Param("expiresFrom") LocalDateTime expiresFrom,
            @Param("expiresTo") LocalDateTime expiresTo,
            Pageable pageable
    );
}
