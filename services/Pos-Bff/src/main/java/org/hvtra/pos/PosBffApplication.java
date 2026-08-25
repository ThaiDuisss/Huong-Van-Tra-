package org.hvtra.pos;

import org.hvtra.commonservice.config.FeignConfig;
import org.hvtra.commonservice.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "org.hvtra.pos")
@EnableDiscoveryClient
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class PosBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosBffApplication.class, args);
    }
}
