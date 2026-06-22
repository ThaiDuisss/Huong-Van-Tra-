package org.hvtra.promotionservice;

import org.hvtra.commonservice.config.FeignConfig;
import org.hvtra.commonservice.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class PromotionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }

}
