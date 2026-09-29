package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Bộ đếm dùng chung để sinh mã tăng dần — mỗi loại mã là một {@code sequenceKey} (vd "LSX"). */
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
