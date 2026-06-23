package org.hvtra.fileservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.commonservice.domain.entity.BaseEntityNoId;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;

import java.util.UUID;

@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata extends BaseEntityNoId {
    @Id
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    private String etag;

    @Column(name = "version_id")
    private String versionId;

    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = true)
    private UUID ownerId;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Enumerated(EnumType.STRING)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    private FileStatus status;


}