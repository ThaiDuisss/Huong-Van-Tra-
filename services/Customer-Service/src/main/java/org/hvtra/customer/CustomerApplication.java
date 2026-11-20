package org.hvtra.customer;

import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.hvtra.customer.taste.CatalogTasteTaxonomyClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {
		"org.hvtra.customer",
		"org.hvtra.commonservice.common_core.config",
		"org.hvtra.commonservice.common_core.kafka",
		"org.hvtra.commonservice.common_core.utils",
		"org.hvtra.commonservice.common_core.exception"
})
@EnableJpaRepositories(basePackages = {
		"org.hvtra.customer",
		"org.hvtra.commonservice.common_core.repository"
})
@EntityScan(basePackages = {
		"org.hvtra.customer",
		"org.hvtra.commonservice.common_core.entity"
})
@EnableKafka
@EnableFeignClients(clients = CatalogTasteTaxonomyClient.class)
@Import(SwaggerConfig.class)
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }

}
