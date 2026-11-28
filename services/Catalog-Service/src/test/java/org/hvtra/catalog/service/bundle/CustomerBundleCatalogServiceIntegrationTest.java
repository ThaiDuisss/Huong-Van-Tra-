package org.hvtra.catalog.service.bundle;

import jakarta.persistence.EntityManager;
import org.hvtra.catalog.entity.BomItem;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.ProductType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.ComponentPayload;
import org.hvtra.catalog.integration.event.OrderCompletedCatalogEvent.OrderItemPayload;
import org.hvtra.catalog.repository.BomItemRepository;
import org.hvtra.catalog.repository.CustomerBundleLinkRepository;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.taste.TasteComposition;
import org.hvtra.commonservice.common_core.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customerbundle;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CustomerBundleCatalogService.class, RecipeCanonicalHasher.class})
class CustomerBundleCatalogServiceIntegrationTest {

    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired CustomerBundleCatalogService service;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired BomItemRepository bomItemRepository;
    @Autowired CustomerBundleLinkRepository bundleLinkRepository;
    @Autowired BusinessSequenceRepository sequenceRepository;

    @MockitoBean ProductVariantService productVariantService;
    @MockitoBean TasteComposition tasteComposition;

    private UUID ingAId;
    private UUID ingBId;

