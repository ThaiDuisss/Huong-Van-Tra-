package org.hvtra.fileservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

/** Durable reservation; the generated file ID is the export UUID. */
@Entity
@Table(name = "generated_report_files")
@Getter
@Setter
@NoArgsConstructor
public class GeneratedReportFile {
    @Id
    @Column(name = "report_id", columnDefinition = "BINARY(16)")
    private UUID reportId;
    @Column(name = "owner_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ownerId;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "file_name", nullable = false)
    private String fileName;
}
