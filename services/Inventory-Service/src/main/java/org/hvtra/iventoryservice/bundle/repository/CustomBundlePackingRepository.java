package org.hvtra.iventoryservice.bundle.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.iventoryservice.domain.entity.bundle.CustomBundlePacking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomBundlePackingRepository extends JpaRepository<CustomBundlePacking, UUID>,
        JpaSpecificationExecutor<CustomBundlePacking> {

    Optional<CustomBundlePacking> findByOrderItemId(UUID orderItemId);

    List<CustomBundlePacking> findByOrderId(UUID orderId);

    @Query("""
            select distinct p from CustomBundlePacking p
            left join fetch p.lines l
            left join fetch l.inventoryItem
            where p.id = :id
            """)
    Optional<CustomBundlePacking> findByIdWithLines(@Param("id") UUID id);

    /** Khóa dòng khi đổi trạng thái để hai thao tác (vd xác nhận đóng gói và huỷ) không chạy chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomBundlePacking p where p.id = :id")
    Optional<CustomBundlePacking> findByIdForUpdate(@Param("id") UUID id);
}
