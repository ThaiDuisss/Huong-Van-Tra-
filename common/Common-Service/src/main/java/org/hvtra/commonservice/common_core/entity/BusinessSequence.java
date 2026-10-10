package org.hvtra.commonservice.common_core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bộ đếm dùng chung để sinh mã tăng dần — mỗi loại mã là một {@code sequenceKey} (vd "LSX", "IMP-BATCH").
 * Cung cấp cơ chế sinh số tăng dần an toàn trong môi trường đa luồng.
 */
@Entity
@Table(name = "business_sequences")
@Getter
@Setter
public class BusinessSequence {

    @Id
    @Column(name = "sequence_key", length = 50)
    private String sequenceKey;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
