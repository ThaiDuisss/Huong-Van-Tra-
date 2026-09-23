package org.hvtra.orderservice;

import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.config.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		"org.hvtra.orderservice",
		"org.hvtra.commonservice.config",
		"org.hvtra.commonservice.common_core.kafka",
		"org.hvtra.commonservice.common_core.utils",
		"org.hvtra.commonservice.exception"
})
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@EnableScheduling
@EnableKafka
@Import(SwaggerConfig.class)
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}
}
