package org.hvtra.catalog.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.catalog.entity.BusinessSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessSequenceRepository extends JpaRepository<BusinessSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BusinessSequence s WHERE s.sequenceKey = :key")
    Optional<BusinessSequence> findBySequenceKeyWithLock(@Param("key") String key);
}
