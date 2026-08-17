package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {

    Optional<CouponUsage> findByIdAndIsDeletedFalse(UUID id);

    Page<CouponUsage> findAllByIsDeletedFalse(Pageable pageable);

    Page<CouponUsage> findAllByCouponIdAndIsDeletedFalse(UUID couponId, Pageable pageable);

    Page<CouponUsage> findAllByOrderIdAndIsDeletedFalse(UUID orderId, Pageable pageable);

    Page<CouponUsage> findAllByCustomerIdAndIsDeletedFalse(UUID customerId, Pageable pageable);

    Page<CouponUsage> findAllByStatusAndIsDeletedFalse(CouponUsageStatus status, Pageable pageable);

    Optional<CouponUsage> findByReservationTokenAndIsDeletedFalse(String reservationToken);

    long countByCouponIdAndCustomerIdAndStatusInAndIsDeletedFalse(
            UUID couponId,
            UUID customerId,
            Collection<CouponUsageStatus> statuses
    );

    Optional<CouponUsage> findByCouponIdAndOrderIdAndStatusAndIsDeletedFalse(
            UUID couponId,
            UUID orderId,
            CouponUsageStatus status
    );

    @Query("""
            select u
            from CouponUsage u
            join u.coupon c
            where u.isDeleted = false
              and (:couponId is null or c.id = :couponId)
              and (:couponCode is null or lower(c.code) like lower(concat('%', :couponCode, '%')))
              and (:orderId is null or u.orderId = :orderId)
              and (:customerId is null or u.customerId = :customerId)
              and (:status is null or u.status = :status)
              and (:reservedFrom is null or u.reservedAt >= :reservedFrom)
              and (:reservedTo is null or u.reservedAt <= :reservedTo)
            """)
    Page<CouponUsage> searchCouponUsages(
            @Param("couponId") UUID couponId,
            @Param("couponCode") String couponCode,
            @Param("orderId") UUID orderId,
            @Param("customerId") UUID customerId,
            @Param("status") CouponUsageStatus status,
            @Param("reservedFrom") LocalDateTime reservedFrom,
            @Param("reservedTo") LocalDateTime reservedTo,
            Pageable pageable
    );
}
