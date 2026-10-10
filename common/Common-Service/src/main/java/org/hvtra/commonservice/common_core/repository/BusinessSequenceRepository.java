package org.hvtra.commonservice.common_core.repository;

import org.hvtra.commonservice.common_core.entity.BusinessSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Repository quản lý bộ đếm dùng chung.
 * Sử dụng pessimistic locking để đảm bảo số tăng dần an toàn trong môi trường đa luồng.
 */
@Repository
public interface BusinessSequenceRepository extends JpaRepository<BusinessSequence, String> {

    /**
     * Tìm sequence với lock pessimistic write để tránh race condition.
     * Khi một transaction khóa row này, transaction khác phải chờ đến khi transaction này commit/rollback.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bs FROM BusinessSequence bs WHERE bs.sequenceKey = :key")
    Optional<BusinessSequence> findBySequenceKeyWithLock(@Param("key") String key);

    /**
     * Khởi tạo sequence key với giá trị 0 nếu chưa tồn tại.
     * Dùng INSERT IGNORE để tránh lỗi duplicate key khi nhiều process khởi tạo cùng lúc.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO business_sequences (sequence_key, current_value) VALUES (:key, 0)", 
           nativeQuery = true)
    void insertIfAbsent(@Param("key") String key);
}
