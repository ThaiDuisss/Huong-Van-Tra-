package org.hvtra.iventoryservice;

import org.hvtra.iventoryservice.service.InventoryBalanceService;
import org.hvtra.iventoryservice.service.InventoryItemService;
import org.hvtra.iventoryservice.service.InventoryLocationService;
import org.hvtra.iventoryservice.service.InventoryTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
