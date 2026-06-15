package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.Bom;
import org.hvtra.iventoryservice.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BomRepository extends JpaRepository<Bom, Long>, JpaSpecificationExecutor<Bom> {
    boolean existsByProductVariantIdAndVersion(Long variantId, Integer version);
    boolean existsByProductVariantIdAndVersionAndIdNot(Long variantId, Integer version, Long id);
    boolean existsByProductVariantIdAndStatusAndIdNot(Long variantId, Status status, Long id);
}
