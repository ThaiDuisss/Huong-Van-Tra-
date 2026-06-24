package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponRepository
        extends JpaRepository<Coupon, UUID> {

}