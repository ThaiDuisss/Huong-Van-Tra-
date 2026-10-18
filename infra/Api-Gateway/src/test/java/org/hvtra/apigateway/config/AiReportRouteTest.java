package org.hvtra.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AiReportRouteTest {
    private final Properties properties;
    private final String routePrefix;

    AiReportRouteTest() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        properties = yaml.getObject();
        routePrefix = properties.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".id") && "ai-report-service".equals(properties.getProperty(key)))
                .findFirst().orElseThrow().replace(".id", "");
    }

    boolean matches(String path) {
        String predicate = properties.getProperty(routePrefix + ".predicates[0]");
        var config = new PathRoutePredicateFactory.Config();
        config.setPatterns(Arrays.asList(predicate.substring("Path=".length()).split(",")));
        return new PathRoutePredicateFactory().apply(config).test(MockServerWebExchange.from(MockServerHttpRequest.get(path)));
    }

    @Test void exposesOnlyAiReportNamespace() {
        assertTrue(matches("/api/recommendation/ai-reports"));
        assertTrue(matches("/api/recommendation/ai-reports/report/download"));
        assertFalse(matches("/recommendations/customer"));
        assertFalse(matches("/internal/catalog/sync"));
        assertFalse(matches("/internal/file/ai-reports/metadata"));
        assertFalse(matches("/api/recommendation/ai-reports-evil"));
    }

    @Test void usesConfiguredHttpUpstreamWithoutPrefixRewrite() {
        assertEquals("${RECOMMENDATION_SERVICE_URL:http://localhost:8090}", properties.getProperty(routePrefix + ".uri"));
        assertFalse(properties.stringPropertyNames().stream().anyMatch(key -> key.startsWith(routePrefix + ".filters")));
    }

    @Test void existingReportingRouteIsUnchanged() {
        String prefix = properties.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".id") && "reporting-service".equals(properties.getProperty(key)))
                .findFirst().orElseThrow().replace(".id", "");
        assertEquals("lb://reporting-service", properties.getProperty(prefix + ".uri"));
        assertEquals("Path=/api/reports/**", properties.getProperty(prefix + ".predicates[0]"));
    }
}
