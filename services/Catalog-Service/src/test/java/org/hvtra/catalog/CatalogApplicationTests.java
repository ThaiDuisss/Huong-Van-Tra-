package org.hvtra.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "security.internal.header-secret=test-internal-secret"
})
class CatalogApplicationTests {

    @Test
    void contextLoads() {
    }

}
