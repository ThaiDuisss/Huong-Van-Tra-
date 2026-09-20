package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.BomItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomItemRepository extends JpaRepository<BomItem, UUID> {

    @Query("SELECT bi FROM BomItem bi "
            + "LEFT JOIN FETCH bi.ingredient "
            + "LEFT JOIN FETCH bi.unit "
            + "WHERE bi.productVariantId = :variantId AND bi.isDeleted = false")
    List<BomItem> findByProductVariantId(@Param("variantId") UUID variantId);

    @Modifying
    @Query("DELETE FROM BomItem bi WHERE bi.productVariantId = :variantId")
    void deleteByProductVariantId(@Param("variantId") UUID variantId);
}
