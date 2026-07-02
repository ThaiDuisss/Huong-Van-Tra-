package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Bom;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID>, JpaSpecificationExecutor<Bom> {
    boolean existsByProductVariantIdAndBomVersion(UUID variantId, Integer version);
    boolean existsByProductVariantIdAndBomVersionAndIdNot(UUID variantId, Integer version, UUID id);
    boolean existsByProductVariantIdAndStatusAndIdNot(UUID variantId, Status status, UUID id);
    boolean existsByBomCode(String bomCode);
    boolean existsByBomCodeAndIdNot(String bomCode, UUID id);

    @Modifying
    @Query("UPDATE Bom b SET b.isDefault = false WHERE b.productVariantId = :variantId AND b.id <> :excludeBomId")
    void clearDefaultBomsForVariant(@Param("variantId") UUID variantId, @Param("excludeBomId") UUID excludeBomId);

    @Query("SELECT MAX(b.bomVersion) FROM Bom b WHERE b.productVariantId = :variantId")
    Integer findMaxVersionByProductVariantId(@Param("variantId") UUID variantId);
}
