package org.hvtra.fileservice.repository;

import org.hvtra.fileservice.model.entity.FileReferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FileReferenceRepository extends JpaRepository<FileReferences, UUID> {
    List<FileReferences> findByFile_IdAndIsDeletedFalse(UUID fileId);
    boolean existsByRefTypeAndRefIdAndUsageTypeAndIsDeletedFalseAndFile_IdNot(
            String refType, UUID refId, String usageType, UUID fileId);

    @Query("""
        select f.id from FileMetadata f where f.ownerType = 'AI_REPORT'
        and f.purpose = org.hvtra.fileservice.model.enums.FilePurpose.USER_DOCUMENT
        and f.visibility = org.hvtra.fileservice.model.enums.FileVisibility.PRIVATE
        and f.isDeleted = false and f.refId is null and f.createdAt < :before
        and not exists (select r.id from FileReferences r where r.file = f and r.isDeleted = false)
        order by f.createdAt, f.id
        """)
    List<UUID> findOrphanCandidates(@Param("before") Instant before, Pageable pageable);
}
