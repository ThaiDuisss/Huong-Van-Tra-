package org.hvtra.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.CustomBundleCatalogClient;
import org.hvtra.orderservice.client.CustomBundleInventoryClient;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceApplicationTests {

	// Keep the existing context smoke test, with all cross-service calls isolated.
	@MockitoBean private CatalogClient catalogClient;
	@MockitoBean private CustomBundleCatalogClient bundleCatalogClient;
	@MockitoBean private CustomBundleInventoryClient bundleInventoryClient;
	@MockitoBean private CustomerClient customerClient;
	@MockitoBean private InventoryClient inventoryClient;
	@MockitoBean private PaymentClient paymentClient;

	@Test
	void contextLoads() {
	}

}
