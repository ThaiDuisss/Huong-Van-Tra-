package org.hvtra.iventoryservice;

import org.hvtra.commonservice.config.FeignConfig;
import org.hvtra.commonservice.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.hvtra")
@EnableJpaRepositories(basePackages = "org.hvtra")
@EntityScan(basePackages = "org.hvtra")
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@EnableScheduling
@Import(SwaggerConfig.class)
public class IventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IventoryServiceApplication.class, args);
    }

}
