package org.hvtra.commonservice.importing.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.commonservice.importing.entity.ImportBatchSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ImportBatchSequenceRepository extends JpaRepository<ImportBatchSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ImportBatchSequence s WHERE s.sequenceKey = :key")
    Optional<ImportBatchSequence> findBySequenceKeyWithLock(@Param("key") String key);
}
