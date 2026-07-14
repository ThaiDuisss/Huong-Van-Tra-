package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "units")
@Getter
@Setter
public class Unit extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(name = "precision_scale", nullable = false)
    private Integer precisionScale;

    /**
     * Phân loại đơn vị: WEIGHT (Khối lượng), VOLUME (Thể tích), COUNT (Đếm).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType type = UnitType.COUNT;

    /**
     * Đơn vị gốc mà đơn vị này tham chiếu đến (nếu có).
     * Ví dụ: Đơn vị "Hộp 100g" -> baseUnit = "Gram"
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_unit_id")
    private Unit baseUnit;

    /**
     * Tỷ lệ quy đổi so với đơn vị gốc.
     * Ví dụ: 1 Hộp = 100 Gram -> conversionRate = 100.0000
     */
    @Column(name = "conversion_rate", nullable = false, precision = 18, scale = 4)
    private BigDecimal conversionRate = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;
}
