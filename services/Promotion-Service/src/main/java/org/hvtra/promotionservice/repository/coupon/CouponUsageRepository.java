package org.hvtra.promotionservice.repository.coupon;

import org.hvtra.promotionservice.entity.coupon.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponUsageRepository
        extends JpaRepository<CouponUsage, UUID> {

}
