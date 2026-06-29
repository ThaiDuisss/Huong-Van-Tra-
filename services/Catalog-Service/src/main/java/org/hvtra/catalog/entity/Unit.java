package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;

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
     * Phân loại đơn vị: BASE (đơn vị gốc) hoặc PACKAGING (đơn vị đóng gói).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType type = UnitType.BASE;

    /**
     * Đơn vị gốc mà đơn vị đóng gói này tham chiếu đến.
     * Chỉ có giá trị khi type = PACKAGING.
     * Ví dụ: Đơn vị "Hộp 100g" -> baseUnit = "Gram"
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_unit_id")
    private Unit baseUnit;

    /**
     * Tỷ lệ quy đổi so với đơn vị gốc.
     * Chỉ có ý nghĩa khi type = PACKAGING.
     * Ví dụ: 1 Hộp = 100 Gram -> conversionRate = 100.0000
     */
    @Column(name = "conversion_rate", nullable = false, precision = 18, scale = 4)
    private BigDecimal conversionRate = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;
}
