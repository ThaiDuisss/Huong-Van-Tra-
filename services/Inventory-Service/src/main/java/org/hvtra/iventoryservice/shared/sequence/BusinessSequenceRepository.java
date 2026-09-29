package org.hvtra.iventoryservice.shared.sequence;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.BusinessSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessSequenceRepository extends JpaRepository<BusinessSequence, String> {

    // INSERT IGNORE để 2 request đồng thời cùng khởi tạo 1 key mới không bị lỗi trùng khóa chính
    @Modifying
    @Query(value = "INSERT IGNORE INTO business_sequences (sequence_key, current_value) VALUES (:key, 0)", nativeQuery = true)
    void insertIfAbsent(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BusinessSequence s WHERE s.sequenceKey = :key")
    Optional<BusinessSequence> findBySequenceKeyWithLock(@Param("key") String key);
}
