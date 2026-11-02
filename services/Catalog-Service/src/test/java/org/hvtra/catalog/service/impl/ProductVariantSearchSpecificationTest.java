package org.hvtra.catalog.service.impl;

import jakarta.persistence.EntityManager;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:variant-search;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProductVariantSearchSpecificationTest {
    @Autowired EntityManager entityManager;
    @Autowired ProductVariantRepository repository;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setCategoryCode("TRA");
        category.setName("Trà");
        entityManager.persist(category);
        Product product = new Product();
        product.setCategory(category);
        product.setProductCode("TRA-SEN");
        product.setName("Trà sen");
        entityManager.persist(product);
        Unit unit = new Unit();
        unit.setName("Hộp");
        unit.setSymbol("hop");
        unit.setPrecisionScale(0);
        entityManager.persist(unit);

        ProductVariant variant1 = new ProductVariant();
        variant1.setProduct(product);
        variant1.setSku("TS-100G");
        variant1.setVariantName("Trà sen hộp 100g");
        variant1.setBarcode("8930000000017");
        variant1.setQuantity(BigDecimal.ONE);
        variant1.setSellingPrice(BigDecimal.ONE);
        variant1.setCostPrice(BigDecimal.ONE);
        variant1.setUnit(unit);
        variant1.setInventoryPolicy(InventoryPolicy.TRACK_STOCK);
        entityManager.persist(variant1);

        ProductVariant variant2 = new ProductVariant();
        variant2.setProduct(product);
        variant2.setSku("TS-200G");
        variant2.setVariantName("Trà sen hộp 200g");
        variant2.setBarcode("8930000000024");
        variant2.setQuantity(BigDecimal.valueOf(2));
        variant2.setSellingPrice(BigDecimal.valueOf(2));
        variant2.setCostPrice(BigDecimal.valueOf(2));
        variant2.setUnit(unit);
        variant2.setInventoryPolicy(InventoryPolicy.TRACK_STOCK);
        entityManager.persist(variant2);

        entityManager.flush();
        entityManager.clear();
    }

    private Page<ProductVariant> search(String keyword) {
        return repository.findAll(
                ProductVariantServiceImpl.searchSpecification(keyword, null, null, null, null, null, null),
                PageRequest.of(0, 20));
    }

    @Test
    void keywordMatchesBarcode() {
        Page<ProductVariant> result = search("8930000000024");

        assertThat(result.getContent()).extracting(ProductVariant::getSku).containsExactly("TS-200G");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void keywordMatchesNameAndSku() {
        assertThat(search("trà sen").getContent()).hasSize(2);
        assertThat(search("ts-100").getContent()).hasSize(1);
        assertThat(search("ts-200").getContent()).hasSize(1);
    }
}

