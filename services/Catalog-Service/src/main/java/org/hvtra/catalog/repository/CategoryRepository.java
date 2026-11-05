package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.hvtra.catalog.repository.projection.IdNameView;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    Optional<Category> findByCategoryCode(String categoryCode);
    boolean existsByCategoryCode(String categoryCode);

    @Query("SELECT c.id AS id, c.name AS name FROM Category c WHERE c.isDeleted = false AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IdNameView> findLookup(@Param("search") String search, Pageable pageable);

    /** Lấy tên theo id cho các giá trị đã chọn sẵn trong dropdown. */
    @Query("SELECT c.id AS id, c.name AS name FROM Category c WHERE c.isDeleted = false AND c.id IN :ids")
    List<IdNameView> findLookupByIds(@Param("ids") Collection<UUID> ids);
}

