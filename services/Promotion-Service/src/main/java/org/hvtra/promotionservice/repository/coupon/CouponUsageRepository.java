package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponUsageRepository
        extends JpaRepository<CouponUsage, UUID> {

    Optional<CouponUsage> findByIdAndIsDeletedFalse(UUID usageId);

    boolean existsByCouponIdAndOrderIdAndIsDeletedFalse(UUID couponId, UUID orderId);

    Page<CouponUsage> findAllByCouponIdAndIsDeletedFalse(UUID couponId, Pageable pageable);

    Page<CouponUsage> findAllByCustomerIdAndIsDeletedFalse(UUID customerId, Pageable pageable);
}