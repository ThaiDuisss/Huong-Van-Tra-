package org.hvtra.catalog.repository;

import org.hvtra.catalog.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID>, JpaSpecificationExecutor<Ingredient> {
    boolean existsByIngredientCode(String ingredientCode);
    boolean existsByIngredientCodeAndIdNot(String ingredientCode, UUID id);
}
