package org.hvtra.iventoryservice.repository;

import org.hvtra.iventoryservice.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IngredientRepository extends JpaRepository<Ingredient, Long>, JpaSpecificationExecutor<Ingredient> {
    boolean existsByIngredientCode(String ingredientCode);
    boolean existsByIngredientCodeAndIdNot(String ingredientCode, Long id);
}
