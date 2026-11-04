package org.hvtra.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profile prod chạy không có Eureka: mọi {@code lb://} được SimpleDiscoveryClient phân giải từ
 * danh sách instance tĩnh trong application-prod.yml.
 *
 * <p>Test này giữ hai thứ:
 * <ul>
 *   <li>Mỗi route {@code lb://} trong application.yml đều có instance tương ứng ở prod — thêm
 *       route mới mà quên khai báo thì gateway trả 503 và chỉ phát hiện được khi chạy thật.</li>
 *   <li>Instance phải khai bằng {@code host} + {@code port}. Khai bằng {@code uri} KHÔNG dùng được:
 *       {@code SimpleDiscoveryProperties.afterPropertiesSet()} chỉ gán serviceId, còn
 *       {@code DefaultServiceInstance.getUri()} dựng lại URI từ host/port và không bao giờ đọc
 *       field {@code uri} — kết quả là {@code http://null:80}.</li>
 * </ul>
 */
class SimpleDiscoveryInstancesTest {

    private final SimpleDiscoveryProperties properties;

    SimpleDiscoveryInstancesTest() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        properties = Binder.get(environment)
                .bind("spring.cloud.discovery.client.simple", SimpleDiscoveryProperties.class)
                .orElseThrow(() -> new AssertionError("application-prod.yml thiếu spring.cloud.discovery.client.simple"));
        properties.afterPropertiesSet();
    }

    private Set<String> loadBalancedServiceIdsFromRoutes() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties routes = yaml.getObject();
        assertNotNull(routes);
        return routes.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".uri"))
                .map(routes::getProperty)
                .filter(uri -> uri.startsWith("lb://"))
                .map(uri -> uri.substring("lb://".length()))
                .collect(Collectors.toSet());
    }

    @Test void declaresAnInstanceForEveryLoadBalancedRoute() {
        Set<String> missing = loadBalancedServiceIdsFromRoutes().stream()
                .filter(serviceId -> !properties.getInstances().containsKey(serviceId))
                .collect(Collectors.toSet());
        assertTrue(missing.isEmpty(),
                "Route lb:// không có instance tĩnh ở profile prod: " + missing);
    }

    @Test void resolvesEveryInstanceToAReachableHttpUri() {
        properties.getInstances().forEach((serviceId, instances) -> {
            assertEquals(1, instances.size(), serviceId + ": cần đúng 1 instance");
            ServiceInstance instance = instances.get(0);
            assertEquals(serviceId, instance.getServiceId());
            assertNotNull(instance.getHost(), serviceId + ": thiếu host");
            assertTrue(instance.getPort() > 0, serviceId + ": thiếu port");
            assertEquals("http://" + serviceId + ":" + instance.getPort(), instance.getUri().toString(),
                    serviceId + ": URI phân giải sai (khai bằng 'uri:' thay vì host/port?)");
        });
    }

    @Test void coversAllTenServicesOfBoxOne() {
        assertEquals(Set.of(
                "catalog-service", "customer-service", "inventory-service", "notification-service",
                "order-service", "payment-service", "promotion-service", "organization-service",
                "file-service", "identity-service"
        ), properties.getInstances().keySet());
    }
}
