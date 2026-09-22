package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnPhysicalProgressResolverTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderReturnInventoryCommandRepository commandRepository;

    @InjectMocks
    private OrderReturnPhysicalProgressResolver resolver;

    private UUID returnId;
    private OrderReturn orderReturn;

    @BeforeEach
    void setUp() {
        returnId = UUID.randomUUID();
        orderReturn = OrderReturn.builder().build();
        orderReturn.setId(returnId);
    }

    private OrderReturnItem createItem(UUID itemId, BigDecimal quantity, boolean isDeleted) {
        OrderReturnItem item = OrderReturnItem.builder()
                .quantity(quantity)
                .build();
        item.setId(itemId);
        item.setIsDeleted(isDeleted);
        return item;
    }

    @Test
    @DisplayName("Case 1 — Single item fully processed")
    void testSingleItemFullyProcessed() {
        UUID itemId = UUID.randomUUID();
        orderReturn.setItems(List.of(createItem(itemId, new BigDecimal("2.000"), false)));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, OrderReturnInventoryCommandStatus.PROCESSED}));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, itemId, new BigDecimal("2.000")}));

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertEquals(0, new BigDecimal("2.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, new BigDecimal("2.000").compareTo(progress.processedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.remainingQuantity()));
        assertTrue(progress.physicalDone());
        assertEquals("COMPLETED", progress.inventoryProcessingStatus());
        assertFalse(progress.hasPendingCommand());
        assertFalse(progress.hasFailedCommand());
    }

    @Test
    @DisplayName("Case 2 — Partial multiple items: A done, B pending command")
    void testPartialMultipleItems() {
        UUID itemAId = UUID.randomUUID();
        UUID itemBId = UUID.randomUUID();
        orderReturn.setItems(List.of(
                createItem(itemAId, new BigDecimal("6.000"), false),
                createItem(itemBId, new BigDecimal("4.000"), false)
        ));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{returnId, OrderReturnInventoryCommandStatus.PROCESSED},
                        new Object[]{returnId, OrderReturnInventoryCommandStatus.PENDING}
                ));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, itemAId, new BigDecimal("6.000")}));

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertEquals(0, new BigDecimal("10.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, new BigDecimal("6.000").compareTo(progress.processedQuantity()));
        assertEquals(0, new BigDecimal("4.000").compareTo(progress.remainingQuantity()));
        assertFalse(progress.physicalDone());
        assertEquals("PROCESSING", progress.inventoryProcessingStatus());
        assertTrue(progress.hasPendingCommand());
        assertFalse(progress.hasFailedCommand());
    }

    @Test
    @DisplayName("Case 3 — Overlap clamp: raw processed > item required does not exceed item required")
    void testOverlapClamp() {
        UUID itemId = UUID.randomUUID();
        orderReturn.setItems(List.of(createItem(itemId, new BigDecimal("6.000"), false)));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, OrderReturnInventoryCommandStatus.PROCESSED}));
        // Raw processed is 10 (e.g. duplicate or retry commands)
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, itemId, new BigDecimal("10.000")}));

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertEquals(0, new BigDecimal("6.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, new BigDecimal("6.000").compareTo(progress.processedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.remainingQuantity()));
        assertTrue(progress.physicalDone());
        assertEquals("COMPLETED", progress.inventoryProcessingStatus());
    }

    @Test
    @DisplayName("Case 4 — Cross-item anti-bleed: excess on Item A does not count towards Item B")
    void testCrossItemAntiBleed() {
        UUID itemAId = UUID.randomUUID();
        UUID itemBId = UUID.randomUUID();
        orderReturn.setItems(List.of(
                createItem(itemAId, new BigDecimal("5.000"), false),
                createItem(itemBId, new BigDecimal("5.000"), false)
        ));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, OrderReturnInventoryCommandStatus.PROCESSED}));
        // Raw processed: Item A = 10 (excess 5), Item B = 0
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, itemAId, new BigDecimal("10.000")}));

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        // Required: 10, Processed: 5 (clamped A), Remaining: 5
        assertEquals(0, new BigDecimal("10.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(progress.processedQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(progress.remainingQuantity()));
        assertFalse(progress.physicalDone(), "physicalDone must be FALSE because Item B is unsatisfied");
        assertEquals("PROCESSING", progress.inventoryProcessingStatus());
    }

    @Test
    @DisplayName("Case 5 — FAILED command when remaining > 0 sets status FAILED")
    void testFailedCommandSetsFailedStatus() {
        UUID itemId = UUID.randomUUID();
        orderReturn.setItems(List.of(createItem(itemId, new BigDecimal("3.000"), false)));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, OrderReturnInventoryCommandStatus.FAILED}));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.of());

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertEquals(0, new BigDecimal("3.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.processedQuantity()));
        assertEquals(0, new BigDecimal("3.000").compareTo(progress.remainingQuantity()));
        assertFalse(progress.physicalDone());
        assertEquals("FAILED", progress.inventoryProcessingStatus());
        assertTrue(progress.hasFailedCommand());
        assertFalse(progress.hasPendingCommand());
    }

    @Test
    @DisplayName("Case 6 — Mixed FAILED + PENDING: FAILED takes precedence over PROCESSING to alert operator")
    void testFailedPrecedenceOverPending() {
        UUID itemAId = UUID.randomUUID();
        UUID itemBId = UUID.randomUUID();
        orderReturn.setItems(List.of(
                createItem(itemAId, new BigDecimal("2.000"), false),
                createItem(itemBId, new BigDecimal("3.000"), false)
        ));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{returnId, OrderReturnInventoryCommandStatus.FAILED},
                        new Object[]{returnId, OrderReturnInventoryCommandStatus.PENDING}
                ));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.of());

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertFalse(progress.physicalDone());
        assertEquals("FAILED", progress.inventoryProcessingStatus(), "FAILED must not be masked by PENDING");
        assertTrue(progress.hasFailedCommand());
        assertTrue(progress.hasPendingCommand());
    }

    @Test
    @DisplayName("Case 7 — Empty or deleted items return non-completed progress")
    void testEmptyOrDeletedItems() {
        UUID deletedItemId = UUID.randomUUID();
        orderReturn.setItems(List.of(createItem(deletedItemId, new BigDecimal("5.000"), true)));

        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId))).thenReturn(List.of());
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.of());

        OrderReturnPhysicalProgress progress = resolver.resolve(orderReturn);

        assertEquals(0, BigDecimal.ZERO.compareTo(progress.requiredQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.processedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.remainingQuantity()));
        assertFalse(progress.physicalDone());
        assertEquals("PENDING", progress.inventoryProcessingStatus());
    }

    @Test
    @DisplayName("Case 8 — Null parameters return empty progress")
    void testNullParameters() {
        OrderReturnPhysicalProgress progress1 = resolver.resolve((OrderReturn) null);
        assertNotNull(progress1);
        assertFalse(progress1.physicalDone());
        assertEquals("PENDING", progress1.inventoryProcessingStatus());

        OrderReturnPhysicalProgress progress2 = resolver.resolve((UUID) null);
        assertNotNull(progress2);
        assertFalse(progress2.physicalDone());
        assertEquals("PENDING", progress2.inventoryProcessingStatus());
    }

    @Test
    @DisplayName("Case 9 — Batch resolution matches individual resolution")
    void testBatchResolution() {
        UUID return1Id = UUID.randomUUID();
        UUID return2Id = UUID.randomUUID();
        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();

        OrderReturn ret1 = OrderReturn.builder().build();
        ret1.setId(return1Id);
        ret1.setItems(List.of(createItem(item1Id, new BigDecimal("4.000"), false)));

        OrderReturn ret2 = OrderReturn.builder().build();
        ret2.setId(return2Id);
        ret2.setItems(List.of(createItem(item2Id, new BigDecimal("2.000"), false)));

        List<UUID> batchIds = List.of(return1Id, return2Id);
        when(commandRepository.findCommandStatusesByReturnIds(batchIds))
                .thenReturn(List.<Object[]>of(
                        new Object[]{return1Id, OrderReturnInventoryCommandStatus.PROCESSED},
                        new Object[]{return2Id, OrderReturnInventoryCommandStatus.PENDING}
                ));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(batchIds, OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(
                        new Object[]{return1Id, item1Id, new BigDecimal("4.000")}
                ));

        Map<UUID, OrderReturnPhysicalProgress> resultMap = resolver.resolveBatch(List.of(ret1, ret2));

        assertEquals(2, resultMap.size());
        OrderReturnPhysicalProgress prog1 = resultMap.get(return1Id);
        assertNotNull(prog1);
        assertTrue(prog1.physicalDone());
        assertEquals("COMPLETED", prog1.inventoryProcessingStatus());

        OrderReturnPhysicalProgress prog2 = resultMap.get(return2Id);
        assertNotNull(prog2);
        assertFalse(prog2.physicalDone());
        assertEquals("PROCESSING", prog2.inventoryProcessingStatus());
        assertTrue(prog2.hasPendingCommand());
    }

    @Test
    @DisplayName("Case 10 — Resolve by returnId loads from repository")
    void testResolveByReturnId() {
        UUID itemId = UUID.randomUUID();
        orderReturn.setItems(List.of(createItem(itemId, new BigDecimal("1.000"), false)));

        when(orderReturnRepository.findByIdAndIsDeletedFalse(returnId)).thenReturn(Optional.of(orderReturn));
        when(commandRepository.findCommandStatusesByReturnIds(List.of(returnId)))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, OrderReturnInventoryCommandStatus.PROCESSED}));
        when(commandRepository.sumQuantityByReturnIdsAndCommandStatus(List.of(returnId), OrderReturnInventoryCommandStatus.PROCESSED))
                .thenReturn(List.<Object[]>of(new Object[]{returnId, itemId, new BigDecimal("1.000")}));

        OrderReturnPhysicalProgress progress = resolver.resolve(returnId);

        assertNotNull(progress);
        assertTrue(progress.physicalDone());
        assertEquals(0, new BigDecimal("1.000").compareTo(progress.requiredQuantity()));
        assertEquals(0, new BigDecimal("1.000").compareTo(progress.processedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(progress.remainingQuantity()));
    }
}
