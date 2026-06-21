package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Bom;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID>, JpaSpecificationExecutor<Bom> {
    boolean existsByProductVariantIdAndBomVersion(UUID variantId, Integer version);
    boolean existsByProductVariantIdAndBomVersionAndIdNot(UUID variantId, Integer version, UUID id);
    boolean existsByProductVariantIdAndStatusAndIdNot(UUID variantId, Status status, UUID id);
}
