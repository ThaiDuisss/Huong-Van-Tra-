package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Bom;
import org.hvtra.catalog.entity.BomItem;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID>, JpaSpecificationExecutor<Bom> {
    boolean existsByProductVariantIdAndBomVersion(UUID variantId, Integer version);
    boolean existsByProductVariantIdAndBomVersionAndIdNot(UUID variantId, Integer version, UUID id);
    boolean existsByBomCode(String bomCode);
    boolean existsByBomCodeAndIdNot(String bomCode, UUID id);

    @Query("SELECT b FROM Bom b WHERE b.productVariantId = :variantId AND b.status = :status "
            + "AND b.isDeleted = false ORDER BY b.isDefault DESC, b.bomVersion DESC")
    List<Bom> findLookupByVariantAndStatus(@Param("variantId") UUID variantId, @Param("status") Status status);

    @Modifying
    @Query("UPDATE Bom b SET b.isDefault = false WHERE b.productVariantId = :variantId AND b.id <> :excludeBomId")
    void clearDefaultBomsForVariant(@Param("variantId") UUID variantId, @Param("excludeBomId") UUID excludeBomId);

    @Query("SELECT MAX(b.bomVersion) FROM Bom b WHERE b.productVariantId = :variantId")
    Integer findMaxVersionByProductVariantId(@Param("variantId") UUID variantId);

    @Query("""
            select b from Bom b
            left join fetch b.productVariant pv
            left join fetch pv.product p
            left join fetch p.category
            left join fetch b.items i
            left join fetch i.ingredient
            left join fetch i.unit
            where b.id = :id
            """)
    Optional<Bom> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            select i from BomItem i
            left join fetch i.ingredient
            left join fetch i.unit
            where i.bom.id in :bomIds
            order by i.bom.id
            """)
    List<BomItem> findItemsByBomIds(@Param("bomIds") Collection<UUID> bomIds);
}
