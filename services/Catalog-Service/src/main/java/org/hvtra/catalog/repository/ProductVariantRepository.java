package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>,
        JpaSpecificationExecutor<ProductVariant> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    java.util.List<ProductVariant> findByProductId(UUID productId);

    @Query("""
            select v from ProductVariant v
            join fetch v.product p
            where p.id in :productIds
            """)
    java.util.List<ProductVariant> findByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query("""
            select new org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse(
                p.id,
                v.id,
                v.sku,
                p.name,
                v.variantName,
                c.name,
                v.sellingPrice,
                p.imageUrl,
                null,
                null,
                null,
                null
            )
            from ProductVariant v
            join v.product p
            left join p.category c
            where v.id = :variantId
            """)
    Optional<ProductVariantSearchViewResponse> findPosViewByVariantId(@Param("variantId") UUID variantId);
}
