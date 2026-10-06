package org.hvtra.identityservice;

import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "org.hvtra.identityservice",
        "org.hvtra.commonservice.common_core"
})
@EnableJpaRepositories(basePackages = {
        "org.hvtra.identityservice",
        "org.hvtra.commonservice.common_core.repository"
})
@EntityScan(basePackages = {
        "org.hvtra.identityservice",
        "org.hvtra.commonservice.common_core.entity"
})
@EnableDiscoveryClient
@Import(SwaggerConfig.class)
@EnableScheduling
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

}
