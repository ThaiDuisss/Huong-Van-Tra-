package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID>, JpaSpecificationExecutor<PriceList> {

    /** Nạp bảng giá kèm items → variant → product → category trong 1 query (tránh N+1 khi map). */
    @Query("""
            select distinct pl from PriceList pl
            left join fetch pl.items i
            left join fetch i.productVariant v
            left join fetch v.product p
            left join fetch p.category
            left join fetch v.unit
            where pl.id in :ids
            """)
    List<PriceList> findAllWithItemsByIdIn(@Param("ids") Collection<UUID> ids);
}
