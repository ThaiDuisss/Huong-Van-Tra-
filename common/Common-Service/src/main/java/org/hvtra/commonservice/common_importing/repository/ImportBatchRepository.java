package org.hvtra.commonservice.common_importing.repository;

import org.hvtra.commonservice.common_importing.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository
        extends JpaRepository<ImportBatch, UUID>, JpaSpecificationExecutor<ImportBatch> {

    @Query("SELECT DISTINCT b FROM ImportBatch b LEFT JOIN FETCH b.items WHERE b.id = :id")
    Optional<ImportBatch> findByIdWithItems(UUID id);

    boolean existsByBatchCode(String batchCode);
}
