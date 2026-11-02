package org.hvtra.iventoryservice.movement;

import org.hvtra.iventoryservice.batch.repository.InventoryBatchRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryBalance;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.location.repository.InventoryLocationRepository;
import org.hvtra.iventoryservice.movement.dto.request.StockInRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreStockInRequest;
import org.hvtra.iventoryservice.movement.dto.request.StoreTransferRequest;
import org.hvtra.iventoryservice.movement.mapper.InventoryTransactionMapper;
import org.hvtra.iventoryservice.movement.repository.InventoryTransactionRepository;
import org.hvtra.iventoryservice.movement.service.InventoryTransactionService;
import org.hvtra.iventoryservice.movement.service.InventoryTransactionServiceImpl;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:stock_in_regression;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never",
        "storage.initialization.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.hibernate.SQL=OFF"
})
@ContextConfiguration(classes = InventoryStockInRegressionTest.JpaConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryStockInRegressionTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = InventoryItem.class)
    @EnableJpaRepositories(basePackageClasses = {
            InventoryItemRepository.class, InventoryLocationRepository.class, InventoryBalanceRepository.class,
            InventoryBatchRepository.class, InventoryTransactionRepository.class
    })
    @Import({InventoryTransactionServiceImpl.class, InventoryTransactionMapper.class})
    static class JpaConfiguration {
    }

    @Autowired InventoryTransactionService service;
    @Autowired InventoryItemRepository items;
    @Autowired InventoryLocationRepository locations;
    @Autowired InventoryBalanceRepository balances;
    @Autowired InventoryBatchRepository batches;
    @Autowired InventoryTransactionRepository transactions;
    @Autowired PlatformTransactionManager transactionManager;

    private InventoryItem item;
    private InventoryLocation shelf;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        branchId = UUID.randomUUID();
        item = item(InventoryStatus.ACTIVE);
        shelf = location();
    }

    @Test
    void storeStockInCreatesOneBalanceAndReusesItWithoutChangingReservations() {
        long transactionCountBefore = transactions.count();
        var first = service.storeStockIn(request("30"));
        InventoryBalance initial = balance(shelf);
        UUID balanceId = initial.getId();
        UUID batchId = initial.getInventoryBatch().getId();
        assertEquals(item.getId(), initial.getInventoryItem().getId());
        assertEquals(shelf.getId(), initial.getLocation().getId());
        assertNotNull(batchId);
        assertEquals(0, initial.getQuantityOnHand().compareTo(new BigDecimal("30")));
        assertEquals(0, initial.getQuantityReserved().compareTo(BigDecimal.ZERO));
        assertEquals(0, initial.getQuantityBlocked().compareTo(BigDecimal.ZERO));
        assertEquals(0, first.lines().getFirst().beforeQuantity().compareTo(BigDecimal.ZERO));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            InventoryBalance locked = balances.findForUpdate(item.getId(), shelf.getId()).orElseThrow();
            locked.setQuantityReserved(new BigDecimal("2"));
            locked.setQuantityBlocked(BigDecimal.ONE);
            balances.save(locked);
        });

        var second = service.storeStockIn(request("5"));
        InventoryBalance updated = balance(shelf);
        assertEquals(balanceId, updated.getId());
        assertEquals(batchId, updated.getInventoryBatch().getId());
        assertEquals(0, updated.getQuantityOnHand().compareTo(new BigDecimal("35")));
        assertEquals(0, updated.getQuantityReserved().compareTo(new BigDecimal("2")));
        assertEquals(0, updated.getQuantityBlocked().compareTo(BigDecimal.ONE));
        assertEquals(0, second.lines().getFirst().beforeQuantity().compareTo(new BigDecimal("30")));
        assertEquals(1, itemBalances().size());
        assertEquals(transactionCountBefore + 2, transactions.count());
    }

    @Test
    void regularStockInReusesExistingBatchForNewBalance() {
        InventoryBatch batch = new InventoryBatch();
        batch.setInventoryItem(item);
        batch.setBatchCode("D1-" + UUID.randomUUID());
        batch.setStatus(InventoryBatchStatus.AVAILABLE);
        batch = batches.saveAndFlush(batch);
        service.stockIn(new StockInRequest(branchId, InventoryReferenceType.MANUAL, UUID.randomUUID().toString(),
                List.of(new StockInRequest.LineRequest(item.getId(), shelf.getId(), new BigDecimal("4")))));
        assertEquals(batch.getId(), balance(shelf).getInventoryBatch().getId());
        assertEquals(item.getId(), balance(shelf).getInventoryItem().getId());
        assertEquals(0, balance(shelf).getQuantityOnHand().compareTo(new BigDecimal("4")));
    }

    @Test
    void transferCreatesDestinationWithSameItemAndBatch() {
        service.storeStockIn(request("8"));
        InventoryLocation destination = location();
        UUID batchId = balance(shelf).getInventoryBatch().getId();
        service.storeTransfer(new StoreTransferRequest(shelf.getId(), destination.getId(), item.getId(),
                new BigDecimal("3"), "D1 regression"));
        assertEquals(item.getId(), balance(destination).getInventoryItem().getId());
        assertEquals(batchId, balance(destination).getInventoryBatch().getId());
        assertEquals(0, balance(shelf).getQuantityOnHand().compareTo(new BigDecimal("5")));
        assertEquals(0, balance(destination).getQuantityOnHand().compareTo(new BigDecimal("3")));
        assertEquals(2, itemBalances().size());
    }

    @Test
    void failedSecondLineRollsBackFirstBalanceDefaultBatchAndTransaction() {
        InventoryItem inactive = item(InventoryStatus.INACTIVE);
        long before = transactions.count();
        assertThrows(BadRequestException.class, () -> service.storeStockIn(new StoreStockInRequest(
                shelf.getId(), InventoryReferenceType.MANUAL, UUID.randomUUID().toString(), List.of(
                        new StoreStockInRequest.LineRequest(item.getId(), new BigDecimal("3")),
                        new StoreStockInRequest.LineRequest(inactive.getId(), BigDecimal.ONE)))));
        assertTrue(itemBalances().isEmpty());
        assertTrue(batches.findFirstByInventoryItemId(item.getId()).isEmpty());
        assertEquals(before, transactions.count());
    }

    @Test
    void duplicateLinesFailBeforeAnyStockWrite() {
        var line = new StoreStockInRequest.LineRequest(item.getId(), BigDecimal.ONE);
        long before = transactions.count();
        assertThrows(BadRequestException.class, () -> service.storeStockIn(new StoreStockInRequest(
                shelf.getId(), InventoryReferenceType.MANUAL, UUID.randomUUID().toString(), List.of(line, line))));
        assertTrue(itemBalances().isEmpty());
        assertTrue(batches.findFirstByInventoryItemId(item.getId()).isEmpty());
        assertEquals(before, transactions.count());
    }

    @Test
    void concurrentStockInsSerializeOnItemAndCreateOneBalance() throws Exception {
        long transactionCountBefore = transactions.count();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return service.storeStockIn(request("2")); });
            var second = executor.submit(() -> { start.await(); return service.storeStockIn(request("3")); });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, itemBalances().size());
        assertEquals(0, balance(shelf).getQuantityOnHand().compareTo(new BigDecimal("5")));
        assertEquals(transactionCountBefore + 2, transactions.count());
    }

    private StoreStockInRequest request(String quantity) {
        return new StoreStockInRequest(shelf.getId(), InventoryReferenceType.MANUAL,
                UUID.randomUUID().toString(), List.of(new StoreStockInRequest.LineRequest(item.getId(), new BigDecimal(quantity))));
    }

    private InventoryItem item(InventoryStatus status) {
        InventoryItem value = new InventoryItem();
        value.setReferenceId(UUID.randomUUID());
        value.setItemType(InventoryItemType.PRODUCT_VARIANT);
        value.setStatus(status);
        value.setReorderPoint(0);
        return items.saveAndFlush(value);
    }

    private InventoryLocation location() {
        InventoryLocation value = new InventoryLocation();
        value.setBranchId(branchId);
        value.setCode("D1-" + UUID.randomUUID());
        value.setName("D1 regression shelf");
        value.setType(InventoryLocationType.SHELF);
        value.setStatus(InventoryStatus.ACTIVE);
        return locations.saveAndFlush(value);
    }

    private List<InventoryBalance> itemBalances() {
        return balances.findAll().stream().filter(value -> value.getInventoryItem().getId().equals(item.getId())).toList();
    }

    private InventoryBalance balance(InventoryLocation location) {
        return itemBalances().stream().filter(value -> value.getLocation().getId().equals(location.getId())).findFirst().orElseThrow();
    }
}
