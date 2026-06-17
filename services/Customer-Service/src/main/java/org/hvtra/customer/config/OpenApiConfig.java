package org.hvtra.customer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customerServiceOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server()
                        .url("http://localhost:8082")
                        .description("Customer Service Local")))
                .info(new Info()
                        .title("Customer Service API")
                        .description("API quản lý khách hàng, hạng khách hàng, địa chỉ và tích điểm")
                        .version("v1"));
    }
}
