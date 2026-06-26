package org.hvtra.fileservice.repository;

import org.hvtra.fileservice.model.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update FileMetadata f
        set f.refId = :refId
        where f.id = :fileId
    """)
    int updateReferenceId(@Param("refId") UUID refId,
                          @Param("fileId") UUID fileId);
}
