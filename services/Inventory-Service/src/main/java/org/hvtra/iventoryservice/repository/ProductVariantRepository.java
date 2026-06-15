package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long>,
        JpaSpecificationExecutor<ProductVariant> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
}
