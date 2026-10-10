package org.hvtra.customer;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.hvtra")
@EnableJpaRepositories(basePackages = {
        "org.hvtra.customer",
        "org.hvtra.commonservice.common_core.repository"
})
@EntityScan(basePackages = {
        "org.hvtra.customer",
        "org.hvtra.commonservice.common_core.entity"
})
@ConfigurationPropertiesScan(basePackages = "org.hvtra.customer.config")
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@EnableScheduling
@Import(SwaggerConfig.class)
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }

}
