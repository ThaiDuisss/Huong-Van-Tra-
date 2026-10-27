package org.hvtra.fileservice;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = {
                "org.hvtra.fileservice",
                "org.hvtra.commonservice.common_core"
        }
)
@EnableJpaRepositories(basePackages = {
        "org.hvtra.fileservice",
        "org.hvtra.commonservice.common_core.repository"
})
@EntityScan(basePackages = {
        "org.hvtra.fileservice",
        "org.hvtra.commonservice.common_core.entity"
})
@EnableFeignClients(basePackages = "org.hvtra", defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }

}
