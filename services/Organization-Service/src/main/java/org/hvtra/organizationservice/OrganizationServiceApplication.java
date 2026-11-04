package org.hvtra.organizationservice;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "org.hvtra")
// scanBasePackages = "org.hvtra" kéo theo BusinessSequenceService và ImportBatchService
// của Common-Service, nhưng repository của chúng nằm ngoài package gốc nên không được
// scan mặc định -> context không khởi tạo được. Khai báo trùng phạm vi component scan,
// giống Catalog-Service và Inventory-Service.
@EnableJpaRepositories(basePackages = "org.hvtra")
@EntityScan(basePackages = "org.hvtra")
@EnableFeignClients(basePackages = "org.hvtra", defaultConfiguration = FeignConfig.class)
@Import(SwaggerConfig.class)
public class OrganizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrganizationServiceApplication.class, args);
    }

}
