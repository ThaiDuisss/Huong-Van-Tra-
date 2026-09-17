package org.hvtra.commonservice.importing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.commonservice.importing.domain.enums.ImportBatchStatus;
import org.hvtra.commonservice.importing.domain.enums.ImportType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "import_batches",
        uniqueConstraints = @UniqueConstraint(name = "uq_import_batches_code", columnNames = "batch_code")
)
@Getter
@Setter
public class ImportBatch extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 50)
    private ImportType importType;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ImportBatchStatus status = ImportBatchStatus.DRAFT;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "submitted_by", columnDefinition = "BINARY(16)")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", columnDefinition = "BINARY(16)")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @OneToMany(mappedBy = "importBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImportBatchItem> items = new ArrayList<>();

    public void addItem(ImportBatchItem item) {
        items.add(item);
        item.setImportBatch(this);
    }
}
