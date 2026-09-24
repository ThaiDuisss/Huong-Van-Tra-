package org.hvtra.catalog.repository;

import org.hvtra.catalog.dto.response.VariantCostResponse;
import org.hvtra.catalog.dto.response.VariantInventorySummaryResponse;
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

    /** Export Excel — fetch sẵn product/unit; productId null = tất cả. */
    @Query("""
            select v from ProductVariant v
            join fetch v.product p
            left join fetch v.unit
            where (:productId is null or p.id = :productId)
            """)
    List<ProductVariant> findAllForExport(@Param("productId") UUID productId);

    /** Batch theo id cho service khác (Feign) — fetch sẵn product/category/unit/barcodes để tránh N+1 khi map. */
    @Query("""
            select distinct v from ProductVariant v
            join fetch v.product p
            left join fetch p.category
            left join fetch v.unit
            left join fetch v.barcodes
            where v.id in :ids
            """)
    List<ProductVariant> findAllByIdWithDetails(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT new org.hvtra.catalog.dto.response.VariantCostResponse(v.id, v.costPrice)
            FROM ProductVariant v
            WHERE v.id IN :ids
            """)
    List<VariantCostResponse> findCostPricesByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT new org.hvtra.catalog.dto.response.VariantInventorySummaryResponse(
                v.id,
                v.sku,
                v.variantName,
                p.id,
                p.productCode,
                p.name,
                c.id,
                c.categoryCode,
                c.name,
                u.id,
                u.name,
                u.symbol,
                v.costPrice,
                v.sellingPrice,
                p.imageUrl
            )
            FROM ProductVariant v
            JOIN v.product p
            LEFT JOIN p.category c
            LEFT JOIN v.unit u
            WHERE v.id IN :ids
            """)
    List<VariantInventorySummaryResponse> findInventorySummariesByIds(@Param("ids") Collection<UUID> ids);
}
