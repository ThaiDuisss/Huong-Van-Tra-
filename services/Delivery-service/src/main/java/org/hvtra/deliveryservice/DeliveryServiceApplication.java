package org.hvtra.deliveryservice;

import org.hvtra.deliveryservice.config.GhnProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableConfigurationProperties(GhnProperties.class)
@EnableFeignClients

public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }

}
