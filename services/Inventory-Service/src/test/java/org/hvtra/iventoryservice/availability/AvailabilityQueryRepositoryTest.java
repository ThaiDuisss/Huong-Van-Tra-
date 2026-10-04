package org.hvtra.iventoryservice.availability;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.availability.repository.AvailabilityQueryRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocumentLine;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy các câu JPQL của phần ước tính ngày có hàng trên database nhúng. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:availability;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AvailabilityQueryRepositoryTest {

    private static final List<ProductionOrderStatus> OPEN_PRODUCTION = List.of(
            ProductionOrderStatus.PENDING_APPROVAL, ProductionOrderStatus.APPROVED,
            ProductionOrderStatus.RELEASED, ProductionOrderStatus.IN_PROGRESS);
    private static final List<InventoryDocumentStatus> OPEN_RECEIPT = List.of(
            InventoryDocumentStatus.PENDING_APPROVAL, InventoryDocumentStatus.APPROVED,
            InventoryDocumentStatus.RECEIVING, InventoryDocumentStatus.QC_PENDING,
            InventoryDocumentStatus.QC_COMPLETED);

    @Autowired EntityManager entityManager;
    @Autowired AvailabilityQueryRepository queries;

    @Test
    void productionOrderQueriesFilterByVariantStatusAndSourceOrder() {
        UUID variantId = UUID.randomUUID();
        UUID otherVariantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        production("LSX-OPEN", variantId, ProductionOrderStatus.IN_PROGRESS, null, null);
        production("LSX-DRAFT", variantId, ProductionOrderStatus.DRAFT, orderId, null);
        production("LSX-DONE", variantId, ProductionOrderStatus.COMPLETED, orderId,
                Instant.parse("2026-09-20T00:00:00Z"));
        production("LSX-CANCELLED", variantId, ProductionOrderStatus.CANCELLED, orderId, null);
        production("LSX-OTHER-DONE", otherVariantId, ProductionOrderStatus.COMPLETED, null,
                Instant.parse("2026-09-25T00:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(queries.findOpenProductionOrders(variantId, OPEN_PRODUCTION))
                .extracting(ProductionOrder::getOrderCode).containsExactly("LSX-OPEN");
        assertThat(queries.findRecentCompletedByVariant(variantId, PageRequest.of(0, 20)))
                .extracting(ProductionOrder::getOrderCode).containsExactly("LSX-DONE");
        // Lệnh đã hoàn tất hoặc đã hủy không còn giữ chỗ cho đơn.
        assertThat(queries.findBySourceOrderAndVariant(orderId, variantId))
                .extracting(ProductionOrder::getOrderCode).containsExactly("LSX-DRAFT");
    }

    @Test
    void receiptQueriesReturnOpenLinesAndPostedHistoryOfTheMaterial() {
        InventoryItem tea = material();
        InventoryItem sugar = material();
        receipt("PN-OPEN", InventoryDocumentStatus.APPROVED, null, tea, "5");
        receipt("PN-DRAFT", InventoryDocumentStatus.DRAFT, null, tea, "5");
        receipt("PN-POSTED-TEA", InventoryDocumentStatus.POSTED, Instant.parse("2026-09-10T00:00:00Z"), tea, "5");
        receipt("PN-POSTED-SUGAR", InventoryDocumentStatus.POSTED, Instant.parse("2026-09-12T00:00:00Z"), sugar, "5");
        entityManager.flush();
        entityManager.clear();

        List<InventoryDocumentLine> open = queries.findOpenReceiptLines(List.of(tea.getId()), OPEN_RECEIPT);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getDocument().getDocumentCode()).isEqualTo("PN-OPEN");
        assertThat(open.get(0).getOrderedQuantity()).isEqualByComparingTo("5");

        assertThat(queries.findRecentPostedReceiptsByItem(tea.getId(), PageRequest.of(0, 20)))
                .extracting(InventoryDocument::getDocumentCode).containsExactly("PN-POSTED-TEA");
    }

    @Test
    void averageSupplyLeadIsTakenFromItemsOfTheSameTypeThatHaveHistory() {
        material().setAvgSupplyLeadSeconds(100L);
        material().setAvgSupplyLeadSeconds(300L);
        material();
        entityManager.flush();
        entityManager.clear();

        assertThat(queries.averageSupplyLeadSeconds(InventoryItemType.INGREDIENT)).isEqualTo(200.0);
        assertThat(queries.averageSupplyLeadSeconds(InventoryItemType.PRODUCT_VARIANT)).isNull();
    }

    /** Chạy đúng các câu UPDATE trong file migration để chắc phần điền số liệu cũ đúng cú pháp và đúng kết quả. */
    @Test
    void migrationBackfillStoresAverageLeadTimePerItem() throws Exception {
        UUID variantId = UUID.randomUUID();
        InventoryItem product = new InventoryItem();
        product.setReferenceId(variantId);
        product.setItemType(InventoryItemType.PRODUCT_VARIANT);
        product.setStatus(InventoryStatus.ACTIVE);
        product.setReorderPoint(0);
        entityManager.persist(product);
        InventoryItem tea = material();
        InventoryItem sugar = material();
        production("LSX-1", variantId, ProductionOrderStatus.COMPLETED, null, null);
        production("LSX-2", variantId, ProductionOrderStatus.COMPLETED, null, null);
        production("LSX-3", variantId, ProductionOrderStatus.IN_PROGRESS, null, null);
        receipt("PN-1", InventoryDocumentStatus.POSTED, null, tea, "5");
        receipt("PN-2", InventoryDocumentStatus.APPROVED, null, tea, "5");
        entityManager.flush();
        // created_at do Hibernate tự gán nên đặt mốc thời gian bằng SQL: 2 lệnh mất 1 và 3 ngày, phiếu nhập mất 2 ngày.
        timestamps("production_orders", "order_code", "LSX-1", "completed_at", 1);
        timestamps("production_orders", "order_code", "LSX-2", "completed_at", 3);
        timestamps("inventory_documents", "document_code", "PN-1", "posted_at", 2);

        String migration = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V261004100000__add_supply_lead_time_to_inventory_items.sql"));
        String withoutComments = migration.replaceAll("(?m)^--.*$", "");
        for (String statement : withoutComments.split(";")) {
            if (statement.trim().toUpperCase().startsWith("UPDATE")) {
                entityManager.createNativeQuery(statement).executeUpdate();
            }
        }
        entityManager.clear();

        InventoryItem storedProduct = entityManager.find(InventoryItem.class, product.getId());
        assertThat(storedProduct.getAvgSupplyLeadSeconds()).isEqualTo(2 * 86_400L);
        assertThat(storedProduct.getSupplyLeadSamples()).isEqualTo(2);
        InventoryItem storedTea = entityManager.find(InventoryItem.class, tea.getId());
        assertThat(storedTea.getAvgSupplyLeadSeconds()).isEqualTo(2 * 86_400L);
        assertThat(storedTea.getSupplyLeadSamples()).isEqualTo(1);
        InventoryItem storedSugar = entityManager.find(InventoryItem.class, sugar.getId());
        assertThat(storedSugar.getAvgSupplyLeadSeconds()).isNull();
        assertThat(storedSugar.getSupplyLeadSamples()).isZero();
    }

    private void timestamps(String table, String codeColumn, String code, String endColumn, int days) {
        entityManager.createNativeQuery("update " + table + " set created_at = :start, " + endColumn
                        + " = :finish where " + codeColumn + " = :code")
                .setParameter("start", Instant.parse("2026-09-01T00:00:00Z"))
                .setParameter("finish", Instant.parse("2026-09-01T00:00:00Z").plusSeconds(days * 86_400L))
                .setParameter("code", code)
                .executeUpdate();
    }

    private void production(String code, UUID variantId, ProductionOrderStatus status, UUID sourceOrderId,
                            Instant completedAt) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderCode(code);
        order.setProductVariantId(variantId);
        order.setPlannedQuantity(BigDecimal.TEN);
        order.setLocationId(UUID.randomUUID());
        order.setStatus(status);
        order.setSourceOrderId(sourceOrderId);
        order.setCompletedAt(completedAt);
        entityManager.persist(order);
    }

    private InventoryItem material() {
        InventoryItem item = new InventoryItem();
        item.setReferenceId(UUID.randomUUID());
        item.setItemType(InventoryItemType.INGREDIENT);
        item.setStatus(InventoryStatus.ACTIVE);
        item.setReorderPoint(0);
        entityManager.persist(item);
        return item;
    }

    private void receipt(String code, InventoryDocumentStatus status, Instant postedAt, InventoryItem item,
                         String orderedQuantity) {
        InventoryDocument document = new InventoryDocument();
        document.setDocumentCode(code);
        document.setDocumentType(InventoryDocumentType.PURCHASE_RECEIPT);
        document.setStatus(status);
        document.setPostedAt(postedAt);
        entityManager.persist(document);
        InventoryDocumentLine line = new InventoryDocumentLine();
        line.setDocument(document);
        line.setLineNo(1);
        line.setInventoryItem(item);
        line.setOrderedQuantity(new BigDecimal(orderedQuantity));
        entityManager.persist(line);
    }
}
