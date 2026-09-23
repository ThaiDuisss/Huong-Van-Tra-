package org.hvtra.catalog;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "org.hvtra.catalog",
        "org.hvtra.commonservice.common_core",
        "org.hvtra.commonservice.common_importing"
})
@EnableJpaRepositories(basePackages = "org.hvtra")
@EntityScan(basePackages = "org.hvtra")
@EnableFeignClients(basePackages = "org.hvtra", defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }

}
