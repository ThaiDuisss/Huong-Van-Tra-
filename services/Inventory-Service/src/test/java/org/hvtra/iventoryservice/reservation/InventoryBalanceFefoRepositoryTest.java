package org.hvtra.iventoryservice.reservation;

import jakarta.persistence.EntityManager;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fefo;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InventoryBalanceFefoRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired InventoryBalanceRepository repository;

    @Test
    void selectsOnlyAvailableUnexpiredBatchesAtRequestedLocationInFefoOrder() {
        InventoryItem item = new InventoryItem();
        item.setReferenceId(UUID.randomUUID());
        item.setItemType(InventoryItemType.INGREDIENT);
        item.setStatus(InventoryStatus.ACTIVE);
        item.setReorderPoint(0);
        entityManager.persist(item);
        InventoryLocation location = location("WH-A");
        InventoryLocation other = location("WH-B");
        LocalDate today = LocalDate.now();
        batch(item, location, "LATER", today.plusDays(10), InventoryBatchStatus.AVAILABLE, "100", "0");
        batch(item, location, "EARLY", today.plusDays(2), InventoryBatchStatus.AVAILABLE, "100", "0");
        batch(item, location, "EXPIRED", today.minusDays(1), InventoryBatchStatus.AVAILABLE, "100", "0");
        batch(item, location, "BLOCKED", today.plusDays(1), InventoryBatchStatus.BLOCKED, "100", "0");
        batch(item, location, "QUARANTINED", today.plusDays(1), InventoryBatchStatus.QUARANTINED, "100", "0");
        batch(item, location, "EMPTY", today.plusDays(1), InventoryBatchStatus.AVAILABLE, "100", "100");
        batch(item, other, "OTHER", today.plusDays(1), InventoryBatchStatus.AVAILABLE, "100", "0");
        entityManager.flush();
        entityManager.clear();

        List<InventoryBalance> candidates = repository.findFefoAvailableForUpdate(
                item.getId(), location.getId(), InventoryStatus.ACTIVE, today);

        assertEquals(List.of("EARLY", "LATER"), candidates.stream()
                .map(balance -> balance.getInventoryBatch().getBatchCode()).toList());
    }

    private InventoryLocation location(String code) {
        InventoryLocation location = new InventoryLocation();
        location.setBranchId(UUID.randomUUID());
        location.setCode(code);
        location.setName(code);
        location.setType(InventoryLocationType.WAREHOUSE);
        location.setStatus(InventoryStatus.ACTIVE);
        entityManager.persist(location);
        return location;
    }

    private void batch(InventoryItem item, InventoryLocation location, String code, LocalDate expiry,
            InventoryBatchStatus status, String onHand, String reserved) {
        InventoryBatch batch = new InventoryBatch();
        batch.setInventoryItem(item);
        batch.setBatchCode(code);
        batch.setStatus(status);
        batch.setExpiryDate(expiry);
        batch.setReceivedAt(Instant.parse("2026-09-01T00:00:00Z"));
        entityManager.persist(batch);
        InventoryBalance balance = new InventoryBalance();
        balance.setInventoryItem(item);
        balance.setInventoryBatch(batch);
        balance.setLocation(location);
        balance.setQuantityOnHand(new BigDecimal(onHand));
        balance.setQuantityReserved(new BigDecimal(reserved));
        entityManager.persist(balance);
    }
}
