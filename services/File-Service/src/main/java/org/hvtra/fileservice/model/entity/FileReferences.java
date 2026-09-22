package org.hvtra.fileservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.util.UUID;

@Entity
@Table(
        name = "file_references",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_file_ref",
                        columnNames = {"file_id", "ref_type", "ref_id", "usage_type"}
                )
        },
        indexes = {
                @Index(name = "idx_ref", columnList = "ref_type, ref_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileReferences extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "file_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_file_references_file")
    )
    FileMetadata file;

    @Column(name = "ref_type", length = 50, nullable = false)
    String refType;

    @Column(name = "ref_id", columnDefinition = "BINARY(16)", nullable = false)
    UUID refId;

    @Column(name = "usage_type", length = 50, nullable = false)
    String usageType;
}
