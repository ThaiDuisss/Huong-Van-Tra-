package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.CustomerStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hvtra.customer.util.AmountUtils.nonNegative;
import static org.hvtra.customer.util.AmountUtils.zeroIfNull;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "email", unique = true, length = 150)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private CustomerTier tier;

    @Column(name = "last_purchase_date")
    private LocalDateTime lastPurchaseDate;

    @Column(name = "total_spent", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "total_order_count", nullable = false)
    private Integer totalOrderCount = 0;

    @Column(name = "point_balance", nullable = false)
    private Integer pointBalance = 0;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    /** Ghi nhận một đơn hoàn tất: cộng chi tiêu, cộng một đơn, cập nhật lần mua gần nhất. */
    public void addPurchase(BigDecimal amount, LocalDateTime purchasedAt) {
        this.totalSpent = nonNegative(zeroIfNull(this.totalSpent).add(zeroIfNull(amount)));
        this.totalOrderCount = zeroIfNull(this.totalOrderCount) + 1;
        if (purchasedAt != null && (lastPurchaseDate == null || purchasedAt.isAfter(lastPurchaseDate))) {
            this.lastPurchaseDate = purchasedAt;
        }
    }

    /** Ghi nhận hoàn tiền: trừ chi tiêu; hoàn toàn bộ thì trừ thêm một đơn. Không bao giờ âm. */
    public void applyRefund(BigDecimal refundAmount, boolean fullRefund) {
        this.totalSpent = nonNegative(zeroIfNull(this.totalSpent).subtract(zeroIfNull(refundAmount)));
        if (fullRefund) {
            this.totalOrderCount = Math.max(zeroIfNull(this.totalOrderCount) - 1, 0);
        }
    }

    /** Cộng (dương) hoặc trừ (âm) điểm và trả về số dư mới. Gọi sau khi đã kiểm tra số dư đủ. */
    public int changePoints(int delta) {
        int next = zeroIfNull(this.pointBalance) + delta;
        if (next < 0) {
            throw new IllegalStateException("Point balance cannot be negative");
        }
        this.pointBalance = next;
        return next;
    }
}
