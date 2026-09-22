package org.hvtra.commonservice.common_importing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "import_batch_items")
@Getter
@Setter
public class ImportBatchItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @Column(name = "row_index", nullable = false)
    private Integer rowIndex;

    /** JSON của payload theo đúng field của request nghiệp vụ tương ứng (ví dụ ProductRequest). */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** JSON danh sách lỗi validate {field, message}; null/rỗng nếu dòng hợp lệ. */
    @Lob
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    /** Số lần dòng dữ liệu này xuất hiện (y hệt tất cả các trường) trong file gốc — các dòng trùng bị gộp lại còn 1 item. */
    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount = 1;

    /** Set sau khi duyệt — id của entity nghiệp vụ thật vừa được tạo (vd Product.id), phục vụ audit. */
    @Column(name = "created_entity_id", columnDefinition = "BINARY(16)")
    private UUID createdEntityId;
}
