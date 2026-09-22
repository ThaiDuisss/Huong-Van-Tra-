package org.hvtra.customer;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.hvtra")
@ConfigurationPropertiesScan(basePackages = "org.hvtra.customer.config")
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@EnableScheduling
@Import(SwaggerConfig.class)
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }

}
