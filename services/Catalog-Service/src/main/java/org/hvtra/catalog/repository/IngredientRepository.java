package org.hvtra.catalog.repository;

import org.hvtra.catalog.dto.response.IngredientBundleLookupResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.repository.projection.IdNameView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID>, JpaSpecificationExecutor<Ingredient> {
    boolean existsByIngredientCode(String ingredientCode);
    boolean existsByIngredientCodeAndIdNot(String ingredientCode, UUID id);
    @Query("SELECT i FROM Supplier s JOIN s.ingredients i WHERE s.id = :supplierId AND i.status = 'ACTIVE'")
    List<Ingredient> findBySupplierId(@Param("supplierId") UUID supplierId);

    @Query("SELECT i FROM Ingredient i LEFT JOIN FETCH i.unit WHERE i.id IN :ids")
    List<Ingredient> findAllByIdWithUnit(@Param("ids") Collection<UUID> ids);

    /** Dropdown chọn nguyên liệu — chỉ id + name, tìm theo tên hoặc mã. */
    @Query("""
            SELECT i.id AS id, i.name AS name
            FROM Ingredient i
            WHERE i.isDeleted = false
              AND (:status IS NULL OR i.status = :status)
              AND (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.ingredientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<IdNameView> findLookup(@Param("search") String search, @Param("status") Status status, Pageable pageable);

    /** Lấy tên theo id cho các giá trị đã chọn sẵn trong dropdown. */
    @Query("SELECT i.id AS id, i.name AS name FROM Ingredient i WHERE i.id IN :ids")
    List<IdNameView> findLookupByIds(@Param("ids") Collection<UUID> ids);

    /** Nguyên liệu bán lẻ cho bundle: ACTIVE, nguyên liệu pha chế (type = 1), có giá bán lẻ. */
    @Query(value = """
            SELECT new org.hvtra.catalog.dto.response.IngredientBundleLookupResponse(
                i.id, i.ingredientCode, i.name, i.imageUrl, u.symbol, i.retailPricePerUnit)
            FROM Ingredient i
            LEFT JOIN i.unit u
            WHERE i.isDeleted = false
              AND i.status = org.hvtra.catalog.enums.Status.ACTIVE
              AND i.type = 1
              AND i.retailPricePerUnit IS NOT NULL
              AND (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.ingredientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
            countQuery = """
            SELECT COUNT(i) FROM Ingredient i
            WHERE i.isDeleted = false
              AND i.status = org.hvtra.catalog.enums.Status.ACTIVE
              AND i.type = 1
              AND i.retailPricePerUnit IS NOT NULL
              AND (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(i.ingredientCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<IngredientBundleLookupResponse> findBundleLookup(@Param("search") String search, Pageable pageable);

    @Query("SELECT i.id FROM Ingredient i JOIN i.suppliers s WHERE s.id = :supplierId")
    List<UUID> findIdsBySupplierId(@Param("supplierId") UUID supplierId);
}
