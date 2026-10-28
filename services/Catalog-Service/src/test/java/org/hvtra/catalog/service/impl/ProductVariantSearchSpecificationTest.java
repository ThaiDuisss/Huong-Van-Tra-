package org.hvtra.catalog.service.impl;

import jakarta.persistence.EntityManager;
import org.hvtra.catalog.entity.Barcode;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.BarcodeType;
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
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("TS-100G");
        variant.setVariantName("Trà sen hộp 100g");
        variant.setQuantity(BigDecimal.ONE);
        variant.setSellingPrice(BigDecimal.ONE);
        variant.setCostPrice(BigDecimal.ONE);
        variant.setUnit(unit);
        variant.setInventoryPolicy(InventoryPolicy.TRACK_STOCK);
        entityManager.persist(variant);
        // Hai mã vạch cho cùng biến thể: tìm theo mã không được trả trùng dòng.
        for (String value : new String[] {"8930000000017", "8930000000024"}) {
            Barcode barcode = new Barcode();
            barcode.setProductVariant(variant);
            barcode.setBarcodeValue(value);
            barcode.setBarcodeType(BarcodeType.EAN13);
            entityManager.persist(barcode);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private Page<ProductVariant> search(String keyword) {
        return repository.findAll(
                ProductVariantServiceImpl.searchSpecification(keyword, null, null, null, null, null, null),
                PageRequest.of(0, 20));
    }

    @Test
    void keywordMatchesExactBarcodeWithoutDuplicates() {
        Page<ProductVariant> result = search("8930000000024");

        assertThat(result.getContent()).extracting(ProductVariant::getSku).containsExactly("TS-100G");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void keywordStillMatchesNameAndSkuButNotPartialBarcode() {
        assertThat(search("trà sen").getContent()).hasSize(1);
        assertThat(search("ts-100").getContent()).hasSize(1);
        assertThat(search("89300000").getContent()).isEmpty();
    }
}
