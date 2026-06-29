package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.hvtra.promotionservice.enums.coupon.CouponUsageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}