package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsByCategoryId(UUID categoryId);
    Optional<Product> findByProductCode(String productCode);
    boolean existsByProductCode(String productCode);
    Page<Product> findByNameContainingIgnoreCaseAndIsDeletedFalse(String name, Pageable pageable);
}

