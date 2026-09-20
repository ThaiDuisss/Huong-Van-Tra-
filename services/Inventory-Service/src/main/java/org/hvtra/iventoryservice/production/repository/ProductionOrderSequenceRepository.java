package org.hvtra.iventoryservice.production.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductionOrderSequenceRepository extends JpaRepository<ProductionOrderSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductionOrderSequence s WHERE s.sequenceKey = :key")
    Optional<ProductionOrderSequence> findBySequenceKeyWithLock(@Param("key") String key);
}
