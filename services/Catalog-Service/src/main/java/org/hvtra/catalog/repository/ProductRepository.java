package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    boolean existsByCategoryId(UUID categoryId);
    Optional<Product> findByProductCode(String productCode);
    boolean existsByProductCode(String productCode);
    Page<Product> findByNameContainingIgnoreCaseAndIsDeletedFalse(String name, Pageable pageable);

    @Query("""
            select p from Product p
            left join fetch p.category
            where p.id in :ids
            """)
    List<Product> findAllByIdWithCategory(@Param("ids") Collection<UUID> ids);

    /** Export Excel — fetch sẵn category để không lazy-load theo từng sản phẩm. */
    @Query("""
            select p from Product p
            left join fetch p.category
            """)
    List<Product> findAllWithCategory();
}

