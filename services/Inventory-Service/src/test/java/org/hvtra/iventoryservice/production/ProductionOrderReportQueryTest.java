package org.hvtra.iventoryservice.production;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrder;
import org.hvtra.iventoryservice.domain.entity.production.ProductionOrderLine;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;
import org.hvtra.iventoryservice.production.repository.ProductionOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy các câu JPQL của báo cáo sản xuất trên database nhúng. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production-report;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProductionOrderReportQueryTest {

    @Autowired EntityManager entityManager;
    @Autowired ProductionOrderRepository repository;

    @Test
    void reportQueriesFilterByStatusAndApprovalPeriodWithoutDuplicatingOrders() {
        InventoryItem variant = item(InventoryItemType.PRODUCT_VARIANT);
        InventoryItem tea = item(InventoryItemType.INGREDIENT);
        InventoryItem bag = item(InventoryItemType.INGREDIENT);
        production("LSX-IN", variant, ProductionOrderStatus.APPROVED, Instant.parse("2026-10-10T03:00:00Z"), tea, bag);
        production("LSX-BEFORE", variant, ProductionOrderStatus.APPROVED, Instant.parse("2026-09-01T03:00:00Z"), tea);
        production("LSX-DRAFT", variant, ProductionOrderStatus.DRAFT, null, tea, bag);
        production("LSX-PENDING", variant, ProductionOrderStatus.PENDING_APPROVAL, null, tea);
        production("LSX-CANCELLED", variant, ProductionOrderStatus.CANCELLED, null, tea);
        entityManager.flush();
        entityManager.clear();

        List<ProductionOrder> approved = repository.findApprovedWithLinesBetween(
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(approved).extracting(ProductionOrder::getOrderCode).containsExactly("LSX-IN");
        assertThat(approved.getFirst().getLines()).hasSize(2);

        assertThat(repository.findWithLinesByStatusIn(
                List.of(ProductionOrderStatus.DRAFT, ProductionOrderStatus.PENDING_APPROVAL)))
                .extracting(ProductionOrder::getOrderCode)
                .containsExactlyInAnyOrder("LSX-DRAFT", "LSX-PENDING");
    }

    private void production(String code, InventoryItem variant, ProductionOrderStatus status, Instant approvedAt,
                            InventoryItem... ingredients) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderCode(code);
        order.setProductVariant(variant);
        order.setPlannedQuantity(BigDecimal.TEN);
        order.setLocationId(UUID.randomUUID());
        order.setStatus(status);
        order.setApprovedAt(approvedAt);
        for (InventoryItem ingredient : ingredients) {
            ProductionOrderLine line = new ProductionOrderLine();
            line.setIngredient(ingredient);
            line.setRequiredQuantity(BigDecimal.ONE);
            order.addLine(line);
        }
        entityManager.persist(order);
    }

    private InventoryItem item(InventoryItemType type) {
        InventoryItem item = new InventoryItem();
        item.setReferenceId(UUID.randomUUID());
        item.setItemType(type);
        item.setStatus(InventoryStatus.ACTIVE);
        item.setReorderPoint(0);
        entityManager.persist(item);
        return item;
    }
}
