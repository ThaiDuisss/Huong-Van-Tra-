package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndIsDeletedFalse(UUID id);

    Page<Coupon> findAllByIsDeletedFalse(Pageable pageable);

    Page<Coupon> findAllByPromotionIdAndIsDeletedFalse(UUID promotionId, Pageable pageable);

    boolean existsByCodeAndIsDeletedFalse(String code);

    Optional<Coupon> findByCodeAndIsDeletedFalse(String code);

    Page<Coupon> findAllByStatusAndIsDeletedFalse(CouponStatus status, Pageable pageable);
}