package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAttemptStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inventory_count_lines",
        indexes = {
                @Index(name = "idx_count_lines_count_location", columnList = "inventory_count_id,location_id"),
                @Index(name = "idx_count_lines_item", columnList = "inventory_item_id"),
                @Index(name = "idx_count_lines_batch", columnList = "inventory_batch_id")
        }
)
@Getter
@Setter
public class InventoryCountLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_count_id", nullable = false)
    private InventoryCount inventoryCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_batch_id")
    private InventoryBatch inventoryBatch;

    @Column(name = "snapshot_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal snapshotQuantity = BigDecimal.ZERO;

    @Column(name = "movement_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal movementQuantity = BigDecimal.ZERO;

    @Column(name = "expected_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedQuantity = BigDecimal.ZERO;

    @Column(name = "snapshot_unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal snapshotUnitCost = BigDecimal.ZERO;

    @Column(name = "counted_quantity", precision = 18, scale = 2)
    private BigDecimal countedQuantity;

    @Column(name = "variance_quantity", precision = 18, scale = 2)
    private BigDecimal varianceQuantity;

    @Column(name = "variance_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal varianceValue = BigDecimal.ZERO;

    @Column(name = "good_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal goodQuantity = BigDecimal.ZERO;

    @Column(name = "damaged_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal damagedQuantity = BigDecimal.ZERO;

    @Column(name = "expired_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal expiredQuantity = BigDecimal.ZERO;

    @Column(name = "quarantined_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quarantinedQuantity = BigDecimal.ZERO;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "countLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNo ASC")
    private List<InventoryCountAttempt> attempts = new ArrayList<>();

    // ====== Domain Methods ======

    /**
     * Chụp snapshot số lượng tồn kho hiện tại.
     */
    public void takeSnapshot(BigDecimal currentQuantityOnHand) {
        this.snapshotQuantity = currentQuantityOnHand;
        this.movementQuantity = BigDecimal.ZERO;
        this.expectedQuantity = currentQuantityOnHand;
    }

    public void reconcileMovements(BigDecimal movementQuantity) {
        this.movementQuantity = movementQuantity == null ? BigDecimal.ZERO : movementQuantity;
        this.expectedQuantity = this.snapshotQuantity.add(this.movementQuantity);
        if (this.countedQuantity != null) {
            this.varianceQuantity = this.countedQuantity.subtract(this.expectedQuantity);
            updateVarianceValue();
        }
    }

    public void takeCostSnapshot(BigDecimal unitCost) {
        this.snapshotUnitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        updateVarianceValue();
    }

    /**
     * Ghi nhận số lượng đếm được. Tự động tính variance.
     */
    public void recordCount(BigDecimal countedQuantity, String reasonCode, String note) {
        if (countedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Số lượng đếm không được âm");
        }
        this.countedQuantity = countedQuantity;
        this.varianceQuantity = this.countedQuantity.subtract(this.expectedQuantity);
        updateVarianceValue();
        this.reasonCode = reasonCode;
        this.note = note;
    }

    public void recordConditions(
            BigDecimal good,
            BigDecimal damaged,
            BigDecimal expired,
            BigDecimal quarantined) {
        if (good == null && damaged == null && expired == null && quarantined == null) {
            this.goodQuantity = this.countedQuantity;
            this.damagedQuantity = BigDecimal.ZERO;
            this.expiredQuantity = BigDecimal.ZERO;
            this.quarantinedQuantity = BigDecimal.ZERO;
            return;
        }
        this.goodQuantity = zeroIfNull(good);
        this.damagedQuantity = zeroIfNull(damaged);
        this.expiredQuantity = zeroIfNull(expired);
        this.quarantinedQuantity = zeroIfNull(quarantined);
        boolean hasNegative = this.goodQuantity.signum() < 0
                || this.damagedQuantity.signum() < 0
                || this.expiredQuantity.signum() < 0
                || this.quarantinedQuantity.signum() < 0;
        if (hasNegative) {
            throw new BadRequestException("Số lượng theo tình trạng không được âm");
        }
        BigDecimal conditionTotal = this.goodQuantity
                .add(this.damagedQuantity)
                .add(this.expiredQuantity)
                .add(this.quarantinedQuantity);
        if (conditionTotal.compareTo(this.countedQuantity) != 0) {
            throw new BadRequestException("Tổng số lượng theo tình trạng phải bằng số lượng thực đếm");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void updateVarianceValue() {
        this.varianceValue = this.varianceQuantity == null
                ? BigDecimal.ZERO
                : this.varianceQuantity.multiply(this.snapshotUnitCost);
    }

    /**
     * Đảm bảo variance đã được tính (dùng khi finish).
     */
    public void finalizeVariance() {
        if (this.countedQuantity != null) {
            this.varianceQuantity = this.countedQuantity.subtract(this.expectedQuantity);
            updateVarianceValue();
        }
    }

    /**
     * Kiểm tra line đã được đếm chưa.
     */
    public boolean hasBeenCounted() {
        return this.countedQuantity != null;
    }

    public void addAttempt(InventoryCountAttempt attempt) {
        attempts.add(attempt);
        attempt.setCountLine(this);
    }

    public void requestRecount() {
        if (!attempts.isEmpty()) {
            attempts.get(attempts.size() - 1).setStatus(InventoryCountAttemptStatus.RECOUNT_REQUIRED);
        }
        this.countedQuantity = null;
        this.varianceQuantity = null;
        this.varianceValue = BigDecimal.ZERO;
        this.reasonCode = null;
        this.note = null;
        this.goodQuantity = BigDecimal.ZERO;
        this.damagedQuantity = BigDecimal.ZERO;
        this.expiredQuantity = BigDecimal.ZERO;
        this.quarantinedQuantity = BigDecimal.ZERO;
    }
}
