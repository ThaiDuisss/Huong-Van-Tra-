package org.hvtra.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationRouteTest {
    @Test void servingRouteIsGetOnlyAndNeverIncludesSync() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        String prefix = properties.stringPropertyNames().stream()
                .filter(k -> k.endsWith(".id") && "recommendation-serving".equals(properties.getProperty(k)))
                .findFirst().orElseThrow().replace(".id", "");
        assertEquals("${RECOMMENDATION_SERVICE_URL:http://localhost:8090}", properties.getProperty(prefix + ".uri"));
        assertEquals("Method=GET", properties.getProperty(prefix + ".predicates[1]"));
        assertEquals("StripPrefix=2", properties.getProperty(prefix + ".filters[0]"));
        var config = new PathRoutePredicateFactory.Config();
        config.setPatterns(List.of(properties.getProperty(prefix + ".predicates[0]").substring(5)));
        var predicate = new PathRoutePredicateFactory().apply(config);
        assertTrue(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get("/api/recommendation/recommendations/customer"))));
        for (String path : List.of("/internal/catalog/sync", "/api/recommendation/internal/catalog/sync",
                "/api/recommendation/recommendations/customer/internal/catalog/sync")) {
            assertFalse(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(path))));
        }
        var strip = new StripPrefixGatewayFilterFactory.Config();
        strip.setParts(2);
        AtomicReference<String> forwarded = new AtomicReference<>();
        new StripPrefixGatewayFilterFactory().apply(strip).filter(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/recommendation/recommendations/customer?mode=REPEAT")),
                exchange -> { forwarded.set(exchange.getRequest().getURI().getRawPath()); return Mono.empty(); }).block();
        assertEquals("/recommendations/customer", forwarded.get());
    }
}
