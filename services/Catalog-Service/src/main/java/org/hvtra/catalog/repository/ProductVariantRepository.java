package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>,
        JpaSpecificationExecutor<ProductVariant> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    List<ProductVariant> findByProductId(UUID productId);

    @Query("""
            select v from ProductVariant v
            join fetch v.product p
            where p.id in :productIds
            """)
    List<ProductVariant> findByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query("""
            select distinct v from ProductVariant v
            join fetch v.product p
            left join fetch p.category
            left join fetch v.unit
            left join fetch v.barcodes
            where p.id in :productIds
            order by v.sku
            """)
    List<ProductVariant> findByProductIdsWithDetails(@Param("productIds") Collection<UUID> productIds);
}
