package org.hvtra.catalog.repository;

import jakarta.persistence.EntityManager;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sellable;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProductVariantSellableConcurrencyTest {
    @Autowired EntityManager entityManager;
    @Autowired ProductVariantRepository repository;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPaymentsCannotDecrementBelowZero() throws Exception {
        UUID id = transactionTemplate.execute(status -> {
            Category category = new Category();
            category.setCategoryCode("CONCURRENCY");
            category.setName("Concurrency");
            entityManager.persist(category);
            Product product = new Product();
            product.setCategory(category);
            product.setProductCode("CONCURRENT-PRODUCT");
            product.setName("Concurrent product");
            entityManager.persist(product);
            Unit unit = new Unit();
            unit.setName("Item");
            unit.setSymbol("item");
            unit.setPrecisionScale(0);
            entityManager.persist(unit);
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSku("CONCURRENT-SKU");
            variant.setVariantName("Concurrent SKU");
            variant.setQuantity(BigDecimal.ONE);
            variant.setSellingPrice(BigDecimal.ONE);
            variant.setCostPrice(BigDecimal.ONE);
            variant.setSellableQuantity(BigDecimal.ONE);
            variant.setProductionThreshold(BigDecimal.ZERO);
            variant.setProductionBatchQuantity(BigDecimal.ONE);
            variant.setUnit(unit);
            variant.setInventoryPolicy(InventoryPolicy.TRACK_STOCK);
            entityManager.persist(variant);
            entityManager.flush();
            return variant.getId();
        });

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> decrementAfter(start, id));
            var second = executor.submit(() -> decrementAfter(start, id));
            start.countDown();
            assertEquals(1, first.get() + second.get());
        }
        int remaining = transactionTemplate.execute(status ->
                repository.findById(id).orElseThrow().getSellableQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, remaining);
        Long version = transactionTemplate.execute(status -> repository.findById(id).orElseThrow().getVersion());
        assertEquals(1L, version);
    }

    private int decrementAfter(CountDownLatch start, UUID id) throws InterruptedException {
        start.await();
        return transactionTemplate.execute(status -> repository.decrementSellableIfEnough(id, BigDecimal.ONE));
    }
}
