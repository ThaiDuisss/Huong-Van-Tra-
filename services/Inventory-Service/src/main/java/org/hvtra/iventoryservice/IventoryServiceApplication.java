package org.hvtra.iventoryservice;

import org.hvtra.commonservice.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SwaggerConfig.class)
public class IventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IventoryServiceApplication.class, args);
    }

}
