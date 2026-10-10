package org.hvtra.promotionservice;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {
        "org.hvtra.promotionservice",
        "org.hvtra.commonservice.common_core",
})@EnableJpaRepositories(basePackages = {
        "org.hvtra.promotionservice",
        "org.hvtra.commonservice.common_core.repository"
})
@EntityScan(basePackages = {
        "org.hvtra.promotionservice",
        "org.hvtra.commonservice.common_core.entity"
})
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class PromotionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }

}
