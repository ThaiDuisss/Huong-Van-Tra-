package org.hvtra.commonservice.common_importing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Sinh batch_code (vd "IMP-00001") cho ImportBatch. Mỗi service (Catalog, Inventory, ...)
 * tự tạo bảng này trong DB riêng của mình qua migration — không share DB giữa các service.
 */
@Entity
@Table(name = "import_batch_sequences")
@Getter
@Setter
public class ImportBatchSequence {

    @Id
    @Column(name = "sequence_key", length = 50)
    private String sequenceKey;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
