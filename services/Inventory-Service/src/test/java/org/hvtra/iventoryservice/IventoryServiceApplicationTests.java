package org.hvtra.iventoryservice;

import org.hvtra.iventoryservice.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.dto.request.InventoryLocationRequest;
import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryBalanceResponse;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.enums.*;
import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.hvtra.iventoryservice.service.InventoryItemService;
import org.hvtra.iventoryservice.service.InventoryLocationService;
import org.hvtra.iventoryservice.service.InventoryTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class IventoryServiceApplicationTests {
    @Autowired
    private InventoryItemService itemService;

    @Autowired
    private InventoryLocationService locationService;

    @Autowired
    private InventoryBalanceService balanceService;

    @Autowired
    private InventoryTransactionService transactionService;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void stockInAndAdjustmentUpdateBalanceAndCreateHistory() {
        var item = itemService.create(new InventoryItemRequest(
                1001L,
                InventoryItemType.PRODUCT_VARIANT,
                1L,
                InventoryStatus.ACTIVE));
        var location = locationService.create(new InventoryLocationRequest(
                10L,
                null,
                "MAIN-STORAGE",
                "Main Storage",
                InventoryLocationType.STORAGE,
                InventoryStatus.ACTIVE));

        InventoryTransactionResponse stockIn = transactionService.stockIn(new StockInRequest(
                10L,
                99L,
                InventoryReferenceType.MANUAL,
                null,
                List.of(new StockInRequest.LineRequest(
                        item.id(), location.id(), new BigDecimal("25.00")))));

        assertThat(stockIn.type()).isEqualTo(InventoryTransactionType.STOCK_IN);
        assertThat(stockIn.lines()).singleElement().satisfies(line -> {
            assertThat(line.beforeQuantity()).isEqualByComparingTo("0.00");
            assertThat(line.afterQuantity()).isEqualByComparingTo("25.00");
        });

        InventoryTransactionResponse adjustment = transactionService.adjust(
                new StockAdjustmentRequest(
                        10L,
                        99L,
                        InventoryReferenceType.STOCK_COUNT,
                        501L,
                        List.of(new StockAdjustmentRequest.LineRequest(
                                item.id(), location.id(), new BigDecimal("20.00")))));

        assertThat(adjustment.type()).isEqualTo(InventoryTransactionType.ADJUSTMENT);
        assertThat(adjustment.lines()).singleElement().satisfies(line -> {
            assertThat(line.quantity()).isEqualByComparingTo("-5.00");
            assertThat(line.beforeQuantity()).isEqualByComparingTo("25.00");
            assertThat(line.afterQuantity()).isEqualByComparingTo("20.00");
        });

        InventoryBalanceResponse balance = balanceService
                .findByInventoryItem(item.id(), PageRequest.of(0, 10))
                .content()
                .getFirst();
        assertThat(balance.quantityOnHand()).isEqualByComparingTo("20.00");
        assertThat(balance.availableQuantity()).isEqualByComparingTo("20.00");

        var history = transactionService.findAll(
                null,
                null,
                null,
                null,
                99L,
                10L,
                null,
                null,
                PageRequest.of(0, 10));
        assertThat(history.totalElements()).isEqualTo(2);
    }
}
