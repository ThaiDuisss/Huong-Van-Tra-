package org.hvtra.catalog.service.impl;

import jakarta.persistence.EntityManager;
import org.hvtra.catalog.dto.request.PriceListRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.PriceChangeHistoryResponse;
import org.hvtra.catalog.entity.Category;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.Unit;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.PriceChangeType;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.exception.ResourceNotFoundException;
import org.hvtra.catalog.mapper.CategoryMapper;
import org.hvtra.catalog.mapper.PriceListMapper;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.PriceChangeHistoryRepository;
import org.hvtra.catalog.service.PriceChangeHistoryService;
import org.hvtra.catalog.service.PriceListService;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pricehistory;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PriceListServiceImpl.class,
        PriceChangeHistoryServiceImpl.class,
        PriceListMapper.class,
        ProductMapper.class,
        CategoryMapper.class
})
class PriceChangeHistoryIntegrationTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Autowired EntityManager entityManager;
    @Autowired PriceListService priceListService;
    @Autowired PriceChangeHistoryService historyService;
    @Autowired PriceChangeHistoryRepository historyRepository;

    private UUID variantA;
    private UUID variantB;
    private UUID variantC;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new InternalUserPrincipal(USER_ID.toString(), "admin01", "test", Set.of("ADMIN"), "corr"),
                null,
                List.of()));

        Category category = new Category();
        category.setCategoryCode("PRICE-HISTORY");
        category.setName("Price history");
        entityManager.persist(category);
        Product product = new Product();
        product.setCategory(category);
        product.setProductCode("PH-PRODUCT");
        product.setName("Tra sen");
        entityManager.persist(product);
        Unit unit = new Unit();
        unit.setName("Hop");
        unit.setSymbol("hop");
        unit.setPrecisionScale(0);
        entityManager.persist(unit);
        variantA = persistVariant(product, unit, "SKU-A");
        variantB = persistVariant(product, unit, "SKU-B");
        variantC = persistVariant(product, unit, "SKU-C");
        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_recordsOneCreatedRowPerItemWithActor() {
        UUID id = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"), item(variantB, "20000"))).id();
        flushAndClear();

        List<PriceChangeHistoryResponse> rows = history(id).content();
        assertEquals(2, rows.size());
        PriceChangeHistoryResponse first = rows.get(0);
        assertEquals("SKU-A", first.sku());
        assertEquals("Variant SKU-A", first.variantName());
        assertEquals("Bang gia le", first.priceListName());
        assertEquals(PriceChangeType.CREATED, first.changeType());
        assertNull(first.oldPrice());
        assertEquals(0, first.newPrice().compareTo(new BigDecimal("10000")));
        assertNull(first.priceDifference());
        assertEquals(USER_ID, first.changedBy());
        assertEquals("admin01", first.changedByName());
    }

    @Test
    void update_recordsOnlyChangedAddedAndRemovedPrices() {
        UUID id = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"), item(variantB, "20000"))).id();
        flushAndClear();
        historyRepository.deleteAll();
        flushAndClear();

        // A: không đổi (khác scale) -> không ghi; B: gỡ; C: thêm.
        priceListService.update(id, request(Status.ACTIVE, item(variantA, "10000.00"), item(variantC, "30000")));
        flushAndClear();
        List<PriceChangeHistoryResponse> rows = history(id).content();
        assertEquals(2, rows.size());
        PriceChangeHistoryResponse removed = rows.stream().filter(r -> r.sku().equals("SKU-B")).findFirst().orElseThrow();
        assertEquals(PriceChangeType.REMOVED, removed.changeType());
        assertEquals(0, removed.oldPrice().compareTo(new BigDecimal("20000")));
        assertNull(removed.newPrice());
        PriceChangeHistoryResponse added = rows.stream().filter(r -> r.sku().equals("SKU-C")).findFirst().orElseThrow();
        assertEquals(PriceChangeType.CREATED, added.changeType());

        historyRepository.deleteAll();
        flushAndClear();
        priceListService.update(id, request(Status.ACTIVE, item(variantA, "12500"), item(variantC, "30000")));
        flushAndClear();
        rows = history(id).content();
        assertEquals(1, rows.size());
        PriceChangeHistoryResponse updated = rows.get(0);
        assertEquals(PriceChangeType.UPDATED, updated.changeType());
        assertEquals(variantA, updated.productVariantId());
        assertEquals(0, updated.oldPrice().compareTo(new BigDecimal("10000")));
        assertEquals(0, updated.newPrice().compareTo(new BigDecimal("12500")));
        assertEquals(0, updated.priceDifference().compareTo(new BigDecimal("2500")));

        // Giá bảng giá vẫn được cập nhật như trước.
        assertEquals(2, priceListService.findById(id).items().size());
    }

    @Test
    void update_withIdenticalItems_recordsNothing() {
        UUID id = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"))).id();
        flushAndClear();
        long before = historyRepository.count();

        priceListService.update(id, request(Status.ACTIVE, item(variantA, "10000")));
        flushAndClear();

        assertEquals(before, historyRepository.count());
    }

    @Test
    void statusChange_isRecordedOnceAndOnlyWhenDifferent() {
        UUID id = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"))).id();
        flushAndClear();

        priceListService.updateStatus(id, Status.ACTIVE);
        priceListService.updateStatus(id, Status.INACTIVE);
        flushAndClear();
        priceListService.update(id, request(Status.ACTIVE, item(variantA, "10000")));
        flushAndClear();

        List<PriceChangeHistoryResponse> rows = historyService
                .findByPriceList(id, null, PriceChangeType.STATUS_CHANGED, null, null, 0, 20).content();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.productVariantId() == null && r.oldPrice() == null));
        assertTrue(rows.stream().anyMatch(r -> r.oldStatus() == Status.ACTIVE && r.newStatus() == Status.INACTIVE));
        assertTrue(rows.stream().anyMatch(r -> r.oldStatus() == Status.INACTIVE && r.newStatus() == Status.ACTIVE));
    }

    @Test
    void find_filtersByVariantAndDateRange_pagesNewestFirst() {
        UUID first = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"), item(variantB, "20000"))).id();
        UUID second = priceListService.create(request(Status.ACTIVE, item(variantA, "11000"))).id();
        flushAndClear();
        priceListService.update(first, request(Status.ACTIVE, item(variantA, "15000"), item(variantB, "20000")));
        flushAndClear();

        PageResponse<PriceChangeHistoryResponse> ofVariantA =
                historyService.findByPriceList(first, variantA, null, null, null, 0, 1);
        assertEquals(2, ofVariantA.totalElements());
        assertEquals(2, ofVariantA.totalPages());
        assertEquals(PriceChangeType.UPDATED, ofVariantA.content().get(0).changeType());

        assertEquals(3, historyService.findByVariant(variantA, null, null, null, 0, 20).totalElements());
        assertEquals(1, history(second).totalElements());

        Instant future = Instant.now().plusSeconds(3600);
        assertEquals(0, historyService.findByPriceList(first, null, null, future, null, 0, 20).totalElements());
        assertEquals(3, historyService.findByPriceList(first, null, null, null, future, 0, 20).totalElements());
    }

    @Test
    void find_unknownPriceList_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> historyService.findByPriceList(UUID.randomUUID(), null, null, null, null, 0, 20));
    }

    @Test
    void record_withoutAuthenticatedUser_storesNullActor() {
        SecurityContextHolder.clearContext();
        UUID id = priceListService.create(request(Status.ACTIVE, item(variantA, "10000"))).id();
        flushAndClear();

        PriceChangeHistoryResponse row = history(id).content().get(0);
        assertNull(row.changedBy());
        assertNull(row.changedByName());
    }

    private PageResponse<PriceChangeHistoryResponse> history(UUID priceListId) {
        return historyService.findByPriceList(priceListId, null, null, null, null, 0, 20);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PriceListRequest request(Status status, PriceListRequest.ItemRequest... items) {
        return new PriceListRequest(BRANCH_ID, "Bang gia le", null, null, status, List.of(items));
    }

    private static PriceListRequest.ItemRequest item(UUID variantId, String price) {
        return new PriceListRequest.ItemRequest(variantId, new BigDecimal(price));
    }

    private UUID persistVariant(Product product, Unit unit, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setVariantName("Variant " + sku);
        variant.setQuantity(BigDecimal.ONE);
        variant.setSellingPrice(BigDecimal.ONE);
        variant.setCostPrice(BigDecimal.ONE);
        variant.setUnit(unit);
        variant.setInventoryPolicy(InventoryPolicy.TRACK_STOCK);
        entityManager.persist(variant);
        return variant.getId();
    }
}
