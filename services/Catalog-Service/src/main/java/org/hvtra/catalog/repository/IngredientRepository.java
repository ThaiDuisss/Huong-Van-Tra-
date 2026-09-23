package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Ingredient;
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
}