    @BeforeEach
    void setupCatalogPrimitives() {
        AtomicLong barcodeSeq = new AtomicLong(8930000000010L);
        when(productVariantService.generateBarcode()).thenAnswer(inv -> String.valueOf(barcodeSeq.incrementAndGet()));

        transactionTemplate.executeWithoutResult(status -> {
            bomItemRepository.deleteAllInBatch();
            bundleLinkRepository.deleteAllInBatch();
            variantRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            entityManager.createQuery("DELETE FROM Ingredient").executeUpdate();
            entityManager.createQuery("DELETE FROM Category").executeUpdate();
            entityManager.createQuery("DELETE FROM Unit").executeUpdate();
            sequenceRepository.deleteAllInBatch();

            Unit gram = new Unit();
            gram.setName("Gram");
            gram.setSymbol("g");
            gram.setPrecisionScale(0);
            gram.setType(UnitType.WEIGHT);
            gram.setConversionRate(BigDecimal.ONE);
            gram.setStatus(Status.ACTIVE);
            entityManager.persist(gram);

            Unit kg = new Unit();
            kg.setName("Kilogram");
            kg.setSymbol("kg");
            kg.setPrecisionScale(0);
            kg.setType(UnitType.WEIGHT);
            kg.setConversionRate(new BigDecimal("1000.0000"));
            kg.setStatus(Status.ACTIVE);
            entityManager.persist(kg);

            Ingredient ingA = new Ingredient();
            ingA.setIngredientCode("VQK-G-59");
            ingA.setName("Vỏ quýt khô");
            ingA.setUnit(gram);
            ingA.setType(1);
            ingA.setStatus(Status.ACTIVE);
            entityManager.persist(ingA);

            Ingredient ingB = new Ingredient();
            ingB.setIngredientCode("ING-CO-NGOT");
            ingB.setName("Cỏ ngọt Stevia");
            ingB.setUnit(kg);
            ingB.setType(1);
            ingB.setStatus(Status.ACTIVE);
            entityManager.persist(ingB);

            entityManager.flush();
            ingAId = ingA.getId();
            ingBId = ingB.getId();
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsCustomerProductOnceAndDeduplicatesAcrossCustomersOrdersAndReplays() {
        UUID orderItem1 = UUID.randomUUID();
        OrderCompletedCatalogEvent firstEvent = completedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderItem1,
                "Trà Quýt Stevia",
                new BigDecimal("92000"),
                List.of(
                        component(ingAId, "VQK-G-59", "35", "g"),
                        component(ingBId, "ING-CO-NGOT", "0.015", "kg")));

        assertTrue(service.persistCompletedOrder(firstEvent));
        // Replay exact same orderItemId must be idempotent
        assertTrue(service.persistCompletedOrder(firstEvent));

        // Different customer, reversed order, units in g -> must reuse same CUSTOMER Product/Variant!
        UUID orderItem2 = UUID.randomUUID();
        OrderCompletedCatalogEvent secondCustomerEvent = completedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderItem2,
                "Gói trà của khách khác",
                new BigDecimal("95000"),
                List.of(
                        component(ingBId, "ING-CO-NGOT", "15", "g"),
                        component(ingAId, "VQK-G-59", "0.035", "kg")));
        assertTrue(service.persistCompletedOrder(secondCustomerEvent));

        transactionTemplate.executeWithoutResult(status -> {
            List<ProductVariant> variants = variantRepository.findAll();
            assertEquals(1, variants.size());
            ProductVariant savedVariant = variants.get(0);
            assertEquals(ProductType.CUSTOMER, savedVariant.getProductType());
            assertEquals(InventoryPolicy.DO_NOT_TRACK, savedVariant.getInventoryPolicy());
            assertEquals(0, savedVariant.getSellableQuantity().compareTo(BigDecimal.ZERO));
            assertFalse(savedVariant.isCustomBundle());
            assertNotNull(savedVariant.getRecipeSha256());

            List<BomItem> bom = bomItemRepository.findByProductVariantId(savedVariant.getId());
            assertEquals(2, bom.size());
            assertEquals(2, bundleLinkRepository.count());

            // CUSTOMER variants must never appear in Taste Snapshot candidates
            assertTrue(variantRepository.findTasteSnapshotVariants(Status.ACTIVE, PageRequest.of(0, 50)).isEmpty());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ignoresNonCompletedAndPlainProductItems() {
        OrderCompletedCatalogEvent pendingOrder = completedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Bundle nháp",
                new BigDecimal("50000"), List.of(component(ingAId, "VQK-G-59", "10", "g")));
        pendingOrder.setOrderStatus("PAYMENT_CONFIRMED");

        assertFalse(service.persistCompletedOrder(pendingOrder));
        assertEquals(0, variantRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPersistenceOfSameRecipeProducesSingleCustomerVariant() throws Exception {
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Callable<ProductVariant>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                UUID orderId = UUID.randomUUID();
                UUID itemId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                OrderCompletedCatalogEvent event = completedEvent(
                        orderId, customerId, itemId, "Concurrent Tea " + i,
                        new BigDecimal("88000"),
                        List.of(component(ingAId, "VQK-G-59", "40", "g")));
                tasks.add(() -> {
                    start.await();
                    return service.persistBundleItem(event, event.getItems().get(0));
                });
            }
            start.countDown();
            List<Future<ProductVariant>> futures = tasks.stream().map(pool::submit).toList();
            for (Future<ProductVariant> f : futures) {
                assertNotNull(f.get());
            }
        }

        assertEquals(1, variantRepository.count());
        assertEquals(threads, bundleLinkRepository.count());
    }

    private static OrderCompletedCatalogEvent completedEvent(
            UUID orderId,
            UUID customerId,
            UUID orderItemId,
            String bundleName,
            BigDecimal unitPrice,
            List<ComponentPayload> components) {
        OrderItemPayload item = new OrderItemPayload();
        item.setOrderItemId(orderItemId);
        item.setPurchaseType("CUSTOM_BUNDLE");
        item.setProductVariantId(UUID.fromString("6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f04"));
        item.setSku("CUSTOM-BUNDLE");
        item.setProductName("Trà đóng theo yêu cầu");
        item.setVariantName(bundleName);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(unitPrice);
        item.setComponents(components);

        OrderCompletedCatalogEvent event = new OrderCompletedCatalogEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("ORDER_COMPLETED");
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setOrderStatus("COMPLETED");
        event.setPaymentStatus("PAID");
        event.setItems(List.of(item));
        return event;
    }

    private static ComponentPayload component(UUID ingredientId, String code, String qty, String unit) {
        ComponentPayload c = new ComponentPayload();
        c.setIngredientId(ingredientId);
        c.setIngredientCode(code);
        c.setQuantity(new BigDecimal(qty));
        c.setUnit(unit);
        return c;
    }
}
