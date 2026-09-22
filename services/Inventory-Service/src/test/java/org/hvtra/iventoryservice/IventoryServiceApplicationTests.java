package org.hvtra.iventoryservice;

import org.hvtra.iventoryservice.stock.service.InventoryBalanceService;
import org.hvtra.iventoryservice.item.service.InventoryItemService;
import org.hvtra.iventoryservice.location.service.InventoryLocationService;
import org.hvtra.iventoryservice.movement.service.InventoryTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
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
}
