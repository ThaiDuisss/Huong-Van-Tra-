package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    java.util.Optional<Category> findByCategoryCode(String categoryCode);
    boolean existsByCategoryCode(String categoryCode);
}

