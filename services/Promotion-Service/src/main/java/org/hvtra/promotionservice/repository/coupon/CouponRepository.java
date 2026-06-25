package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository
        extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndIsDeletedFalse(UUID couponId);

    boolean existsByCodeAndIdNotAndIsDeletedFalse(String code, UUID id);

    boolean existsByCodeAndIsDeletedFalse(String code);

    Page<Coupon> findAllByPromotionIdAndIsDeletedFalse(UUID promotionId, Pageable pageable);

}