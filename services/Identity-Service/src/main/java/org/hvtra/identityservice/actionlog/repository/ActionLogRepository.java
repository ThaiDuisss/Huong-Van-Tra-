package org.hvtra.identityservice.actionlog.repository;

import org.hvtra.identityservice.actionlog.entity.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long>, JpaSpecificationExecutor<ActionLog> {

    /** Xóa theo lô để không khóa bảng lâu; job dọn gọi lặp tới khi trả về < limit. */
    @Modifying
    @Query(value = "DELETE FROM action_log WHERE occurred_at < :before LIMIT :limit", nativeQuery = true)
    int deleteOlderThan(@Param("before") Instant before, @Param("limit") int limit);
}
