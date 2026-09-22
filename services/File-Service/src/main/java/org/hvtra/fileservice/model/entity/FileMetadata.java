package org.hvtra.fileservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntityNoId;
import org.hvtra.fileservice.model.enums.FilePurpose;
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
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "public_url", length = 1000)
    private String publicUrl;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FilePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @Column(name = "owner_id", columnDefinition = "BINARY(16)")
    private UUID ownerId;

    @Column(name = "owner_type")
    private String ownerType;



    @Column(name = "ref_id", columnDefinition = "BINARY(16)")
    private UUID refId;

    private String etag;
}